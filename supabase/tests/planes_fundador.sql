-- ============================================================================
-- Planes & Entitlements, US-4: SC-008, SC-009, FR-012, FR-013, FR-015, FR-016,
-- FR-019 y R7. El caso concurrente (SC-007) está en scripts/test-planes-concurrencia.sh.
--
-- El reloj se controla moviendo pagado_hasta respecto de now(), que es fijo en la transacción.
-- Todo corre en una transacción que termina en ROLLBACK.
-- Resultado esperado: una fila 'OK planes_fundador'.
-- ============================================================================

begin;

-- Aísla el cupo de otros datos de Sandbox: el ROLLBACK lo restaura.
delete from public.fundador_otorgamientos;

insert into public.empresas (id, nombre) values
    ('91000000-0000-4000-8000-00000000000e', 'TEST fundador otorgado'),
    ('92000000-0000-4000-8000-00000000000e', 'TEST fundador premium previo'),
    ('93000000-0000-4000-8000-00000000000e', 'TEST fundador regulariza'),
    ('94000000-0000-4000-8000-00000000000e', 'TEST fundador impago'),
    ('95000000-0000-4000-8000-00000000000e', 'TEST fundador baja'),
    ('96000000-0000-4000-8000-00000000000e', 'TEST fundador paso a free'),
    ('97000000-0000-4000-8000-00000000000e', 'TEST fundador sin cupo'),
    ('98000000-0000-4000-8000-00000000000e', 'TEST premium paso a free');

insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('91000000-0000-4000-8000-0000000000a1', '91000000-0000-4000-8000-00000000000e', 'admin', 'fu1@test.invalid', 'ADMIN');

insert into public.bodegas (id, empresa_id, nombre) values
    ('96000000-0000-4000-8000-0000000000b1', '96000000-0000-4000-8000-00000000000e', 'bodega'),
    ('98000000-0000-4000-8000-0000000000b1', '98000000-0000-4000-8000-00000000000e', 'bodega');

insert into public.productos (id, empresa_id, bodega_id, nombre, es_perecedero) values
    ('96000000-0000-4000-8000-0000000000c1', '96000000-0000-4000-8000-00000000000e', '96000000-0000-4000-8000-0000000000b1', 'p', true),
    ('98000000-0000-4000-8000-0000000000c1', '98000000-0000-4000-8000-00000000000e', '98000000-0000-4000-8000-0000000000b1', 'p', true);

-- Trial vencido: así el plan efectivo depende solo del pago y del estatus.
update public.suscripciones
set trial_inicio = now() - interval '721 hours', trial_fin = now() - interval '1 hour'
where empresa_id::text like '9_000000-0000-4000-8000-00000000000e';

create function pg_temp.plan(p_empresa uuid) returns text
language sql
as $$
    select private.plan_efectivo(s, now()) from public.suscripciones s where s.empresa_id = p_empresa;
$$;

create function pg_temp.pagar(p_empresa uuid, p_plan text) returns jsonb
language sql
as $$
    select private.registrar_pago(p_empresa, p_plan, now() + interval '30 days', 'test');
$$;

create function pg_temp.exige(p_obtenido text, p_esperado text, p_caso text) returns void
language plpgsql
as $$
begin
    if p_obtenido is distinct from p_esperado then
        raise exception '%: se esperaba %, se obtuvo %', p_caso, p_esperado, p_obtenido;
    end if;
end;
$$;

do $$
declare
    e1 constant uuid := '91000000-0000-4000-8000-00000000000e';
    e2 constant uuid := '92000000-0000-4000-8000-00000000000e';
    r jsonb;
