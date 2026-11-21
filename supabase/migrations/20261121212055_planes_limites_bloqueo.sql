-- ============================================================================
-- M4 — Planes & Entitlements, US-3: límites, capacidad de lotes, retenidos y
-- bloqueo de usuarios excedentes (FR-009, FR-010, FR-023, FR-025, FR-026, FR-029;
-- R2, R5, R7, R19).
--
-- Reversión del bloqueo:
--   alter role authenticator reset pgrst.db_pre_request;
--   notify pgrst, 'reload config';
--
-- Verificación: supabase/tests/planes_limites.sql, supabase/tests/planes_bloqueo.sql
-- y scripts/test-planes-concurrencia.sh
-- ============================================================================

-- ---------------------------------------------------------------- límites (R5)
-- Bloquea la suscripción antes de contar: en READ COMMITTED el conteo posterior
-- al lock ve lo confirmado por la transacción anterior.
create function private.validar_limite_bodega()
returns trigger
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_s public.suscripciones;
    v_max integer;
    v_total bigint;
begin
    select * into v_s from public.suscripciones s where s.empresa_id = new.empresa_id for no key update;
    if not found then
        return new;
    end if;

    select c.max_bodegas into v_max
    from public.planes_catalogo c
    where c.plan = private.plan_efectivo(v_s, now());

    if v_max is null then
        return new;
    end if;

    select count(*) into v_total from public.bodegas b where b.empresa_id = new.empresa_id;
    if v_total >= v_max then
        raise exception 'Se alcanzó el límite de bodegas del plan' using errcode = 'SFP01';
    end if;

    return new;
end;
$$;

create trigger bodegas_limite_plan
    before insert on public.bodegas
    for each row execute function private.validar_limite_bodega();

create function private.validar_limite_usuario()
returns trigger
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_s public.suscripciones;
    v_max integer;
    v_total bigint;
begin
    select * into v_s from public.suscripciones s where s.empresa_id = new.empresa_id for no key update;
    if not found then
        return new;
    end if;

    select c.max_usuarios into v_max
    from public.planes_catalogo c
    where c.plan = private.plan_efectivo(v_s, now());

    if v_max is null then
        return new;
    end if;

    select count(*) into v_total from public.usuarios u where u.empresa_id = new.empresa_id;
    if v_total >= v_max then
        raise exception 'Se alcanzó el límite de usuarios del plan' using errcode = 'SFP02';
    end if;

    return new;
end;
$$;

create trigger usuarios_limite_plan
    before insert on public.usuarios
    for each row execute function private.validar_limite_usuario();

-- ---------------------------------------------------------------- capacidad de lotes (R7, FR-025)
create function private.tiene_lotes(p_s public.suscripciones, p_instante timestamptz)
returns boolean
language sql
stable
set search_path = ''
as $$
    select c.lotes
        or ((p_s).activado_en is not null and p_instante < (p_s).trial_fin and (p_s).usaba_lotes)
    from public.planes_catalogo c
    where c.plan = private.plan_efectivo(p_s, p_instante);
$$;

-- El límite solo impide crear lotes nuevos: editar uno existente sigue las reglas de M3.
create or replace function private.proteger_lote()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_producto uuid := case when tg_op = 'DELETE' then old.producto_id else new.producto_id end;
    v_bodega uuid;
    v_empresa uuid;
    v_excedente boolean;
    v_solo_lectura boolean;
    v_s public.suscripciones;
    v_capacidad boolean := true;
    v_tenia_capacidad boolean := true;
begin
    if pg_trigger_depth() > 1 then
        return coalesce(new, old);
    end if;

    select p.bodega_id, p.empresa_id into v_bodega, v_empresa
    from public.productos p
    where p.id = v_producto;

    if v_bodega is null then
        return coalesce(new, old);
    end if;

    v_excedente := private.usuario_excedente(auth.uid(), v_empresa);
    v_solo_lectura := private.bodega_en_solo_lectura(v_bodega);

    if tg_op = 'UPDATE' and new.producto_id is distinct from old.producto_id then
        v_solo_lectura := v_solo_lectura or exists (
            select 1 from public.productos p
            where p.id = old.producto_id and private.bodega_en_solo_lectura(p.bodega_id)
        );
    end if;

    if tg_op = 'INSERT' then
        select * into v_s from public.suscripciones s where s.empresa_id = v_empresa;
        if found then
            v_capacidad := coalesce(private.tiene_lotes(v_s, now()), false);
            v_tenia_capacidad := v_capacidad
                or coalesce(private.tiene_lotes(v_s, private.vencimiento(v_s) - interval '1 microsecond'), false);
        end if;

        -- Un lote nuevo entra en la ventana si la empresa tenía la capacidad (R7).
        if v_tenia_capacidad and private.dentro_de_ventana(v_empresa, new.created_at) then
            return new;
        end if;
    end if;

    if v_excedente then
        raise exception 'Usuario sin acceso en el plan actual' using errcode = 'SFP05';
    end if;
    if v_solo_lectura then
        raise exception 'La bodega está en solo lectura con el plan actual' using errcode = 'SFP04';
    end if;
    if tg_op = 'INSERT' and not v_capacidad then
        raise exception 'Los lotes no están incluidos en el plan actual' using errcode = 'SFP03';
    end if;

    return coalesce(new, old);
