-- ============================================================================
-- M5 — Planes & Entitlements, US-4: Plan Fundador y operaciones del operador
-- (FR-012 a FR-016, FR-018, FR-019; R1, R5, R7, R9)
--
-- Sin jobs: la vigencia del estatus se calcula al evaluar. Ninguna función de este
-- archivo es accesible por la API.
--
-- Verificación: supabase/tests/planes_fundador.sql y scripts/test-planes-concurrencia.sh
-- ============================================================================

-- ---------------------------------------------------------------- tablas
create table public.fundador_olas (
    id uuid primary key default gen_random_uuid(),
    ola integer not null unique check (ola >= 1),
    cupo integer not null check (cupo >= 1)
);

insert into public.fundador_olas (ola, cupo) values (1, 100);

-- Sin FK a empresas: el otorgamiento sobrevive a su eliminación y sigue contando (FR-013).
create table public.fundador_otorgamientos (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null unique,
    ola integer not null references public.fundador_olas (ola),
    otorgado_at timestamptz not null default now(),
    perdido_at timestamptz,
    motivo_perdida text check (motivo_perdida in ('BAJA', 'PASO_A_FREE', 'IMPAGO')),
    constraint fundador_perdida_consistente check ((perdido_at is null) = (motivo_perdida is null))
);

create index fundador_otorgamientos_ola_idx on public.fundador_otorgamientos (ola);

-- Sin FK a empresas, para conservar la auditoría. Solo inserción (FR-019).
create table public.plan_cambios (
    id uuid primary key default gen_random_uuid(),
    empresa_id uuid not null,
    accion text not null check (accion in ('PAGO', 'BAJA', 'PASO_A_FREE', 'TESTER')),
    detalle jsonb not null,
    operador text not null check (btrim(operador) <> ''),
    created_at timestamptz not null default now()
);

create index plan_cambios_empresa_id_idx on public.plan_cambios (empresa_id);

alter table public.fundador_olas enable row level security;
alter table public.fundador_otorgamientos enable row level security;
alter table public.plan_cambios enable row level security;

revoke all on table public.fundador_olas from public, anon, authenticated, service_role;
revoke all on table public.fundador_otorgamientos from public, anon, authenticated, service_role;
revoke all on table public.plan_cambios from public, anon, authenticated, service_role;

-- Marca si la empresa tenía lotes al darse de baja: paso a Free borra el plan pagado (R7).
alter table public.suscripciones
    add column cobertura_lotes boolean not null default false;

create function private.proteger_perdida_fundador()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if old.perdido_at is not null
       and (new.perdido_at is distinct from old.perdido_at
            or new.motivo_perdida is distinct from old.motivo_perdida) then
        raise exception 'La pérdida del estatus Fundador es permanente';
    end if;
    return new;
end;
$$;

create trigger fundador_perdida_permanente
    before update on public.fundador_otorgamientos
    for each row execute function private.proteger_perdida_fundador();

create function private.rechazar_cambio_auditoria()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    raise exception 'plan_cambios es de solo inserción';
end;
$$;

create trigger plan_cambios_solo_insercion
    before update or delete on public.plan_cambios
    for each row execute function private.rechazar_cambio_auditoria();

-- ---------------------------------------------------------------- vigencia (R1, R7, R9)
-- Vigente desde el otorgamiento hasta su pérdida o, sin pérdida registrada, hasta V + 7 días.
create function private.fundador_vigente(p_s public.suscripciones, p_instante timestamptz)
returns boolean
language sql
stable
set search_path = ''
as $$
    select exists (
        select 1
        from public.fundador_otorgamientos o
        where o.empresa_id = (p_s).empresa_id
          and o.otorgado_at <= p_instante
          and p_instante < coalesce(o.perdido_at, (p_s).pagado_hasta + interval '168 hours')
    );
$$;

create or replace function private.plan_efectivo(p_s public.suscripciones, p_instante timestamptz)
returns text
language sql
stable
set search_path = ''
as $$
    select case
        when (p_s).es_tester then 'PREMIUM'
        when private.fundador_vigente(p_s, p_instante) then 'PREMIUM'
        when (p_s).plan_pagado is not null and p_instante < (p_s).pagado_hasta then (p_s).plan_pagado
        when p_instante < (p_s).trial_fin then 'STANDARD'
        else 'FREE'
    end;
$$;