begin
    -- FR-012: primer pago de Standard con cupo
    r := pg_temp.pagar(e1, 'STANDARD');
    perform pg_temp.exige(r->>'motivo', 'OTORGADO', 'FR-012 motivo');
    perform pg_temp.exige(r->>'fundador', 'true', 'FR-012 fundador');
    perform pg_temp.exige(pg_temp.plan(e1), 'PREMIUM', 'FR-012 plan');

    -- Una renovación no otorga de nuevo y conserva el estatus
    r := pg_temp.pagar(e1, 'STANDARD');
    perform pg_temp.exige(r->>'motivo', 'NO_APLICA', 'renovación motivo');
    perform pg_temp.exige(r->>'fundador', 'true', 'renovación fundador');

    -- FR-015: un Fundador que pasa a Premium pagado conserva el estatus
    r := pg_temp.pagar(e1, 'PREMIUM');
    perform pg_temp.exige(r->>'fundador', 'true', 'FR-015 a Premium');
    r := pg_temp.pagar(e1, 'STANDARD');
    perform pg_temp.exige(pg_temp.plan(e1), 'PREMIUM', 'FR-015 vuelve a Standard');

    -- FR-012: un pago previo de Premium no impide el otorgamiento
    r := pg_temp.pagar(e2, 'PREMIUM');
    perform pg_temp.exige(r->>'motivo', 'NO_APLICA', 'Premium no otorga');
    perform pg_temp.exige(r->>'fundador', 'false', 'Premium no es Fundador');
    r := pg_temp.pagar(e2, 'STANDARD');
    perform pg_temp.exige(r->>'motivo', 'OTORGADO', 'FR-012 tras Premium');
end $$;

-- ---------------------------------------------------------------- la RPC no revela el estatus
set local role authenticated;
select set_config('request.jwt.claims',
    '{"sub":"91000000-0000-4000-8000-0000000000a1","role":"authenticated"}', true);

do $$
declare
    r jsonb := public.obtener_entitlement();
begin
    perform pg_temp.exige(r->>'plan_efectivo', 'PREMIUM', 'RPC plan');
    perform pg_temp.exige(r->>'estado', 'PAGADO', 'RPC estado');
    if r::text ~* '(fundador|cupo|precio)' then
        raise exception 'FR-027: la RPC expone Fundador o cupo: %', r;
    end if;

    -- FR-019: sin acceso de clientes
    begin
        perform private.registrar_pago('91000000-0000-4000-8000-00000000000e', 'PREMIUM', now() + interval '1 day', 'intruso');
        raise exception 'FR-019: un cliente ejecutó registrar_pago' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;
    begin
        perform count(*) from public.fundador_otorgamientos;
        raise exception 'FR-019: un cliente lee los otorgamientos' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;
    begin
        perform count(*) from public.plan_cambios;
        raise exception 'FR-019: un cliente lee plan_cambios' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;
    begin
        perform count(*) from public.fundador_olas;
        raise exception 'FR-019: un cliente lee las olas' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;
end $$;

-- Durante la gracia sigue siendo Premium y su estado no es "gratuito"
reset role;
update public.suscripciones set pagado_hasta = now() - interval '1 hour'
where empresa_id = '91000000-0000-4000-8000-00000000000e';
set local role authenticated;

do $$
declare
    r jsonb := public.obtener_entitlement();
begin
    perform pg_temp.exige(r->>'plan_efectivo', 'PREMIUM', 'FR-015 gracia plan');
    perform pg_temp.exige(r->>'estado', 'PAGADO', 'FR-015 gracia estado');
end $$;

reset role;

do $$
declare
    v_fn text;
    v_rol text;
begin
    foreach v_fn in array array[
        'private.registrar_pago(uuid, text, timestamptz, text)',
        'private.registrar_baja(uuid, text)',
        'private.pasar_a_free(uuid, text)',
        'private.marcar_tester(uuid, boolean, text)'
    ] loop
        foreach v_rol in array array['anon', 'authenticated'] loop
            if has_function_privilege(v_rol, v_fn, 'execute') then
                raise exception 'FR-019: % puede ejecutar %', v_rol, v_fn;
            end if;
        end loop;
    end loop;
end $$;

-- ---------------------------------------------------------------- SC-009: gracia de impago
do $$
declare
    e3 constant uuid := '93000000-0000-4000-8000-00000000000e';
    e4 constant uuid := '94000000-0000-4000-8000-00000000000e';
    r jsonb;
    v_otor public.fundador_otorgamientos;
    v_v timestamptz;
