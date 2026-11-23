-- ============================================================================
-- M1 — Planes & Entitlements, US-1: catálogo, suscripciones, trial y RPC de plan
-- (FR-001 a FR-004, FR-011, FR-018 a FR-020, FR-026; R1, R15, R20, R22)
--
-- ATENCIÓN: al aplicarse, cada empresa existente inicia su trial de 30 días.
-- Esta migración todavía no aplica ningún límite.
--
-- Verificación: supabase/tests/planes_modelo.sql
-- ============================================================================

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- ---------------------------------------------------------------- catálogo
create table public.planes_catalogo (
    id uuid primary key default gen_random_uuid(),
    plan text not null unique check (plan in ('FREE', 'STANDARD', 'PREMIUM')),
    max_bodegas integer check (max_bodegas is null or max_bodegas >= 1),
    max_usuarios integer check (max_usuarios is null or max_usuarios >= 1),
    export boolean not null,
    lotes boolean not null,
    multiempresa boolean not null,
    historial_dias integer check (historial_dias is null or historial_dias >= 1)
);

alter table public.planes_catalogo enable row level security;
revoke all on table public.planes_catalogo from public, anon, authenticated;

insert into public.planes_catalogo
    (plan, max_bodegas, max_usuarios, export, lotes, multiempresa, historial_dias)
values
    ('FREE', 1, 2, false, false, false, 90),
    ('STANDARD', 5, 10, true, false, false, 365),
    ('PREMIUM', null, null, true, true, true, null);

-- ---------------------------------------------------------------- suscripciones
create table public.suscripciones (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null unique references public.empresas (id) on delete cascade,
    trial_inicio timestamptz not null,
    trial_fin timestamptz not null,
    plan_pagado text check (plan_pagado in ('STANDARD', 'PREMIUM')),
    pagado_hasta timestamptz,
    primer_pago_standard_at timestamptz,
    cobertura_fin timestamptz,
    es_tester boolean not null default false,
    activado_en timestamptz,
    usaba_lotes boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    -- Resta y no suma: el resultado no depende de la zona horaria de la sesión.
    constraint suscripciones_trial_30_dias check (trial_fin - trial_inicio = interval '30 days'),
    constraint suscripciones_pago_consistente check ((plan_pagado is null) = (pagado_hasta is null))
);

alter table public.suscripciones enable row level security;
revoke all on table public.suscripciones from public, anon, authenticated, service_role;
grant select on table public.suscripciones to authenticated;

create policy suscripcion_propia on public.suscripciones
    for select to authenticated
    using (empresa_id = (select public.get_empresa_id()));

-- ---------------------------------------------------------------- plan efectivo (R1)
-- Función pura: recibe el instante para poder probar T - 1 s y T. Fundador se agrega en M5.
create function private.plan_efectivo(p_s public.suscripciones, p_instante timestamptz)
returns text
language sql
immutable
set search_path = ''
as $$
    select case
        when (p_s).es_tester then 'PREMIUM'
        when (p_s).plan_pagado is not null and p_instante < (p_s).pagado_hasta then (p_s).plan_pagado
        when p_instante < (p_s).trial_fin then 'STANDARD'
        else 'FREE'
    end;
$$;

revoke all on function private.plan_efectivo(public.suscripciones, timestamptz)
    from public, anon, authenticated, service_role;

-- ---------------------------------------------------------------- alta automática (FR-001)
create function private.crear_suscripcion_empresa()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_ahora timestamptz := now();
begin
    insert into public.suscripciones (empresa_id, trial_inicio, trial_fin)
    values (new.id, v_ahora, v_ahora + interval '720 hours');
    return new;
end;
$$;

revoke all on function private.crear_suscripcion_empresa()
    from public, anon, authenticated, service_role;

create trigger empresas_crear_suscripcion
    after insert on public.empresas
    for each row execute function private.crear_suscripcion_empresa();

-- ---------------------------------------------------------------- empresas existentes (FR-026)
insert into public.suscripciones
    (empresa_id, trial_inicio, trial_fin, activado_en, usaba_lotes, es_tester)
select
    e.id,
    now(),
    now() + interval '720 hours',
    now(),
    exists (select 1 from public.lotes l where l.empresa_id = e.id),
    false
from public.empresas e;

-- ---------------------------------------------------------------- RPC de plan
-- Sin parámetros: la empresa sale de la sesión (FR-020). Sin valores de límites ni precios
-- (FR-011, FR-027). M3, M4 y M6 la reemplazan agregando campos.
create function public.obtener_entitlement()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_ahora timestamptz := now();
    v_empresa uuid;
    v_rol text;
    v_s public.suscripciones;
    v_plan text;
    v_estado text;
    v_cat public.planes_catalogo;
    v_free public.planes_catalogo;
    v_bodegas integer;
    v_usuarios integer;
    v_activacion boolean;
    v_bodegas_free jsonb;
begin
    select u.empresa_id, u.rol into v_empresa, v_rol
    from public.usuarios u
    where u.id = auth.uid();

    if v_empresa is null then
        raise exception 'Usuario sin empresa';
    end if;

    select * into v_s from public.suscripciones s where s.empresa_id = v_empresa;
    if not found then
        raise exception 'Empresa sin suscripción';
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

    v_activacion := v_s.activado_en is not null and v_ahora < v_s.trial_fin;

    -- Las bodegas más antiguas siguen activas si el dispositivo degrada sin red (R22).
    select coalesce(jsonb_agg(x.id order by x.created_at, x.id), '[]'::jsonb)
    into v_bodegas_free
    from (
        select b.id, b.created_at
        from public.bodegas b
        where b.empresa_id = v_empresa
        order by b.created_at, b.id
        limit v_free.max_bodegas
    ) x;

    return jsonb_build_object(
        'bloqueado', false,
        'empresa_id', v_empresa,
        'rol', v_rol,
        'plan_efectivo', v_plan,
        'estado', v_estado,
        'capacidades', jsonb_build_object(
            'export', v_cat.export,
            'lotes', v_cat.lotes or (v_activacion and v_s.usaba_lotes)
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
        'vencimiento', coalesce(v_s.cobertura_fin, greatest(v_s.trial_fin, v_s.pagado_hasta)),
        'bodegas_solo_lectura', '[]'::jsonb,
        'degradacion_free', jsonb_build_object(
            'bodegas_activas', v_bodegas_free,
            'bloqueado', false,
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
