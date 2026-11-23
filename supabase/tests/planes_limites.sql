-- ============================================================================
-- Planes & Entitlements, US-3: SC-005, FR-009, FR-010, FR-011, FR-025 y FR-026.
--
-- Todo corre en una transacción que termina en ROLLBACK.
-- Resultado esperado: una fila 'OK planes_limites'.
-- ============================================================================

begin;

-- Ejecuta una sentencia y exige que falle con ese SQLSTATE.
create function pg_temp.espera(p_sql text, p_estado text) returns void
language plpgsql
as $$
declare
    v_estado text;
begin
    begin
        execute p_sql;
    exception when others then
        get stacked diagnostics v_estado = returned_sqlstate;
        if v_estado <> p_estado then
            raise exception 'Se esperaba % y se obtuvo % (%) en: %', p_estado, v_estado, sqlerrm, p_sql;
        end if;
        return;
    end;
    raise exception 'Se esperaba % pero la operación pasó: %', p_estado, p_sql;
end;
$$;

-- F: Free. S: Standard en trial. A: trial de activación con lotes. N: activación sin lotes.
-- P: Premium vencido, dentro de la ventana. X: Premium vigente.
insert into public.empresas (id, nombre) values
    ('f1000000-0000-4000-8000-00000000000f', 'TEST limites free'),
    ('f2000000-0000-4000-8000-00000000000f', 'TEST limites standard'),
    ('f3000000-0000-4000-8000-00000000000f', 'TEST limites activacion'),
    ('f4000000-0000-4000-8000-00000000000f', 'TEST limites activacion sin lotes'),
    ('f5000000-0000-4000-8000-00000000000f', 'TEST limites premium vencido'),
    ('f6000000-0000-4000-8000-00000000000f', 'TEST limites premium');

-- Premium temporal: permite armar empresas que ya exceden su plan final.
update public.suscripciones
set plan_pagado = 'PREMIUM', pagado_hasta = now() + interval '1 day'
where empresa_id in ('f3000000-0000-4000-8000-00000000000f', 'f4000000-0000-4000-8000-00000000000f',
                     'f5000000-0000-4000-8000-00000000000f', 'f6000000-0000-4000-8000-00000000000f');

insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('f1000000-0000-4000-8000-0000000000a1', 'f1000000-0000-4000-8000-00000000000f', 'admin f', 'lf1@test.invalid', 'ADMIN'),
    ('f1000000-0000-4000-8000-0000000000a2', 'f1000000-0000-4000-8000-00000000000f', 'operador f', 'lf2@test.invalid', 'OPERADOR'),
    ('f2000000-0000-4000-8000-0000000000a1', 'f2000000-0000-4000-8000-00000000000f', 'admin s', 'ls1@test.invalid', 'ADMIN'),
    ('f3000000-0000-4000-8000-0000000000a1', 'f3000000-0000-4000-8000-00000000000f', 'admin a', 'la1@test.invalid', 'ADMIN'),
    ('f4000000-0000-4000-8000-0000000000a1', 'f4000000-0000-4000-8000-00000000000f', 'admin n', 'ln1@test.invalid', 'ADMIN'),
    ('f5000000-0000-4000-8000-0000000000a1', 'f5000000-0000-4000-8000-00000000000f', 'admin p', 'lp1@test.invalid', 'ADMIN'),
    ('f6000000-0000-4000-8000-0000000000a1', 'f6000000-0000-4000-8000-00000000000f', 'admin x', 'lx1@test.invalid', 'ADMIN');

insert into public.bodegas (id, empresa_id, nombre) values
    ('f1000000-0000-4000-8000-0000000000b1', 'f1000000-0000-4000-8000-00000000000f', 'unica'),
    ('f2000000-0000-4000-8000-0000000000b1', 'f2000000-0000-4000-8000-00000000000f', 'bodega 1'),
    ('f3000000-0000-4000-8000-0000000000b1', 'f3000000-0000-4000-8000-00000000000f', 'bodega 1'),
    ('f4000000-0000-4000-8000-0000000000b1', 'f4000000-0000-4000-8000-00000000000f', 'bodega 1'),
    ('f5000000-0000-4000-8000-0000000000b1', 'f5000000-0000-4000-8000-00000000000f', 'bodega 1'),
    ('f6000000-0000-4000-8000-0000000000b1', 'f6000000-0000-4000-8000-00000000000f', 'bodega 1');