begin
    -- V + 7 días - 1 s: regulariza y conserva
    perform pg_temp.pagar(e3, 'STANDARD');
    update public.suscripciones set pagado_hasta = now() - interval '168 hours' + interval '1 second' where empresa_id = e3;
    perform pg_temp.exige(pg_temp.plan(e3), 'PREMIUM', 'SC-009 V+7-1s plan');
    r := pg_temp.pagar(e3, 'STANDARD');
    perform pg_temp.exige(r->>'fundador', 'true', 'SC-009 regulariza');
    select * into strict v_otor from public.fundador_otorgamientos where empresa_id = e3;
    if v_otor.perdido_at is not null then raise exception 'SC-009: perdió el estatus al regularizar'; end if;

    -- V + 7 días exactos: ya no es Premium, aunque nadie haya ejecutado nada
    perform pg_temp.pagar(e4, 'STANDARD');
    v_v := now() - interval '168 hours';
    update public.suscripciones set pagado_hasta = v_v where empresa_id = e4;
    perform pg_temp.exige(pg_temp.plan(e4), 'FREE', 'SC-009 V+7 plan');

    r := pg_temp.pagar(e4, 'STANDARD');
    perform pg_temp.exige(r->>'fundador', 'false', 'SC-009 pierde');
    perform pg_temp.exige(pg_temp.plan(e4), 'STANDARD', 'SC-009 queda en Standard');
    select * into strict v_otor from public.fundador_otorgamientos where empresa_id = e4;
    perform pg_temp.exige(v_otor.motivo_perdida, 'IMPAGO', 'SC-009 motivo');
    if v_otor.perdido_at <> v_v + interval '168 hours' then
        raise exception 'R9: la pérdida no quedó fechada en V + 7 días: %', v_otor.perdido_at;
    end if;

    -- FR-016: no lo recupera
    r := pg_temp.pagar(e4, 'STANDARD');
    perform pg_temp.exige(r->>'fundador', 'false', 'FR-016 tras impago');
    perform pg_temp.exige(r->>'motivo', 'NO_APLICA', 'FR-016 motivo');

    -- La pérdida es permanente también a nivel de tabla
    begin
        update public.fundador_otorgamientos set perdido_at = null where empresa_id = e4;
        raise exception 'FR-016: perdido_at volvió a nulo' using errcode = 'XX000';
    exception when raise_exception then null;
    end;
end $$;

-- ---------------------------------------------------------------- baja y paso a Free
do $$
declare
    e5 constant uuid := '95000000-0000-4000-8000-00000000000e';
    e6 constant uuid := '96000000-0000-4000-8000-00000000000e';
    e8 constant uuid := '98000000-0000-4000-8000-00000000000e';
    r jsonb;
    s public.suscripciones;
    v_otor public.fundador_otorgamientos;
begin
    perform pg_temp.pagar(e5, 'STANDARD');
    perform private.registrar_baja(e5, 'test');
    perform pg_temp.exige(pg_temp.plan(e5), 'FREE', 'FR-015 baja');
    select * into strict v_otor from public.fundador_otorgamientos where empresa_id = e5;
    perform pg_temp.exige(v_otor.motivo_perdida, 'BAJA', 'motivo baja');
    select * into strict s from public.suscripciones where empresa_id = e5;
    if s.cobertura_fin <> now() then raise exception 'R7: cobertura_fin tras baja: %', s.cobertura_fin; end if;

    r := pg_temp.pagar(e5, 'STANDARD');
    perform pg_temp.exige(r->>'fundador', 'false', 'FR-016 tras baja');
    perform pg_temp.exige(pg_temp.plan(e5), 'STANDARD', 'recontrata en Standard');
    select * into strict s from public.suscripciones where empresa_id = e5;
    if s.cobertura_fin is not null or s.cobertura_lotes then raise exception 'R7: el pago no limpió la cobertura'; end if;

    perform pg_temp.pagar(e6, 'STANDARD');
    -- now() es fijo en la transacción: el estatus debe existir antes del instante de la baja.
    update public.fundador_otorgamientos set otorgado_at = now() - interval '1 day' where empresa_id = e6;
    perform private.pasar_a_free(e6, 'test');
    perform pg_temp.exige(pg_temp.plan(e6), 'FREE', 'FR-015 paso a Free');
    select * into strict v_otor from public.fundador_otorgamientos where empresa_id = e6;
    perform pg_temp.exige(v_otor.motivo_perdida, 'PASO_A_FREE', 'motivo paso a Free');
    select * into strict s from public.suscripciones where empresa_id = e6;
    if s.plan_pagado is not null or s.pagado_hasta is not null or s.cobertura_fin <> now() then
        raise exception 'R7: estado incorrecto tras paso a Free';
    end if;

    -- R7: el lote hecho con la capacidad entra en la ventana; fuera de ella no
    insert into public.lotes (producto_id, empresa_id, fecha_caducidad, created_at)
    values ('96000000-0000-4000-8000-0000000000c1', e6, now() + interval '9 days', now() - interval '1 hour');
    begin
        insert into public.lotes (producto_id, empresa_id, fecha_caducidad, created_at)
        values ('96000000-0000-4000-8000-0000000000c1', e6, now() + interval '9 days', now() + interval '168 hours');
        raise exception 'R7: se aceptó un lote fuera de la ventana' using errcode = 'XX000';
    exception when sqlstate 'SFP03' then null;
    end;

    -- R7: también para Premium pagado que no es Fundador
    perform pg_temp.pagar(e8, 'PREMIUM');
    perform private.pasar_a_free(e8, 'test');
    insert into public.lotes (producto_id, empresa_id, fecha_caducidad, created_at)
    values ('98000000-0000-4000-8000-0000000000c1', e8, now() + interval '9 days', now() - interval '1 hour');