end;
$$;

-- ---------------------------------------------------------------- resumen de retenidos (R19)
create table public.retenidos_resumen (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null references public.empresas (id) on delete cascade,
    usuario_id uuid not null references public.usuarios (id) on delete cascade,
    dispositivo_id uuid not null,
    cantidad integer not null check (cantidad > 0),
    actualizado_at timestamptz not null default now(),
    unique (usuario_id, dispositivo_id)
);

create index retenidos_resumen_empresa_id_idx on public.retenidos_resumen (empresa_id);

alter table public.retenidos_resumen enable row level security;
revoke all on table public.retenidos_resumen from public, anon, authenticated, service_role;

create function public.reportar_retenidos(p_dispositivo uuid, p_cantidad integer)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_empresa uuid;
begin
    if p_dispositivo is null or p_cantidad is null or p_cantidad < 0 then
        raise exception 'Parámetros inválidos' using errcode = '22023';
    end if;

    select u.empresa_id into v_empresa from public.usuarios u where u.id = v_uid;
    if v_empresa is null then
        raise exception 'Usuario sin empresa';
    end if;

    if p_cantidad = 0 then
        delete from public.retenidos_resumen r
        where r.usuario_id = v_uid and r.dispositivo_id = p_dispositivo;
        return;
    end if;

    insert into public.retenidos_resumen (empresa_id, usuario_id, dispositivo_id, cantidad, actualizado_at)
    values (v_empresa, v_uid, p_dispositivo, p_cantidad, now())
    on conflict (usuario_id, dispositivo_id)
    do update set cantidad = excluded.cantidad, empresa_id = excluded.empresa_id, actualizado_at = now();
end;
$$;

revoke all on function public.reportar_retenidos(uuid, integer) from public, anon, service_role;
grant execute on function public.reportar_retenidos(uuid, integer) to authenticated;

-- ---------------------------------------------------------------- RPC de plan
-- Conserva los campos de M3 y agrega `retenidos`, solo para ADMIN.
create or replace function public.obtener_entitlement()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_ahora timestamptz := now();
    v_uid uuid := auth.uid();
    v_empresa uuid;
    v_rol text;
    v_s public.suscripciones;
    v_plan text;
    v_estado text;
    v_cat public.planes_catalogo;
    v_free public.planes_catalogo;
    v_bodegas integer;
    v_usuarios integer;
    v_corte timestamptz;
    v_solo_lectura jsonb;
    v_bodegas_free jsonb;
    v_retenidos jsonb := '[]'::jsonb;
begin
    select u.empresa_id, u.rol into v_empresa, v_rol
    from public.usuarios u
    where u.id = v_uid;

    if v_empresa is null then
        raise exception 'Usuario sin empresa';
    end if;

    select * into v_s from public.suscripciones s where s.empresa_id = v_empresa;
    if not found then
        raise exception 'Empresa sin suscripción';
    end if;

    if private.usuario_excedente(v_uid, v_empresa) then
        return jsonb_build_object(
            'bloqueado', true,
            'empresa_id', v_empresa,
            'rol', v_rol,
            'ahora_servidor', v_ahora
        );
    end if;

    v_plan := private.plan_efectivo(v_s, v_ahora);
    v_estado := case
        when v_s.es_tester then 'TESTER'
        when v_s.plan_pagado is not null and v_ahora < v_s.pagado_hasta then 'PAGADO'
        when v_ahora < v_s.trial_fin then 'TRIAL'
        else 'FREE'
    end;

    select * into strict v_cat from public.planes_catalogo c where c.plan = v_plan;
    select * into strict v_free from public.planes_catalogo c where c.plan = 'FREE';

    select count(*) into v_bodegas from public.bodegas b where b.empresa_id = v_empresa;
    select count(*) into v_usuarios from public.usuarios u where u.empresa_id = v_empresa;

    v_corte := case when v_s.activado_en is not null and v_ahora < v_s.trial_fin then v_s.activado_en end;

    select coalesce(jsonb_agg(b.id order by b.created_at, b.id), '[]'::jsonb)
    into v_solo_lectura
    from public.bodegas b
    where b.empresa_id = v_empresa
      and b.id not in (select private.ids_bodegas_activas(v_empresa, v_cat.max_bodegas, v_corte));

    select coalesce(jsonb_agg(b.id order by b.created_at, b.id), '[]'::jsonb)
    into v_bodegas_free
    from public.bodegas b
    where b.id in (select private.ids_bodegas_activas(v_empresa, v_free.max_bodegas, null));

    if v_rol = 'ADMIN' then
        select coalesce(jsonb_agg(jsonb_build_object(
            'usuario_id', x.usuario_id,
            'cantidad', x.cantidad,
            'actualizado_at', x.actualizado_at
        ) order by x.usuario_id), '[]'::jsonb)
        into v_retenidos
        from (
            select r.usuario_id, sum(r.cantidad)::integer as cantidad, max(r.actualizado_at) as actualizado_at
            from public.retenidos_resumen r
            where r.empresa_id = v_empresa
            group by r.usuario_id
        ) x;
    end if;

    return jsonb_build_object(
        'bloqueado', false,
        'empresa_id', v_empresa,
        'rol', v_rol,
        'plan_efectivo', v_plan,
        'estado', v_estado,
        'capacidades', jsonb_build_object(
            'export', v_cat.export,
            'lotes', v_cat.lotes or (v_corte is not null and v_s.usaba_lotes)
        ),
        'permisos', jsonb_build_object(
            'crear_bodega', v_cat.max_bodegas is null or v_bodegas < v_cat.max_bodegas,
            'crear_usuario', v_cat.max_usuarios is null or v_usuarios < v_cat.max_usuarios
        ),
        'historial_desde', case
            when v_cat.historial_dias is null then null
            else v_ahora - make_interval(days => v_cat.historial_dias)
        end,
        'trial_fin', v_s.trial_fin,
        'vencimiento', private.vencimiento(v_s),
        'bodegas_solo_lectura', v_solo_lectura,
        'degradacion_free', jsonb_build_object(
            'bodegas_activas', v_bodegas_free,
            'bloqueado', v_uid not in (select private.ids_usuarios_activos(v_empresa, v_free.max_usuarios, null)),
            'capacidades', jsonb_build_object('export', v_free.export, 'lotes', v_free.lotes),
            'permisos', jsonb_build_object(
                'crear_bodega', v_free.max_bodegas is null or v_bodegas < v_free.max_bodegas,
                'crear_usuario', v_free.max_usuarios is null or v_usuarios < v_free.max_usuarios
            ),
            'historial_desde', case
                when v_free.historial_dias is null then null
                else v_ahora - make_interval(days => v_free.historial_dias)
            end
        ),
        'retenidos', v_retenidos,
        'avisos', '[]'::jsonb,
        'ahora_servidor', v_ahora
    );