-- La empresa en activación ya excede Standard
insert into public.bodegas (empresa_id, nombre)
select 'f3000000-0000-4000-8000-00000000000f', 'extra ' || i from generate_series(2, 7) i;

insert into public.productos (id, empresa_id, bodega_id, nombre, es_perecedero) values
    ('f1000000-0000-4000-8000-0000000000c1', 'f1000000-0000-4000-8000-00000000000f', 'f1000000-0000-4000-8000-0000000000b1', 'p', true),
    ('f2000000-0000-4000-8000-0000000000c1', 'f2000000-0000-4000-8000-00000000000f', 'f2000000-0000-4000-8000-0000000000b1', 'p', true),
    ('f3000000-0000-4000-8000-0000000000c1', 'f3000000-0000-4000-8000-00000000000f', 'f3000000-0000-4000-8000-0000000000b1', 'p', true),
    ('f4000000-0000-4000-8000-0000000000c1', 'f4000000-0000-4000-8000-00000000000f', 'f4000000-0000-4000-8000-0000000000b1', 'p', true),
    ('f5000000-0000-4000-8000-0000000000c1', 'f5000000-0000-4000-8000-00000000000f', 'f5000000-0000-4000-8000-0000000000b1', 'p', true),
    ('f6000000-0000-4000-8000-0000000000c1', 'f6000000-0000-4000-8000-00000000000f', 'f6000000-0000-4000-8000-0000000000b1', 'p', true);

-- Estado final de cada suscripción
update public.suscripciones
set trial_inicio = now() - interval '721 hours', trial_fin = now() - interval '1 hour'
where empresa_id in ('f1000000-0000-4000-8000-00000000000f', 'f5000000-0000-4000-8000-00000000000f');

update public.suscripciones
set plan_pagado = null, pagado_hasta = null, activado_en = clock_timestamp(), usaba_lotes = true
where empresa_id = 'f3000000-0000-4000-8000-00000000000f';

update public.suscripciones
set plan_pagado = null, pagado_hasta = null, activado_en = clock_timestamp(), usaba_lotes = false
where empresa_id = 'f4000000-0000-4000-8000-00000000000f';

update public.suscripciones
set pagado_hasta = now() - interval '1 hour'
where empresa_id = 'f5000000-0000-4000-8000-00000000000f';

-- ---------------------------------------------------------------- SC-005 y FR-010
-- El servidor decide con el plan real: un cliente con caché obsoleta recibe el mismo rechazo.
set local role authenticated;
select set_config('request.jwt.claims',
    '{"sub":"f1000000-0000-4000-8000-0000000000a1","role":"authenticated"}', true);

select pg_temp.espera($q$insert into public.bodegas (empresa_id, nombre) values ('f1000000-0000-4000-8000-00000000000f', 'segunda')$q$, 'SFP01');

-- FR-025: sin capacidad no se crean lotes, ni siquiera dentro de la ventana
select pg_temp.espera($q$insert into public.lotes (producto_id, empresa_id, fecha_caducidad, created_at) values ('f1000000-0000-4000-8000-0000000000c1', 'f1000000-0000-4000-8000-00000000000f', now() + interval '9 days', now() - interval '2 hours')$q$, 'SFP03');

-- FR-025: la entrada de un perecedero se registra sin lote
insert into public.movimientos (producto_id, tipo, cantidad, nota) values
    ('f1000000-0000-4000-8000-0000000000c1', 'ENTRADA', 5, 'sin lote');

-- ---------------------------------------------------------------- service role sujeto a límites
reset role;
set local role service_role;
select set_config('request.jwt.claims', '{"role":"service_role"}', true);

select pg_temp.espera($q$insert into public.usuarios (id, empresa_id, nombre, email, rol) values (gen_random_uuid(), 'f1000000-0000-4000-8000-00000000000f', 'tercero', 'lf3@test.invalid', 'OPERADOR')$q$, 'SFP02');
select pg_temp.espera($q$insert into public.bodegas (empresa_id, nombre) values ('f1000000-0000-4000-8000-00000000000f', 'segunda')$q$, 'SFP01');

