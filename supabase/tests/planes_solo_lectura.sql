-- ============================================================================
-- Planes & Entitlements, US-2: SC-003, SC-004, SC-006, SC-013, SC-014,
-- FR-007, FR-011, FR-021, FR-022, FR-028 y FR-029.
--
-- Todo corre en una transacción que termina en ROLLBACK.
-- Resultado esperado: una fila 'OK planes_solo_lectura'.
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

-- ---------------------------------------------------------------- empresa E: 3 bodegas, 3 usuarios
insert into public.empresas (id, nombre) values
    ('e3000000-0000-4000-8000-00000000000e', 'TEST solo lectura'),
    ('e4000000-0000-4000-8000-00000000000e', 'TEST unica activa'),
    ('e5000000-0000-4000-8000-00000000000e', 'TEST activacion');

-- Premium temporal: permite armar datos que exceden el plan final (límites de M4).
update public.suscripciones
set plan_pagado = 'PREMIUM', pagado_hasta = now() + interval '1 day'
where empresa_id in ('e3000000-0000-4000-8000-00000000000e', 'e5000000-0000-4000-8000-00000000000e');

insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('e3000000-0000-4000-8000-0000000000a2', 'e3000000-0000-4000-8000-00000000000e', 'operador antiguo', 'e2@test.invalid', 'OPERADOR');
insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('e3000000-0000-4000-8000-0000000000a1', 'e3000000-0000-4000-8000-00000000000e', 'admin', 'e1@test.invalid', 'ADMIN');
insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('e3000000-0000-4000-8000-0000000000a3', 'e3000000-0000-4000-8000-00000000000e', 'excedente', 'e3@test.invalid', 'OPERADOR');

insert into public.bodegas (id, empresa_id, nombre, created_at) values
    ('e3000000-0000-4000-8000-0000000000b1', 'e3000000-0000-4000-8000-00000000000e', 'mas antigua', now());
insert into public.bodegas (id, empresa_id, nombre, created_at) values
    -- R6: la fecha enviada por el cliente no permite antedatar una bodega
    ('e3000000-0000-4000-8000-0000000000b2', 'e3000000-0000-4000-8000-00000000000e', 'antedatada', '2000-01-01');
insert into public.bodegas (id, empresa_id, nombre) values
    ('e3000000-0000-4000-8000-0000000000b3', 'e3000000-0000-4000-8000-00000000000e', 'vacia');

insert into public.atributo_templates (id, empresa_id, clave, etiqueta) values
    ('e3000000-0000-4000-8000-0000000000d1', 'e3000000-0000-4000-8000-00000000000e', 'color', 'Color');

insert into public.productos (id, empresa_id, bodega_id, nombre) values
    ('e3000000-0000-4000-8000-0000000000c1', 'e3000000-0000-4000-8000-00000000000e', 'e3000000-0000-4000-8000-0000000000b1', 'activo'),
    ('e3000000-0000-4000-8000-0000000000c2', 'e3000000-0000-4000-8000-00000000000e', 'e3000000-0000-4000-8000-0000000000b2', 'en solo lectura');

insert into public.lotes (id, producto_id, empresa_id, fecha_caducidad) values
    ('e3000000-0000-4000-8000-0000000000d2', 'e3000000-0000-4000-8000-0000000000c2', 'e3000000-0000-4000-8000-00000000000e', now() + interval '30 days');

insert into public.movimientos (id, producto_id, tipo, cantidad, nota, created_at) values
    ('e3000000-0000-4000-8000-0000000000f1', 'e3000000-0000-4000-8000-0000000000c1', 'ENTRADA', 10, 'test', now()),
    ('e3000000-0000-4000-8000-0000000000f2', 'e3000000-0000-4000-8000-0000000000c2', 'ENTRADA', 7, 'test', now()),
    ('e3000000-0000-4000-8000-0000000000f3', 'e3000000-0000-4000-8000-0000000000c2', 'ENTRADA', 3, 'antiguo', now() - interval '200 days');

-- Empresa con 2 bodegas vacías, para la regla de la única activa
insert into public.bodegas (id, empresa_id, nombre) values
    ('e4000000-0000-4000-8000-0000000000b1', 'e4000000-0000-4000-8000-00000000000e', 'unica activa');
insert into public.bodegas (id, empresa_id, nombre) values
    ('e4000000-0000-4000-8000-0000000000b2', 'e4000000-0000-4000-8000-00000000000e', 'excedente');

-- Empresa existente con más bodegas que Standard
insert into public.bodegas (empresa_id, nombre)
select 'e5000000-0000-4000-8000-00000000000e', 'bodega ' || i from generate_series(1, 7) i;