end;
$$;

revoke all on function public.obtener_entitlement() from public, anon, service_role;
grant execute on function public.obtener_entitlement() to authenticated;

-- ---------------------------------------------------------------- pre-request (R2)
-- La decisión se calcula en un bloque protegido y el rechazo se lanza fuera: un error
-- interno deja pasar el request (el aislamiento lo sigue garantizando RLS).
create function public.verificar_acceso_plan()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_bloquear boolean := false;
    v_claims jsonb;
    v_uid uuid;
    v_empresa uuid;
    v_ruta text;
    v_metodo text;
    v_prefer text;
begin
    begin
        v_claims := nullif(current_setting('request.jwt.claims', true), '')::jsonb;

        if v_claims->>'role' = 'authenticated' and v_claims->>'sub' is not null then
            v_uid := (v_claims->>'sub')::uuid;
            select u.empresa_id into v_empresa from public.usuarios u where u.id = v_uid;

            if v_empresa is not null and private.usuario_excedente(v_uid, v_empresa) then
                v_ruta := rtrim(regexp_replace(coalesce(current_setting('request.path', true), ''), '^/rest/v1', ''), '/');
                v_metodo := upper(coalesce(current_setting('request.method', true), ''));
                v_prefer := lower(coalesce(
                    nullif(current_setting('request.headers', true), '')::jsonb->>'prefer', ''
                ));

                v_bloquear := not (
                    v_ruta in ('/rpc/obtener_entitlement', '/rpc/reportar_retenidos')
                    or (
                        v_metodo = 'POST'
                        and v_ruta in ('/movimientos', '/lotes')
                        and v_prefer ~ '(^|[,[:space:]])return=minimal($|[,[:space:]])'
                        and v_prefer !~ 'return=(?!minimal)'
                        and v_prefer !~ 'resolution='
                    )
                );
            end if;
        end if;
    exception when others then
        raise warning 'verificar_acceso_plan: % %', sqlstate, sqlerrm;
        v_bloquear := false;
    end;

    if v_bloquear then
        raise sqlstate 'PGRST' using
            message = json_build_object(
                'code', 'SFP05',
                'message', 'Usuario sin acceso en el plan actual',
                'details', null,
                'hint', null
            )::text,
            detail = json_build_object('status', 403, 'headers', json_build_object())::text;
    end if;
end;
$$;

revoke all on function public.verificar_acceso_plan() from public;
grant execute on function public.verificar_acceso_plan() to anon, authenticated, service_role;

alter role authenticator set pgrst.db_pre_request = 'public.verificar_acceso_plan';
notify pgrst, 'reload config';

-- ---------------------------------------------------------------- permisos del esquema private
do $$
declare
    v_funcion regprocedure;
begin
    for v_funcion in
        select p.oid::regprocedure from pg_proc p where p.pronamespace = 'private'::regnamespace
    loop
        execute format('revoke all on function %s from public, anon, authenticated, service_role', v_funcion);
    end loop;
end $$;