end $$;

-- ---------------------------------------------------------------- cupo
do $$
declare
    e1 constant uuid := '91000000-0000-4000-8000-00000000000e';
    e7 constant uuid := '97000000-0000-4000-8000-00000000000e';
    r jsonb;
    n bigint;
begin
    -- FR-013: el otorgamiento sobrevive a la eliminación de la empresa
    delete from public.empresas where id = e1;
    select count(*) into n from public.fundador_otorgamientos where empresa_id = e1;
    if n <> 1 then raise exception 'FR-013: el otorgamiento no sobrevivió'; end if;

    -- FR-013: los perdidos y eliminados siguen contando
    insert into public.fundador_otorgamientos (empresa_id, ola)
    select gen_random_uuid(), 1
    from generate_series(1, 100 - (select count(*) from public.fundador_otorgamientos)::integer);

    -- SC-008: sin cupo, el pago se registra y queda en Standard
    r := pg_temp.pagar(e7, 'STANDARD');
    perform pg_temp.exige(r->>'motivo', 'CUPO_AGOTADO', 'SC-008 motivo');
    perform pg_temp.exige(r->>'fundador', 'false', 'SC-008 fundador');
    perform pg_temp.exige(pg_temp.plan(e7), 'STANDARD', 'SC-008 plan');
    select count(*) into n from public.fundador_otorgamientos;
    if n <> 100 then raise exception 'FR-013: hay % otorgamientos', n; end if;
end $$;

-- ---------------------------------------------------------------- operador y auditoría
do $$
declare
    e7 constant uuid := '97000000-0000-4000-8000-00000000000e';
    n bigint;
    v_antes bigint;
begin
    foreach n in array array[1, 2, 3, 4] loop
        begin
            case n
                when 1 then perform private.registrar_pago(e7, 'STANDARD', now() + interval '1 day', '  ');
                when 2 then perform private.registrar_baja(e7, '');
                when 3 then perform private.pasar_a_free(e7, null);
                else perform private.marcar_tester(e7, true, ' ');
            end case;
            raise exception 'FR-019: se aceptó un operador vacío' using errcode = 'XX000';
        exception when sqlstate '22023' then null;
        end;
    end loop;

    select count(*) into v_antes from public.plan_cambios where empresa_id = e7;
    perform private.marcar_tester(e7, true, 'test');
    perform pg_temp.exige(pg_temp.plan(e7), 'PREMIUM', 'FR-018 marcar tester');
    perform private.marcar_tester(e7, false, 'test');
    select count(*) into n from public.plan_cambios where empresa_id = e7 and operador = 'test';
    if n <> v_antes + 2 then raise exception 'FR-019: no quedó constancia de los cambios'; end if;

    begin
        update public.plan_cambios set operador = 'otro' where empresa_id = e7;
        raise exception 'FR-019: se editó plan_cambios' using errcode = 'XX000';
    exception when raise_exception then null;
    end;
    begin
        delete from public.plan_cambios where empresa_id = e7;
        raise exception 'FR-019: se borró plan_cambios' using errcode = 'XX000';
    exception when raise_exception then null;
    end;
end $$;

select 'OK planes_fundador' as resultado;
rollback;