update public.suscripciones
set activado_en = clock_timestamp(), plan_pagado = null, pagado_hasta = null
where empresa_id = 'e5000000-0000-4000-8000-00000000000e';

create temp table stock_antes on commit drop as
select id, stock_actual from public.productos_con_stock
where empresa_id = 'e3000000-0000-4000-8000-00000000000e';

do $$
declare
    x constant uuid := 'e5000000-0000-4000-8000-00000000000e';
    n bigint;
begin
    -- R6: el servidor asigna la antigüedad
    if (select created_at from public.bodegas where id = 'e3000000-0000-4000-8000-0000000000b2') < now() then
        raise exception 'R6: se aceptó una bodega antedatada';
    end if;

    -- SC-013: en el trial de activación todo sigue activo aunque exceda Standard
    select count(*) into n from private.bodegas_activas(x);
    if n <> 7 then raise exception 'SC-013: % de 7 bodegas activas en el trial de activación', n; end if;
end $$;

-- ---------------------------------------------------------------- vence el trial
update public.suscripciones
set trial_inicio = now() - interval '721 hours', trial_fin = now() - interval '1 hour',
    plan_pagado = null, pagado_hasta = null
where empresa_id in ('e3000000-0000-4000-8000-00000000000e', 'e4000000-0000-4000-8000-00000000000e');

do $$
declare
    e constant uuid := 'e3000000-0000-4000-8000-00000000000e';
    n bigint;
    v_ids uuid[];
begin
    -- SC-003: no se borra nada y el stock difiere en 0
    select count(*) into n from public.bodegas where empresa_id = e;
    if n <> 3 then raise exception 'SC-003: quedaron % bodegas', n; end if;
    select count(*) into n
    from stock_antes a join public.productos_con_stock p on p.id = a.id
    where p.stock_actual <> a.stock_actual;
    if n <> 0 then raise exception 'SC-003: cambió el stock de % productos', n; end if;

    -- FR-021: el stock suma también los movimientos de más de 90 días
    if (select stock_actual from public.productos_con_stock where id = 'e3000000-0000-4000-8000-0000000000c2') <> 10 then
        raise exception 'FR-021: el stock no incluye los movimientos antiguos';
    end if;

    -- FR-007: queda activa la bodega más antigua
    select array_agg(id) into v_ids from private.bodegas_activas(e) id;
    if v_ids <> array['e3000000-0000-4000-8000-0000000000b1'::uuid] then
        raise exception 'FR-007: bodegas activas incorrectas: %', v_ids;
    end if;

    -- FR-007: primero el ADMIN más antiguo, aunque otro usuario sea anterior
    select array_agg(id order by id) into v_ids from private.usuarios_activos(e) id;
    if v_ids <> array['e3000000-0000-4000-8000-0000000000a1'::uuid, 'e3000000-0000-4000-8000-0000000000a2'::uuid] then
        raise exception 'FR-007: usuarios activos incorrectos: %', v_ids;
    end if;
end $$;

-- ---------------------------------------------------------------- como ADMIN activo
set local role authenticated;
select set_config('request.jwt.claims',
    '{"sub":"e3000000-0000-4000-8000-0000000000a1","role":"authenticated"}', true);