create or replace function private.vencimiento(p_s public.suscripciones)
returns timestamptz
language sql
stable
set search_path = ''
as $$
    select coalesce(
        (p_s).cobertura_fin,
        greatest(
            (p_s).trial_fin,
            (p_s).pagado_hasta,
            case
                when exists (
                    select 1 from public.fundador_otorgamientos o
                    where o.empresa_id = (p_s).empresa_id and o.perdido_at is null
                ) then (p_s).pagado_hasta + interval '168 hours'
            end
        )
    );
$$;

-- Igual que en M4, salvo que considera la capacidad que tenía al darse de baja (R7).
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
                or v_s.cobertura_lotes
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

-- ---------------------------------------------------------------- operaciones del operador
create function private.exigir_operador(p_operador text)
returns void
language plpgsql
immutable
set search_path = ''
as $$
begin
    if p_operador is null or btrim(p_operador) = '' then
        raise exception 'Se requiere identificar al operador' using errcode = '22023';
    end if;
end;
$$;

create function private.bloquear_suscripcion(p_empresa uuid)
returns public.suscripciones
language plpgsql
set search_path = ''
as $$
declare
    v_s public.suscripciones;
begin
    select * into v_s from public.suscripciones s where s.empresa_id = p_empresa for update;
    if not found then
        raise exception 'Empresa sin suscripción' using errcode = 'P0002';
    end if;
    return v_s;
end;
$$;

-- La pérdida por impago se fecha en V + 7 días, aunque se registre después (R9).
create function private.persistir_impago(p_s public.suscripciones)
returns void
language sql
set search_path = ''
as $$
    update public.fundador_otorgamientos o
    set perdido_at = (p_s).pagado_hasta + interval '168 hours',
        motivo_perdida = 'IMPAGO'
    where o.empresa_id = (p_s).empresa_id
      and o.perdido_at is null
      and (p_s).pagado_hasta is not null
      and now() >= (p_s).pagado_hasta + interval '168 hours';
$$;

-- Una baja no acorta el trial en curso ni extiende una cobertura ya terminada.
create function private.fin_de_cobertura(p_s public.suscripciones)
returns timestamptz
language sql
stable
set search_path = ''
as $$
    select least(greatest(now(), (p_s).trial_fin), private.vencimiento(p_s));
$$;

create function private.registrar_pago(
    p_empresa uuid,
    p_plan text,
    p_hasta timestamptz,
    p_operador text
)
returns jsonb
language plpgsql
set search_path = ''
as $$
declare
    v_s public.suscripciones;
    v_primer_standard boolean;
    v_cupo integer;
    v_otorgados bigint;
    v_motivo text := 'NO_APLICA';
    v_fundador boolean;
begin
    perform private.exigir_operador(p_operador);
    if p_plan is null or p_plan not in ('STANDARD', 'PREMIUM') or p_hasta is null or p_hasta <= now() then
        raise exception 'Plan o período inválido' using errcode = '22023';
    end if;

    v_s := private.bloquear_suscripcion(p_empresa);

    -- Antes de renovar: una ventana ya vencida no se puede regularizar (FR-015).
    perform private.persistir_impago(v_s);

    v_primer_standard := p_plan = 'STANDARD' and v_s.primer_pago_standard_at is null;

    update public.suscripciones s
    set plan_pagado = p_plan,
        pagado_hasta = p_hasta,
        cobertura_fin = null,
        cobertura_lotes = false,
        primer_pago_standard_at = coalesce(
            s.primer_pago_standard_at,
            case when p_plan = 'STANDARD' then now() end
        ),
        updated_at = now()
    where s.empresa_id = p_empresa;

    if v_primer_standard
       and not exists (select 1 from public.fundador_otorgamientos o where o.empresa_id = p_empresa) then
        -- El lock de la ola serializa el conteo: nunca se supera el cupo (FR-014, R5).
        select f.cupo into strict v_cupo from public.fundador_olas f where f.ola = 1 for update;
        select count(*) into v_otorgados from public.fundador_otorgamientos o where o.ola = 1;

        if v_otorgados < v_cupo then
            insert into public.fundador_otorgamientos (empresa_id, ola) values (p_empresa, 1);
            v_motivo := 'OTORGADO';
        else
            v_motivo := 'CUPO_AGOTADO';
        end if;
    end if;

    v_fundador := exists (
        select 1 from public.fundador_otorgamientos o
        where o.empresa_id = p_empresa and o.perdido_at is null
    );

    insert into public.plan_cambios (empresa_id, accion, detalle, operador)
    values (
        p_empresa,
        'PAGO',
        jsonb_build_object(
            'anterior', jsonb_build_object('plan_pagado', v_s.plan_pagado, 'pagado_hasta', v_s.pagado_hasta),
            'nuevo', jsonb_build_object('plan_pagado', p_plan, 'pagado_hasta', p_hasta),
            'fundador', v_fundador,
            'motivo', v_motivo
        ),
        p_operador
    );

    return jsonb_build_object('fundador', v_fundador, 'motivo', v_motivo);
