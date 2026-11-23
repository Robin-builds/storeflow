-- ============================================================================
-- Planes & Entitlements, US-1: SC-001, SC-002, FR-004, FR-018, FR-019 y FR-020.
--
-- Todo corre en una transacción que termina en ROLLBACK.
-- Resultado esperado: una fila 'OK planes_modelo'.
-- ============================================================================

begin;

insert into public.empresas (id, nombre) values
    ('a1000000-0000-4000-8000-00000000000a', 'TEST planes A'),
    ('b1000000-0000-4000-8000-00000000000b', 'TEST planes B'),
    ('c1000000-0000-4000-8000-00000000000c', 'TEST planes tester');

insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('a1000000-0000-4000-8000-0000000000a1', 'a1000000-0000-4000-8000-00000000000a', 'admin a', 'a1@test.invalid', 'ADMIN'),
    ('a1000000-0000-4000-8000-0000000000a2', 'a1000000-0000-4000-8000-00000000000a', 'operador a', 'a2@test.invalid', 'OPERADOR'),
    ('b1000000-0000-4000-8000-0000000000b1', 'b1000000-0000-4000-8000-00000000000b', 'admin b', 'b1@test.invalid', 'ADMIN');

insert into public.bodegas (id, empresa_id, nombre) values
    ('a1000000-0000-4000-8000-0000000000a3', 'a1000000-0000-4000-8000-00000000000a', 'bodega a');

do $$
declare
    ea constant uuid := 'a1000000-0000-4000-8000-00000000000a';
    ec constant uuid := 'c1000000-0000-4000-8000-00000000000c';
    s public.suscripciones;
    n bigint;
begin
    -- Catálogo con exactamente 3 filas
    select count(*) into n from public.planes_catalogo where plan in ('FREE', 'STANDARD', 'PREMIUM');
    if n <> 3 or (select count(*) from public.planes_catalogo) <> 3 then
        raise exception 'catálogo: se esperaban exactamente 3 planes';
    end if;

    -- Toda empresa tiene suscripción (backfill y trigger)
    select count(*) into n
    from public.empresas e
    left join public.suscripciones x on x.empresa_id = e.id
    where x.id is null;
    if n <> 0 then raise exception 'FR-001/FR-026: % empresas sin suscripción', n; end if;

    -- SC-001: Standard en t0 y término en t0 + 30 días, sin datos de pago
    select * into strict s from public.suscripciones where empresa_id = ea;
    if s.trial_fin - s.trial_inicio <> interval '30 days' then
        raise exception 'SC-001: el trial no dura 30 días';
    end if;
    if s.plan_pagado is not null or s.pagado_hasta is not null or s.es_tester or s.activado_en is not null then
        raise exception 'SC-001: una empresa nueva no debe tener pago, marca ni activación';
    end if;
    if private.plan_efectivo(s, s.trial_inicio) <> 'STANDARD' then
        raise exception 'SC-001: el plan en t0 no es Standard';
    end if;

    -- SC-002: T - 1 s Standard, T Free
    if private.plan_efectivo(s, s.trial_fin - interval '1 second') <> 'STANDARD' then
        raise exception 'SC-002: en T - 1 s no es Standard';
    end if;
    if private.plan_efectivo(s, s.trial_fin) <> 'FREE' then
        raise exception 'SC-002: en T no es Free';
    end if;

    -- FR-004: el trial es único por empresa
    begin
        insert into public.suscripciones (empresa_id, trial_inicio, trial_fin)
        values (ea, now(), now() + interval '720 hours');
        raise exception 'FR-004: se creó un segundo trial' using errcode = 'XX000';
    exception when unique_violation then null;
    end;

    -- Checks de la tabla
    begin
        update public.suscripciones set trial_fin = trial_fin + interval '1 day' where empresa_id = ea;
        raise exception 'check: se aceptó un trial de más de 30 días' using errcode = 'XX000';
    exception when check_violation then null;
    end;
    begin
        update public.suscripciones set plan_pagado = 'STANDARD' where empresa_id = ea;
        raise exception 'check: se aceptó plan_pagado sin pagado_hasta' using errcode = 'XX000';
    exception when check_violation then null;
    end;

    -- Un período pagado vigente manda sobre el trial y al vencer cae a Free
    s.plan_pagado := 'PREMIUM';
    s.pagado_hasta := s.trial_fin + interval '10 days';
    if private.plan_efectivo(s, s.trial_inicio) <> 'PREMIUM' then
        raise exception 'R1: el pago vigente no manda sobre el trial';
    end if;
    if private.plan_efectivo(s, s.pagado_hasta) <> 'FREE' then
        raise exception 'R1: el pago vencido no cae a Free';
    end if;

    -- FR-018: una empresa con marca de tester no cae a Free
    update public.suscripciones set es_tester = true where empresa_id = ec;
    select * into strict s from public.suscripciones where empresa_id = ec;
    if private.plan_efectivo(s, s.trial_fin + interval '1000 days') <> 'PREMIUM' then
        raise exception 'FR-018: la marca de tester cayó a Free';
    end if;
