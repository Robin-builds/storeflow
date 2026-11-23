-- ============================================================================
-- Planes & Entitlements, US-5: SC-010 (emisión) y FR-017.
--
-- El reloj se controla moviendo el trial respecto de now(), que es fijo en la transacción.
-- Todo corre en una transacción que termina en ROLLBACK.
-- Resultado esperado: una fila 'OK planes_avisos'.
-- ============================================================================

begin;

insert into public.empresas (id, nombre) values
    ('a7000000-0000-4000-8000-00000000000e', 'TEST avisos trial'),
    ('a8000000-0000-4000-8000-00000000000e', 'TEST avisos pagada'),
    ('a9000000-0000-4000-8000-00000000000e', 'TEST avisos tester'),
    ('aa000000-0000-4000-8000-00000000000e', 'TEST avisos otra');

insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('a7000000-0000-4000-8000-0000000000a1', 'a7000000-0000-4000-8000-00000000000e', 'admin 1', 'av1@test.invalid', 'ADMIN');
insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('a7000000-0000-4000-8000-0000000000a2', 'a7000000-0000-4000-8000-00000000000e', 'admin 2', 'av2@test.invalid', 'ADMIN');
insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('a7000000-0000-4000-8000-0000000000a3', 'a7000000-0000-4000-8000-00000000000e', 'operador', 'av3@test.invalid', 'OPERADOR'),
    ('a8000000-0000-4000-8000-0000000000a1', 'a8000000-0000-4000-8000-00000000000e', 'admin', 'av4@test.invalid', 'ADMIN'),
    ('a9000000-0000-4000-8000-0000000000a1', 'a9000000-0000-4000-8000-00000000000e', 'admin', 'av5@test.invalid', 'ADMIN'),
    ('aa000000-0000-4000-8000-0000000000a1', 'aa000000-0000-4000-8000-00000000000e', 'admin', 'av6@test.invalid', 'ADMIN');

-- Deja el término del trial a p_falta de ahora.
create function pg_temp.mover(p_empresa uuid, p_falta interval) returns void
language sql
as $$
    update public.suscripciones
    set trial_inicio = now() + p_falta - interval '720 hours', trial_fin = now() + p_falta
    where empresa_id = p_empresa;
$$;

-- Avisos que recibe un usuario, en orden. Un asterisco marca los que ese usuario cerró.
create function pg_temp.avisos(p_usuario uuid) returns text
language plpgsql
as $$
declare
    v_r text;
begin
    perform set_config('request.jwt.claims',
        json_build_object('sub', p_usuario, 'role', 'authenticated')::text, true);
    select coalesce(string_agg(
        x.valor->>'momento' || case when (x.valor->>'cerrado')::boolean then '*' else '' end,
        ',' order by x.orden
    ), '')
    into v_r
    from jsonb_array_elements(coalesce(public.obtener_entitlement()->'avisos', '[]'::jsonb))
        with ordinality as x(valor, orden);
    return v_r;
end;
$$;

create function pg_temp.cerrar(p_usuario uuid, p_momento text) returns void
language plpgsql
as $$
begin
    perform set_config('request.jwt.claims',
        json_build_object('sub', p_usuario, 'role', 'authenticated')::text, true);
    perform public.cerrar_aviso_plan(p_momento);
end;
$$;

create function pg_temp.exige(p_obtenido text, p_esperado text, p_caso text) returns void
language plpgsql
as $$
begin
    if p_obtenido is distinct from p_esperado then
        raise exception '%: se esperaba [%], se obtuvo [%]', p_caso, p_esperado, p_obtenido;
    end if;
end;
$$;

do $$
declare
    e  constant uuid := 'a7000000-0000-4000-8000-00000000000e';
    a1 constant uuid := 'a7000000-0000-4000-8000-0000000000a1';
    a2 constant uuid := 'a7000000-0000-4000-8000-0000000000a2';
    op constant uuid := 'a7000000-0000-4000-8000-0000000000a3';
    n bigint;
