-- ============================================================================
-- M3 — Planes & Entitlements, US-2: conjuntos activos y solo lectura
-- (FR-005 a FR-008, FR-021, FR-022, FR-026, FR-028, FR-029; R6, R7, R8, R20, R22)
--
-- Ninguna regla modifica filas de negocio: qué está activo se calcula al evaluar.
-- Las acciones de FK (pg_trigger_depth() > 1) quedan exentas.
--
-- Verificación: supabase/tests/planes_solo_lectura.sql
-- ============================================================================

-- ---------------------------------------------------------------- helpers
create function private.vencimiento(p_s public.suscripciones)
returns timestamptz
language sql
immutable
set search_path = ''
as $$
    select coalesce((p_s).cobertura_fin, greatest((p_s).trial_fin, (p_s).pagado_hasta));
$$;

-- `corte` solo existe durante el trial de activación: todo lo creado hasta ahí sigue activo (R8).
create function private.contexto_plan(p_empresa uuid)
returns table (max_bodegas integer, max_usuarios integer, corte timestamptz, vencimiento timestamptz)
language sql
stable
set search_path = ''
as $$
    select
        c.max_bodegas,
        c.max_usuarios,
        case when s.activado_en is not null and now() < s.trial_fin then s.activado_en end,
        private.vencimiento(s)
    from public.suscripciones s
    join public.planes_catalogo c on c.plan = private.plan_efectivo(s, now())
    where s.empresa_id = p_empresa;
$$;

create function private.ids_bodegas_activas(p_empresa uuid, p_max integer, p_corte timestamptz)
returns setof uuid
language sql
stable
set search_path = ''
as $$
    select x.id
    from (
        select b.id, b.created_at, row_number() over (order by b.created_at, b.id) as orden
        from public.bodegas b
        where b.empresa_id = p_empresa
    ) x
    where p_max is null or x.orden <= p_max or x.created_at <= p_corte;
$$;

-- Primero el ADMIN más antiguo; luego el resto por antigüedad (FR-007).
create function private.ids_usuarios_activos(p_empresa uuid, p_max integer, p_corte timestamptz)
returns setof uuid
language sql
stable
set search_path = ''
as $$
    with admin as (
        select u.id
        from public.usuarios u
        where u.empresa_id = p_empresa and u.rol = 'ADMIN'
        order by u.created_at, u.id
        limit 1
    )
    select x.id
    from (
        select
            u.id,
            u.created_at,
            row_number() over (
                order by (u.id = (select a.id from admin a)) is true desc, u.created_at, u.id
            ) as orden
        from public.usuarios u
        where u.empresa_id = p_empresa
    ) x
    where p_max is null or x.orden <= p_max or x.created_at <= p_corte;
$$;

create function private.bodegas_activas(p_empresa uuid)
returns setof uuid
language sql
stable
set search_path = ''
as $$
    select private.ids_bodegas_activas(p_empresa, c.max_bodegas, c.corte)
    from private.contexto_plan(p_empresa) c;
$$;

create function private.usuarios_activos(p_empresa uuid)
returns setof uuid
language sql
stable
set search_path = ''
as $$
    select private.ids_usuarios_activos(p_empresa, c.max_usuarios, c.corte)
    from private.contexto_plan(p_empresa) c;
$$;

create function private.bodega_en_solo_lectura(p_bodega uuid)
returns boolean
language plpgsql
stable
set search_path = ''
as $$
declare
    v_empresa uuid;
begin
    select b.empresa_id into v_empresa from public.bodegas b where b.id = p_bodega;
    if v_empresa is null or not exists (select 1 from private.contexto_plan(v_empresa)) then
        return false;
    end if;
    return not exists (select 1 from private.bodegas_activas(v_empresa) a where a = p_bodega);
end;
$$;