end $$;

-- ---------------------------------------------------------------- como ADMIN de A
set local role authenticated;
select set_config('request.jwt.claims',
    '{"sub":"a1000000-0000-4000-8000-0000000000a1","role":"authenticated"}', true);

do $$
declare
    ea constant uuid := 'a1000000-0000-4000-8000-00000000000a';
    eb constant uuid := 'b1000000-0000-4000-8000-00000000000b';
    r jsonb;
    n bigint;
begin
    r := public.obtener_entitlement();

    if (r->>'empresa_id')::uuid <> ea or r->>'rol' <> 'ADMIN' then
        raise exception 'FR-020: la RPC no resolvió la empresa de la sesión';
    end if;
    if r->>'plan_efectivo' <> 'STANDARD' or r->>'estado' <> 'TRIAL' or (r->>'bloqueado')::boolean then
        raise exception 'SC-001: la RPC no entrega Standard en trial: %', r;
    end if;
    if not (r ? 'capacidades' and r ? 'permisos' and r ? 'historial_desde' and r ? 'trial_fin'
            and r ? 'vencimiento' and r ? 'bodegas_solo_lectura' and r ? 'degradacion_free'
            and r ? 'retenidos' and r ? 'avisos' and r ? 'ahora_servidor') then
        raise exception 'contrato: faltan campos: %', r;
    end if;
    if r::text ~* '(max_bodegas|max_usuarios|historial_dias|precio|cupo)' then
        raise exception 'FR-011/FR-027: la RPC expone límites o precios: %', r;
    end if;
    if not (r->'degradacion_free'->'bodegas_activas') ? 'a1000000-0000-4000-8000-0000000000a3' then
        raise exception 'R22: la bodega más antigua no está en degradacion_free';
    end if;

    -- FR-020: no lee la suscripción de B
    select count(*) into n from public.suscripciones where empresa_id = eb;
    if n <> 0 then raise exception 'FR-020: A lee la suscripción de B'; end if;
    select count(*) into n from public.suscripciones where empresa_id = ea;
    if n <> 1 then raise exception 'FR-020: A no lee su propia suscripción'; end if;

    -- FR-019: ADMIN sin escritura
    begin
        update public.suscripciones set es_tester = true where empresa_id = ea;
        raise exception 'FR-019: ADMIN modificó su suscripción' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;
    begin
        delete from public.suscripciones where empresa_id = ea;
        raise exception 'FR-019: ADMIN borró su suscripción' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;
    begin
        insert into public.suscripciones (empresa_id, trial_inicio, trial_fin)
        values (gen_random_uuid(), now(), now() + interval '720 hours');
        raise exception 'FR-019: ADMIN insertó una suscripción' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;

    -- Sin acceso al catálogo ni a las funciones internas
    begin
        select count(*) into n from public.planes_catalogo;
        raise exception 'FR-011: un cliente lee el catálogo' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;
    begin
        perform private.plan_efectivo(s, now()) from public.suscripciones s;
        raise exception 'esquema private accesible' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;
end $$;

-- ---------------------------------------------------------------- como OPERADOR de A
select set_config('request.jwt.claims',
    '{"sub":"a1000000-0000-4000-8000-0000000000a2","role":"authenticated"}', true);

do $$
begin
    if public.obtener_entitlement()->>'rol' <> 'OPERADOR' then
        raise exception 'FR-020: rol incorrecto';
    end if;
    begin
        update public.suscripciones set es_tester = true;
        raise exception 'FR-019: OPERADOR modificó la suscripción' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;
end $$;

-- ---------------------------------------------------------------- cuenta sin empresa
select set_config('request.jwt.claims',
    '{"sub":"e1000000-0000-4000-8000-0000000000e1","role":"authenticated"}', true);

do $$
begin
    begin
        perform public.obtener_entitlement();
        raise exception 'contrato: un usuario sin empresa recibió un plan' using errcode = 'XX000';
    exception when raise_exception then null;
    end;
end $$;

-- ---------------------------------------------------------------- anon
reset role;
set local role anon;

do $$
begin
    begin
        perform public.obtener_entitlement();
        raise exception 'contrato: anon ejecutó la RPC' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;
end $$;

reset role;
select 'OK planes_modelo' as resultado;
rollback;