begin
    -- Cada momento se alcanza exactamente en su instante
    perform pg_temp.mover(e, interval '240 hours');
    perform pg_temp.exige(pg_temp.avisos(a1), 'DIA_0', 'día 0');

    -- Solo ADMIN los ve y solo ADMIN los cierra
    perform pg_temp.exige(pg_temp.avisos(op), '', 'OPERADOR no recibe avisos');
    begin
        perform pg_temp.cerrar(op, 'DIA_0');
        raise exception 'FR-017: un OPERADOR cerró un aviso' using errcode = 'XX000';
    exception when insufficient_privilege then null;
    end;

    perform pg_temp.mover(e, interval '168 hours' + interval '1 second');
    perform pg_temp.exige(pg_temp.avisos(a1), 'DIA_0', '-7 días + 1 s');
    perform pg_temp.mover(e, interval '168 hours');
    perform pg_temp.exige(pg_temp.avisos(a1), 'DIA_0,MENOS_7', '-7 días');

    perform pg_temp.mover(e, interval '24 hours' + interval '1 second');
    perform pg_temp.exige(pg_temp.avisos(a1), 'DIA_0,MENOS_7', '-1 día + 1 s');
    perform pg_temp.mover(e, interval '24 hours');
    perform pg_temp.exige(pg_temp.avisos(a1), 'DIA_0,MENOS_7,MENOS_1', '-1 día');

    perform pg_temp.mover(e, interval '1 second');
    perform pg_temp.exige(pg_temp.avisos(a1), 'DIA_0,MENOS_7,MENOS_1', 'término - 1 s');
    perform pg_temp.mover(e, interval '0');
    perform pg_temp.exige(pg_temp.avisos(a1), 'DIA_0,MENOS_7,MENOS_1,TERMINO', 'término');

    perform pg_temp.mover(e, interval '-48 hours' + interval '1 second');
    perform pg_temp.exige(pg_temp.avisos(a1), 'DIA_0,MENOS_7,MENOS_1,TERMINO', '+2 días - 1 s');
    perform pg_temp.mover(e, interval '-48 hours');
    perform pg_temp.exige(pg_temp.avisos(a1), 'DIA_0,MENOS_7,MENOS_1,TERMINO,MAS_2', '+2 días');

    -- Emisión única, aunque consulten varios ADMIN y varias veces
    perform pg_temp.avisos(a1);
    perform pg_temp.avisos(a2);
    select count(*) into n from public.avisos_plan where empresa_id = e;
    if n <> 5 then raise exception 'FR-017: hay % avisos, se esperaban 5', n; end if;

    -- Cerrar uno cierra los anteriores, solo para ese ADMIN
    perform pg_temp.cerrar(a1, 'MENOS_1');
    perform pg_temp.exige(pg_temp.avisos(a1), 'DIA_0*,MENOS_7*,MENOS_1*,TERMINO,MAS_2', 'cierre con anteriores');
    perform pg_temp.exige(pg_temp.avisos(a2), 'DIA_0,MENOS_7,MENOS_1,TERMINO,MAS_2', 'el otro ADMIN no cambia');

    -- Idempotente
    perform pg_temp.cerrar(a1, 'MENOS_1');
    select count(*) into n from public.avisos_plan
    where empresa_id = e and momento = 'MENOS_1' and cardinality(cerrado_por) = 1;
    if n <> 1 then raise exception 'FR-017: el cierre no es idempotente'; end if;

    perform pg_temp.cerrar(a2, 'MAS_2');
    perform pg_temp.exige(pg_temp.avisos(a2), 'DIA_0*,MENOS_7*,MENOS_1*,TERMINO*,MAS_2*', 'cierre del último');

    begin
        perform pg_temp.cerrar(a1, 'OTRO');
        raise exception 'FR-017: se aceptó un momento inválido' using errcode = 'XX000';
    exception when sqlstate '22023' then null;
    end;
end $$;

-- ---------------------------------------------------------------- empresas con plan pagado
do $$
declare
    p constant uuid := 'a8000000-0000-4000-8000-00000000000e';
    t constant uuid := 'a9000000-0000-4000-8000-00000000000e';
    o constant uuid := 'aa000000-0000-4000-8000-00000000000e';
begin
    update public.suscripciones set plan_pagado = 'PREMIUM', pagado_hasta = now() + interval '10 days' where empresa_id = p;
    perform pg_temp.mover(p, interval '-72 hours');
    perform pg_temp.exige(pg_temp.avisos('a8000000-0000-4000-8000-0000000000a1'), 'DIA_0', 'pagada sin avisos de vencimiento');

    -- Un pago vencido no genera meses después un aviso de término del trial
    update public.suscripciones set pagado_hasta = now() - interval '1 hour' where empresa_id = p;
    perform pg_temp.exige(pg_temp.avisos('a8000000-0000-4000-8000-0000000000a1'), 'DIA_0', 'pago vencido');

    update public.suscripciones set es_tester = true where empresa_id = t;
    perform pg_temp.mover(t, interval '-72 hours');
    perform pg_temp.exige(pg_temp.avisos('a9000000-0000-4000-8000-0000000000a1'), 'DIA_0', 'tester sin avisos de vencimiento');

    -- Aislamiento: los cierres de otra empresa no la afectan
    perform pg_temp.exige(pg_temp.avisos('aa000000-0000-4000-8000-0000000000a1'), 'DIA_0', 'otra empresa');
end $$;

-- ---------------------------------------------------------------- sin acceso directo
do $$
begin
    if has_table_privilege('authenticated', 'public.avisos_plan', 'select')
       or has_table_privilege('authenticated', 'public.avisos_plan', 'update')
       or has_table_privilege('anon', 'public.avisos_plan', 'select') then
        raise exception 'FR-017: los clientes tienen acceso directo a avisos_plan';
    end if;
    if has_function_privilege('anon', 'public.cerrar_aviso_plan(text)', 'execute') then
        raise exception 'FR-017: anon puede cerrar avisos';
    end if;
    if not has_function_privilege('authenticated', 'public.cerrar_aviso_plan(text)', 'execute') then
        raise exception 'FR-017: authenticated no puede cerrar avisos';
    end if;
end $$;

select 'OK planes_avisos' as resultado;
rollback;