create function private.usuario_excedente(p_usuario uuid, p_empresa uuid)
returns boolean
language plpgsql
stable
set search_path = ''
as $$
begin
    if p_usuario is null
       or not exists (select 1 from public.usuarios u where u.id = p_usuario and u.empresa_id = p_empresa)
       or not exists (select 1 from private.contexto_plan(p_empresa)) then
        return false;
    end if;
    return not exists (select 1 from private.usuarios_activos(p_empresa) a where a = p_usuario);
end;
$$;

-- Ventana de FR-028: fecha anterior a vencimiento + 7 días.
create function private.dentro_de_ventana(p_empresa uuid, p_fecha timestamptz)
returns boolean
language sql
stable
set search_path = ''
as $$
    select coalesce(p_fecha < c.vencimiento + interval '168 hours', false)
    from private.contexto_plan(p_empresa) c;
$$;

-- ---------------------------------------------------------------- antigüedad del servidor (R6)
create function private.fijar_created_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if tg_op = 'INSERT' then
        -- clock_timestamp: las altas de una misma transacción no deben empatar.
        new.created_at := clock_timestamp();
    else
        new.created_at := old.created_at;
    end if;
    return new;
end;
$$;

create trigger bodegas_fijar_created_at
    before insert or update on public.bodegas
    for each row execute function private.fijar_created_at();

create trigger usuarios_fijar_created_at
    before insert or update on public.usuarios
    for each row execute function private.fijar_created_at();

-- ---------------------------------------------------------------- solo lectura
create function private.proteger_bodega()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_activas bigint;
    v_total bigint;
begin
    if pg_trigger_depth() > 1 then
        return coalesce(new, old);
    end if;

    if private.bodega_en_solo_lectura(old.id) then
        raise exception 'La bodega está en solo lectura con el plan actual' using errcode = 'SFP04';
    end if;

    if tg_op = 'DELETE' then
        select count(*) into v_activas from private.bodegas_activas(old.empresa_id);
        select count(*) into v_total from public.bodegas b where b.empresa_id = old.empresa_id;
        if v_activas = 1 and v_total > 1 then
            raise exception 'No se puede eliminar la única bodega editable' using errcode = 'SFP04';
        end if;
        return old;
    end if;

    return new;
end;
$$;

create trigger bodegas_solo_lectura
    before update or delete on public.bodegas
    for each row execute function private.proteger_bodega();

create function private.proteger_producto()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if pg_trigger_depth() > 1 then
        return coalesce(new, old);
    end if;

    if tg_op in ('UPDATE', 'DELETE') and private.bodega_en_solo_lectura(old.bodega_id) then
        raise exception 'La bodega está en solo lectura con el plan actual' using errcode = 'SFP04';
    end if;

    if tg_op = 'INSERT' or (tg_op = 'UPDATE' and new.bodega_id is distinct from old.bodega_id) then
        if private.bodega_en_solo_lectura(new.bodega_id) then
            raise exception 'La bodega está en solo lectura con el plan actual' using errcode = 'SFP04';
        end if;
    end if;

    return coalesce(new, old);
end;
$$;

create trigger productos_solo_lectura
    before insert or update or delete on public.productos
    for each row execute function private.proteger_producto();

create function private.proteger_lote()
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

    -- Un lote nuevo entra en la ventana porque su movimiento depende de él (R7).
    if tg_op = 'INSERT' and private.dentro_de_ventana(v_empresa, new.created_at) then
        return new;
    end if;

    if v_excedente then
        raise exception 'Usuario sin acceso en el plan actual' using errcode = 'SFP05';
    end if;
    if v_solo_lectura then
        raise exception 'La bodega está en solo lectura con el plan actual' using errcode = 'SFP04';
    end if;

    return coalesce(new, old);
end;
$$;

create trigger lotes_solo_lectura
    before insert or update or delete on public.lotes
    for each row execute function private.proteger_lote();