select pg_temp.espera($q$update public.bodegas set nombre = 'x' where id = 'e3000000-0000-4000-8000-0000000000b2'$q$, 'SFP04');
select pg_temp.espera($q$delete from public.bodegas where id = 'e3000000-0000-4000-8000-0000000000b3'$q$, 'SFP04');
select pg_temp.espera($q$update public.productos set nombre = 'x' where id = 'e3000000-0000-4000-8000-0000000000c2'$q$, 'SFP04');
select pg_temp.espera($q$delete from public.productos where id = 'e3000000-0000-4000-8000-0000000000c2'$q$, 'SFP04');
select pg_temp.espera($q$update public.productos set bodega_id = 'e3000000-0000-4000-8000-0000000000b2' where id = 'e3000000-0000-4000-8000-0000000000c1'$q$, 'SFP04');
select pg_temp.espera($q$update public.productos set bodega_id = 'e3000000-0000-4000-8000-0000000000b1' where id = 'e3000000-0000-4000-8000-0000000000c2'$q$, 'SFP04');
-- FR-028: un producto nuevo se rechaza aunque su fecha esté en la ventana
select pg_temp.espera($q$insert into public.productos (empresa_id, bodega_id, nombre, created_at) values ('e3000000-0000-4000-8000-00000000000e', 'e3000000-0000-4000-8000-0000000000b2', 'nuevo', now() - interval '2 hours')$q$, 'SFP04');
select pg_temp.espera($q$insert into public.producto_atributos (producto_id, template_id, valor) values ('e3000000-0000-4000-8000-0000000000c2', 'e3000000-0000-4000-8000-0000000000d1', 'rojo')$q$, 'SFP04');
select pg_temp.espera($q$update public.lotes set numero_lote = 'x' where id = 'e3000000-0000-4000-8000-0000000000d2'$q$, 'SFP04');
select pg_temp.espera($q$delete from public.lotes where id = 'e3000000-0000-4000-8000-0000000000d2'$q$, 'SFP04');
-- SC-014: la fecha exacta de vencimiento + 7 días ya no entra
select pg_temp.espera($q$insert into public.movimientos (producto_id, tipo, cantidad, nota, created_at) select 'e3000000-0000-4000-8000-0000000000c2', 'ENTRADA', 1, 'fuera', trial_fin + interval '168 hours' from public.suscripciones$q$, 'SFP04');
select pg_temp.espera($q$insert into public.lotes (producto_id, empresa_id, fecha_caducidad, created_at) select 'e3000000-0000-4000-8000-0000000000c2', empresa_id, now() + interval '9 days', trial_fin + interval '168 hours' from public.suscripciones$q$, 'SFP04');
-- FR-022: tras el downgrade los movimientos siguen siendo inmutables
select pg_temp.espera($q$update public.movimientos set cantidad = 99 where id = 'e3000000-0000-4000-8000-0000000000f2'$q$, 'P0001');
select pg_temp.espera($q$delete from public.movimientos where id = 'e3000000-0000-4000-8000-0000000000f2'$q$, 'P0001');

do $$
declare
    r jsonb;
    v_fecha timestamptz;
    n bigint;
begin
    -- SC-004: la bodega en solo lectura se puede leer
    select count(*) into n from public.bodegas where id = 'e3000000-0000-4000-8000-0000000000b2';
    if n <> 1 then raise exception 'SC-004: no se puede leer la bodega en solo lectura'; end if;

    -- La bodega activa sigue editable
    update public.productos set nombre = 'editado' where id = 'e3000000-0000-4000-8000-0000000000c1';
    get diagnostics n = row_count;
    if n <> 1 then raise exception 'la bodega activa dejó de ser editable'; end if;

    insert into public.movimientos (id, producto_id, tipo, cantidad, nota) values
        ('e3000000-0000-4000-8000-0000000000f4', 'e3000000-0000-4000-8000-0000000000c1', 'ENTRADA', 1, 'activa');
    select count(*) into n from public.movimientos_fuera_plan where movimiento_id = 'e3000000-0000-4000-8000-0000000000f4';
    if n <> 0 then raise exception 'FR-028: se marcó un movimiento de bodega activa'; end if;

    -- SC-014: vencimiento + 7 días - 1 s entra, queda marcado y conserva sus campos
    select trial_fin + interval '168 hours' - interval '1 second' into v_fecha from public.suscripciones;
    insert into public.movimientos (id, producto_id, tipo, cantidad, nota, created_at) values
        ('e3000000-0000-4000-8000-0000000000f5', 'e3000000-0000-4000-8000-0000000000c2', 'SALIDA', -2, 'en ventana', v_fecha);
    select count(*) into n from public.movimientos_fuera_plan where movimiento_id = 'e3000000-0000-4000-8000-0000000000f5';
    if n <> 1 then raise exception 'SC-014: el movimiento en ventana no quedó marcado'; end if;
    select count(*) into n from public.movimientos
    where id = 'e3000000-0000-4000-8000-0000000000f5' and cantidad = -2 and nota = 'en ventana' and created_at = v_fecha;
    if n <> 1 then raise exception 'SC-014: el movimiento en ventana cambió sus campos'; end if;

    -- FR-011: conjuntos activos sin valores de límites
    r := public.obtener_entitlement();
    if r->>'plan_efectivo' <> 'FREE' or (r->>'bloqueado')::boolean then
        raise exception 'plan incorrecto: %', r;
    end if;
    if not (r->'bodegas_solo_lectura' ? 'e3000000-0000-4000-8000-0000000000b2'
        and r->'bodegas_solo_lectura' ? 'e3000000-0000-4000-8000-0000000000b3')
        or r->'bodegas_solo_lectura' ? 'e3000000-0000-4000-8000-0000000000b1' then
        raise exception 'bodegas_solo_lectura incorrecto: %', r->'bodegas_solo_lectura';
    end if;
    if r->'degradacion_free'->'bodegas_activas' <> '["e3000000-0000-4000-8000-0000000000b1"]'::jsonb then
        raise exception 'R22: degradacion_free incorrecta: %', r->'degradacion_free';
    end if;
    if (r->'permisos'->>'crear_bodega')::boolean or (r->'permisos'->>'crear_usuario')::boolean then
        raise exception 'permisos incorrectos en Free: %', r->'permisos';
    end if;
    if r::text ~* '(max_bodegas|max_usuarios|historial_dias|precio|cupo)' then
        raise exception 'FR-011: la RPC expone límites: %', r;
    end if;