reset role;

-- ---------------------------------------------------------------- límite exacto de Standard
do $$
declare
    s constant uuid := 'f2000000-0000-4000-8000-00000000000f';
    i integer;
begin
    for i in 2..5 loop
        insert into public.bodegas (empresa_id, nombre) values (s, 'bodega ' || i);
    end loop;
    for i in 2..10 loop
        insert into public.usuarios (id, empresa_id, nombre, email, rol)
        values (gen_random_uuid(), s, 'usuario ' || i, format('ls-%s@test.invalid', i), 'OPERADOR');
    end loop;
end $$;

select pg_temp.espera($q$insert into public.bodegas (empresa_id, nombre) values ('f2000000-0000-4000-8000-00000000000f', 'sexta')$q$, 'SFP01');
select pg_temp.espera($q$insert into public.usuarios (id, empresa_id, nombre, email, rol) values (gen_random_uuid(), 'f2000000-0000-4000-8000-00000000000f', 'once', 'ls11@test.invalid', 'OPERADOR')$q$, 'SFP02');

-- Standard no incluye lotes
select pg_temp.espera($q$insert into public.lotes (producto_id, empresa_id, fecha_caducidad) values ('f2000000-0000-4000-8000-0000000000c1', 'f2000000-0000-4000-8000-00000000000f', now() + interval '9 days')$q$, 'SFP03');

-- ---------------------------------------------------------------- FR-026: trial de activación
select pg_temp.espera($q$insert into public.bodegas (empresa_id, nombre) values ('f3000000-0000-4000-8000-00000000000f', 'octava')$q$, 'SFP01');

do $$
declare
    n bigint;
begin
    -- Lo que ya tenía sigue editable y conserva los lotes que usaba
    update public.bodegas set nombre = 'editada' where empresa_id = 'f3000000-0000-4000-8000-00000000000f';
    get diagnostics n = row_count;
    if n <> 7 then raise exception 'FR-026: solo % de 7 bodegas editables', n; end if;

    insert into public.lotes (producto_id, empresa_id, fecha_caducidad)
    values ('f3000000-0000-4000-8000-0000000000c1', 'f3000000-0000-4000-8000-00000000000f', now() + interval '9 days');
end $$;

-- Sin uso previo de lotes, la activación no los concede
select pg_temp.espera($q$insert into public.lotes (producto_id, empresa_id, fecha_caducidad) values ('f4000000-0000-4000-8000-0000000000c1', 'f4000000-0000-4000-8000-00000000000f', now() + interval '9 days')$q$, 'SFP03');

-- ---------------------------------------------------------------- R7: lote dentro de la ventana
insert into public.lotes (producto_id, empresa_id, fecha_caducidad, created_at) values
    ('f5000000-0000-4000-8000-0000000000c1', 'f5000000-0000-4000-8000-00000000000f', now() + interval '9 days', now() - interval '2 hours');

select pg_temp.espera($q$insert into public.lotes (producto_id, empresa_id, fecha_caducidad, created_at) values ('f5000000-0000-4000-8000-0000000000c1', 'f5000000-0000-4000-8000-00000000000f', now() + interval '9 days', now() + interval '8 days')$q$, 'SFP03');

-- ---------------------------------------------------------------- Premium sin límites
do $$
declare
    i integer;
begin
    for i in 2..12 loop
        insert into public.bodegas (empresa_id, nombre)
        values ('f6000000-0000-4000-8000-00000000000f', 'bodega ' || i);
    end loop;
    insert into public.lotes (producto_id, empresa_id, fecha_caducidad)
    values ('f6000000-0000-4000-8000-0000000000c1', 'f6000000-0000-4000-8000-00000000000f', now() + interval '9 days');
end $$;

-- ---------------------------------------------------------------- FR-011: valores del catálogo
update public.planes_catalogo set max_bodegas = 2 where plan = 'FREE';

insert into public.bodegas (empresa_id, nombre) values ('f1000000-0000-4000-8000-00000000000f', 'segunda');
select pg_temp.espera($q$insert into public.bodegas (empresa_id, nombre) values ('f1000000-0000-4000-8000-00000000000f', 'tercera')$q$, 'SFP01');

select 'OK planes_limites' as resultado;
rollback;