create function private.proteger_atributo()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_bodega uuid;
begin
    if pg_trigger_depth() > 1 then
        return coalesce(new, old);
    end if;

    if tg_op in ('UPDATE', 'DELETE') then
        select p.bodega_id into v_bodega from public.productos p where p.id = old.producto_id;
        if v_bodega is not null and private.bodega_en_solo_lectura(v_bodega) then
            raise exception 'La bodega está en solo lectura con el plan actual' using errcode = 'SFP04';
        end if;
    end if;

    if tg_op in ('INSERT', 'UPDATE') then
        select p.bodega_id into v_bodega from public.productos p where p.id = new.producto_id;
        if v_bodega is not null and private.bodega_en_solo_lectura(v_bodega) then
            raise exception 'La bodega está en solo lectura con el plan actual' using errcode = 'SFP04';
        end if;
    end if;

    return coalesce(new, old);
end;
$$;

create trigger producto_atributos_solo_lectura
    before insert or update or delete on public.producto_atributos
    for each row execute function private.proteger_atributo();

-- ---------------------------------------------------------------- movimientos (FR-028, FR-029)
create table public.movimientos_fuera_plan (
    id uuid primary key default gen_random_uuid(),
    movimiento_id uuid not null unique references public.movimientos (id) on delete cascade,
    empresa_id uuid not null references public.empresas (id) on delete cascade,
    marcado_at timestamptz not null default now()
);

create index movimientos_fuera_plan_empresa_id_idx on public.movimientos_fuera_plan (empresa_id);

alter table public.movimientos_fuera_plan enable row level security;
revoke all on table public.movimientos_fuera_plan from public, anon, authenticated, service_role;
grant select on table public.movimientos_fuera_plan to authenticated;

create policy movimientos_fuera_plan_propios on public.movimientos_fuera_plan
    for select to authenticated
    using (empresa_id = (select public.get_empresa_id()));

create function private.validar_movimiento_plan()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_bodega uuid;
    v_empresa uuid;
    v_excedente boolean;
begin
    select p.bodega_id, p.empresa_id into v_bodega, v_empresa
    from public.productos p
    where p.id = new.producto_id;

    if v_bodega is null then
        return new;
    end if;

    -- Siempre con auth.uid(), nunca con el usuario_id del payload.
    v_excedente := private.usuario_excedente(auth.uid(), v_empresa);

    if (v_excedente or private.bodega_en_solo_lectura(v_bodega))
       and not private.dentro_de_ventana(v_empresa, new.created_at) then
        if v_excedente then
            raise exception 'Usuario sin acceso en el plan actual' using errcode = 'SFP05';
        end if;
        raise exception 'La bodega está en solo lectura con el plan actual' using errcode = 'SFP04';
    end if;

    return new;
end;
$$;

create trigger movimientos_validar_plan
    before insert on public.movimientos
    for each row execute function private.validar_movimiento_plan();

create function private.marcar_movimiento_fuera_plan()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_bodega uuid;
    v_empresa uuid;
begin
    select p.bodega_id, p.empresa_id into v_bodega, v_empresa
    from public.productos p
    where p.id = new.producto_id;

    if v_bodega is not null
       and (private.usuario_excedente(auth.uid(), v_empresa)
            or private.bodega_en_solo_lectura(v_bodega)) then
        insert into public.movimientos_fuera_plan (movimiento_id, empresa_id)
        values (new.id, v_empresa)
        on conflict (movimiento_id) do nothing;
    end if;

    return new;
end;
$$;

create trigger movimientos_marcar_fuera_plan
    after insert on public.movimientos
    for each row execute function private.marcar_movimiento_fuera_plan();

-- ---------------------------------------------------------------- RPC de plan
-- Un excedente recibe solo su estado de bloqueo (R20). La app recibe conjuntos y permisos
-- ya resueltos, nunca los valores de los límites (FR-011, R22).
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
        'retenidos', '[]'::jsonb,
        'avisos', '[]'::jsonb,
        'ahora_servidor', v_ahora
    );
end;
$$;

revoke all on function public.obtener_entitlement() from public, anon, service_role;
grant execute on function public.obtener_entitlement() to authenticated;

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