end;
$$;

create function private.registrar_baja(p_empresa uuid, p_operador text)
returns void
language plpgsql
set search_path = ''
as $$
declare
    v_s public.suscripciones;
    v_fin timestamptz;
    v_lotes boolean;
begin
    perform private.exigir_operador(p_operador);
    v_s := private.bloquear_suscripcion(p_empresa);

    if v_s.plan_pagado is null then
        raise exception 'La empresa no tiene un período pagado' using errcode = '22023';
    end if;

    -- Se calculan antes de registrar la pérdida, que cambia el vencimiento.
    v_fin := private.fin_de_cobertura(v_s);
    v_lotes := coalesce(private.tiene_lotes(v_s, least(now(), v_fin) - interval '1 microsecond'), false);

    perform private.persistir_impago(v_s);

    update public.fundador_otorgamientos o
    set perdido_at = now(), motivo_perdida = 'BAJA'
    where o.empresa_id = p_empresa and o.perdido_at is null;

    update public.suscripciones s
    set pagado_hasta = least(s.pagado_hasta, now()),
        cobertura_fin = v_fin,
        cobertura_lotes = v_lotes,
        updated_at = now()
    where s.empresa_id = p_empresa;

    insert into public.plan_cambios (empresa_id, accion, detalle, operador)
    values (
        p_empresa,
        'BAJA',
        jsonb_build_object(
            'anterior', jsonb_build_object('plan_pagado', v_s.plan_pagado, 'pagado_hasta', v_s.pagado_hasta),
            'nuevo', jsonb_build_object('cobertura_fin', v_fin)
        ),
        p_operador
    );
end;
$$;

create function private.pasar_a_free(p_empresa uuid, p_operador text)
returns void
language plpgsql
set search_path = ''
as $$
declare
    v_s public.suscripciones;
    v_fin timestamptz;
    v_lotes boolean;
begin
    perform private.exigir_operador(p_operador);
    v_s := private.bloquear_suscripcion(p_empresa);

    -- Se calculan antes de registrar la pérdida, que cambia el vencimiento.
    v_fin := private.fin_de_cobertura(v_s);
    v_lotes := coalesce(private.tiene_lotes(v_s, least(now(), v_fin) - interval '1 microsecond'), false);

    perform private.persistir_impago(v_s);

    update public.fundador_otorgamientos o
    set perdido_at = now(), motivo_perdida = 'PASO_A_FREE'
    where o.empresa_id = p_empresa and o.perdido_at is null;

    update public.suscripciones s
    set plan_pagado = null,
        pagado_hasta = null,
        cobertura_fin = v_fin,
        cobertura_lotes = v_lotes,
        updated_at = now()
    where s.empresa_id = p_empresa;

    insert into public.plan_cambios (empresa_id, accion, detalle, operador)
    values (
        p_empresa,
        'PASO_A_FREE',
        jsonb_build_object(
            'anterior', jsonb_build_object('plan_pagado', v_s.plan_pagado, 'pagado_hasta', v_s.pagado_hasta),
            'nuevo', jsonb_build_object('cobertura_fin', v_fin)
        ),
        p_operador
    );
end;
$$;

create function private.marcar_tester(p_empresa uuid, p_valor boolean, p_operador text)
returns void
language plpgsql
set search_path = ''
as $$
declare
    v_s public.suscripciones;
begin
    perform private.exigir_operador(p_operador);
    if p_valor is null then
        raise exception 'Valor de tester inválido' using errcode = '22023';
    end if;

    v_s := private.bloquear_suscripcion(p_empresa);

    update public.suscripciones s
    set es_tester = p_valor, updated_at = now()
    where s.empresa_id = p_empresa;

    insert into public.plan_cambios (empresa_id, accion, detalle, operador)
    values (
        p_empresa,
        'TESTER',
        jsonb_build_object('anterior', v_s.es_tester, 'nuevo', p_valor),
        p_operador
    );
end;
$$;

-- ---------------------------------------------------------------- RPC de plan
-- Igual que en M4, salvo el estado: durante la gracia, un Fundador sigue como PAGADO.
-- Nunca revela el estatus ni el cupo (FR-027).
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
