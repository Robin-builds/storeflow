-- ============================================================================
-- M6 — Planes & Entitlements, US-5: avisos del ciclo de trial (FR-017, R14)
--
-- Sin jobs: la RPC emite de forma idempotente los momentos ya alcanzados cuando un
-- ADMIN abre la app, que es el único canal de estos avisos en v1.
--
-- Verificación: supabase/tests/planes_avisos.sql
-- ============================================================================

create table public.avisos_plan (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null references public.empresas (id) on delete cascade,
    momento text not null check (momento in ('DIA_0', 'MENOS_7', 'MENOS_1', 'TERMINO', 'MAS_2')),
    emitido_at timestamptz not null default now(),
    cerrado_por uuid[] not null default '{}',
    unique (empresa_id, momento)
);

alter table public.avisos_plan enable row level security;
revoke all on table public.avisos_plan from public, anon, authenticated, service_role;

create function private.orden_momento(p_momento text)
returns integer
language sql
immutable
set search_path = ''
as $$
    select array_position(array['DIA_0', 'MENOS_7', 'MENOS_1', 'TERMINO', 'MAS_2']::text[], p_momento);
$$;

-- Los avisos de vencimiento son solo para quien nunca pagó: un pago vencido meses
-- después no debe anunciar el término de un trial que ya no le aplica.
create function private.sin_pagos(p_s public.suscripciones)
returns boolean
language sql
stable
set search_path = ''
as $$
    select not (p_s).es_tester
       and (p_s).plan_pagado is null
       and not exists (
           select 1 from public.plan_cambios c
           where c.empresa_id = (p_s).empresa_id and c.accion = 'PAGO'
       )
       and not exists (
           select 1 from public.fundador_otorgamientos o
           where o.empresa_id = (p_s).empresa_id
       );
$$;

create function private.emitir_avisos(p_s public.suscripciones, p_ahora timestamptz)
returns void
language sql
set search_path = ''
as $$
    insert into public.avisos_plan (empresa_id, momento)
    select (p_s).empresa_id, m.momento
    from (
        values
            ('DIA_0', (p_s).trial_inicio, false),
            ('MENOS_7', (p_s).trial_fin - interval '168 hours', true),
            ('MENOS_1', (p_s).trial_fin - interval '24 hours', true),
            ('TERMINO', (p_s).trial_fin, true),
            ('MAS_2', (p_s).trial_fin + interval '48 hours', true)
    ) as m(momento, desde, de_vencimiento)
    where p_ahora >= m.desde
      and (not m.de_vencimiento or private.sin_pagos(p_s))
      and not exists (
          select 1 from public.avisos_plan a
          where a.empresa_id = (p_s).empresa_id and a.momento = m.momento
      )
    on conflict (empresa_id, momento) do nothing;
$$;

-- Cierra ese momento y los anteriores para el ADMIN de la sesión. Idempotente.
create function public.cerrar_aviso_plan(p_momento text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_empresa uuid;
    v_rol text;
    v_orden integer := private.orden_momento(p_momento);
begin
    if v_orden is null then
        raise exception 'Momento de aviso inválido' using errcode = '22023';
    end if;

    select u.empresa_id, u.rol into v_empresa, v_rol from public.usuarios u where u.id = v_uid;
    if v_empresa is null then
        raise exception 'Usuario sin empresa';
    end if;
    if v_rol <> 'ADMIN' then
        raise exception 'Solo un administrador puede cerrar avisos' using errcode = '42501';
    end if;

    update public.avisos_plan a
    set cerrado_por = array_append(a.cerrado_por, v_uid)
    where a.empresa_id = v_empresa
      and private.orden_momento(a.momento) <= v_orden
      and not (v_uid = any (a.cerrado_por));
end;
$$;

revoke all on function public.cerrar_aviso_plan(text) from public, anon, service_role;
grant execute on function public.cerrar_aviso_plan(text) to authenticated;

-- ---------------------------------------------------------------- RPC de plan
-- Igual que en M5, y agrega `avisos`, solo para ADMIN.
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
    v_avisos jsonb := '[]'::jsonb;
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
        when private.fundador_vigente(v_s, v_ahora) then 'PAGADO'
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

        perform private.emitir_avisos(v_s, v_ahora);

        select coalesce(jsonb_agg(jsonb_build_object(
            'momento', a.momento,
            'cerrado', v_uid = any (a.cerrado_por)
        ) order by private.orden_momento(a.momento)), '[]'::jsonb)
        into v_avisos
        from public.avisos_plan a
        where a.empresa_id = v_empresa;
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
        'avisos', v_avisos,
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