end $$;

-- ---------------------------------------------------------------- como usuario excedente
select set_config('request.jwt.claims',
    '{"sub":"e3000000-0000-4000-8000-0000000000a3","role":"authenticated"}', true);

-- FR-029: se identifica con auth.uid(), aunque el payload declare a otro usuario
select pg_temp.espera($q$insert into public.movimientos (producto_id, tipo, cantidad, nota, usuario_id, created_at) select 'e3000000-0000-4000-8000-0000000000c1', 'ENTRADA', 1, 'fuera', 'e3000000-0000-4000-8000-0000000000a1', trial_fin + interval '168 hours' from public.suscripciones$q$, 'SFP05');
select pg_temp.espera($q$update public.lotes set numero_lote = 'x' where id = 'e3000000-0000-4000-8000-0000000000d2'$q$, 'SFP05');

do $$
declare
    r jsonb;
    n bigint;
begin
    r := public.obtener_entitlement();
    if not (r->>'bloqueado')::boolean then raise exception 'FR-029: el excedente no quedó bloqueado'; end if;
    if (select array_agg(k order by k) from jsonb_object_keys(r) k) <> array['ahora_servidor', 'bloqueado', 'empresa_id', 'rol'] then
        raise exception 'R20: el excedente recibe campos de más: %', r;
    end if;

    insert into public.movimientos (id, producto_id, tipo, cantidad, nota, usuario_id, created_at) values
        ('e3000000-0000-4000-8000-0000000000f6', 'e3000000-0000-4000-8000-0000000000c1', 'ENTRADA', 1, 'excedente',
         'e3000000-0000-4000-8000-0000000000a1', now() - interval '30 minutes');
    select count(*) into n from public.movimientos_fuera_plan where movimiento_id = 'e3000000-0000-4000-8000-0000000000f6';
    if n <> 1 then raise exception 'FR-029: el movimiento del excedente no quedó marcado'; end if;
end $$;

-- ---------------------------------------------------------------- única activa con excedentes
reset role;
insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('e4000000-0000-4000-8000-0000000000a1', 'e4000000-0000-4000-8000-00000000000e', 'admin', 'e4@test.invalid', 'ADMIN');
set local role authenticated;
select set_config('request.jwt.claims',
    '{"sub":"e4000000-0000-4000-8000-0000000000a1","role":"authenticated"}', true);
select pg_temp.espera($q$delete from public.bodegas where id = 'e4000000-0000-4000-8000-0000000000b1'$q$, 'SFP04');

-- ---------------------------------------------------------------- SC-006: reactivación
reset role;
update public.suscripciones
set plan_pagado = 'PREMIUM', pagado_hasta = now() + interval '10 days'
where empresa_id = 'e3000000-0000-4000-8000-00000000000e';

set local role authenticated;
select set_config('request.jwt.claims',
    '{"sub":"e3000000-0000-4000-8000-0000000000a3","role":"authenticated"}', true);

do $$
declare
    r jsonb;
    n bigint;
begin
    r := public.obtener_entitlement();
    if (r->>'bloqueado')::boolean or r->>'plan_efectivo' <> 'PREMIUM' or r->'bodegas_solo_lectura' <> '[]'::jsonb then
        raise exception 'SC-006: no se reactivó: %', r;
    end if;

    update public.productos set nombre = 'reactivado' where id = 'e3000000-0000-4000-8000-0000000000c2';
    get diagnostics n = row_count;
    if n <> 1 then raise exception 'SC-006: el producto no aceptó edición'; end if;

    update public.bodegas set nombre = 'reactivada' where id = 'e3000000-0000-4000-8000-0000000000b2';
    get diagnostics n = row_count;
    if n <> 1 then raise exception 'SC-006: la bodega no aceptó edición'; end if;
end $$;

reset role;
select 'OK planes_solo_lectura' as resultado;
rollback;
