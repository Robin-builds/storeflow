-- ============================================================================
-- Test de aislamiento entre empresas — Constitución, principio III
--
-- Crea dos empresas de prueba (A y B) y, actuando como un usuario de A con el rol
-- `authenticated` (igual que un request REST real), intenta leer y escribir datos de B
-- SIN filtrar por empresa_id en la consulta. También intenta que un usuario sin empresa
-- se una a B vía RPC.
--
-- Todo corre dentro de una transacción que termina en ROLLBACK: no deja datos.
-- Si algo se filtra, lanza una excepción que lista cada violación.
--
-- Ejecutar:  psql "$STOREFLOW_DB_URL" -v ON_ERROR_STOP=1 -f supabase/tests/aislamiento_empresas.sql
--            (o pegar en el SQL editor / MCP execute_sql)
-- Resultado esperado: una fila 'OK aislamiento_empresas'.
-- ============================================================================

begin;

-- ---------------------------------------------------------------- datos (como postgres)
insert into public.empresas (id, nombre) values
    ('a0000000-0000-4000-8000-00000000000a', 'TEST aislamiento A'),
    ('b0000000-0000-4000-8000-00000000000b', 'TEST aislamiento B');

insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('a0000000-0000-4000-8000-0000000000a1', 'a0000000-0000-4000-8000-00000000000a', 'test a', 'a@test.invalid', 'ADMIN'),
    ('b0000000-0000-4000-8000-0000000000b1', 'b0000000-0000-4000-8000-00000000000b', 'test b', 'b@test.invalid', 'ADMIN');

insert into public.bodegas (id, empresa_id, nombre) values
    ('a0000000-0000-4000-8000-0000000000a2', 'a0000000-0000-4000-8000-00000000000a', 'bodega A'),
    ('b0000000-0000-4000-8000-0000000000b2', 'b0000000-0000-4000-8000-00000000000b', 'bodega B');

insert into public.proveedores (id, empresa_id, nombre) values
    ('b0000000-0000-4000-8000-0000000000b3', 'b0000000-0000-4000-8000-00000000000b', 'proveedor B');

insert into public.atributo_templates (id, empresa_id, clave, etiqueta) values
    ('b0000000-0000-4000-8000-0000000000b4', 'b0000000-0000-4000-8000-00000000000b', 'color', 'Color');

insert into public.productos (id, empresa_id, bodega_id, nombre) values
    ('b0000000-0000-4000-8000-0000000000b5', 'b0000000-0000-4000-8000-00000000000b', 'b0000000-0000-4000-8000-0000000000b2', 'producto B');

insert into public.producto_atributos (producto_id, template_id, valor) values
    ('b0000000-0000-4000-8000-0000000000b5', 'b0000000-0000-4000-8000-0000000000b4', 'rojo');

-- Los lotes requieren una capacidad de plan (M4).
update public.suscripciones set es_tester = true
where empresa_id = 'b0000000-0000-4000-8000-00000000000b';

insert into public.lotes (id, producto_id, empresa_id, fecha_caducidad) values
    ('b0000000-0000-4000-8000-0000000000b6', 'b0000000-0000-4000-8000-0000000000b5', 'b0000000-0000-4000-8000-00000000000b', now() + interval '30 days');

insert into public.movimientos (id, producto_id, tipo, cantidad, nota, lote_id) values
    ('b0000000-0000-4000-8000-0000000000b7', 'b0000000-0000-4000-8000-0000000000b5', 'ENTRADA', 10, 'test', 'b0000000-0000-4000-8000-0000000000b6');

-- ---------------------------------------------------------------- actuar como usuario de A
set local role authenticated;
select set_config('request.jwt.claims',
    '{"sub":"a0000000-0000-4000-8000-0000000000a1","role":"authenticated"}', true);

do $$
declare
    ea constant uuid := 'a0000000-0000-4000-8000-00000000000a';
    eb constant uuid := 'b0000000-0000-4000-8000-00000000000b';
    pb constant uuid := 'b0000000-0000-4000-8000-0000000000b5';
    fallas text[] := '{}';
    n bigint;
    t text;
begin
    -- 1. LECTURA: ninguna fila de otra empresa (incluye datos reales de producción)
    foreach t in array array['usuarios','bodegas','proveedores','atributo_templates',
                             'productos','lotes','productos_con_stock','bodegas_resumen'] loop
        execute format('select count(*) from public.%I where empresa_id <> $1', t) into n using ea;
        if n > 0 then fallas := array_append(fallas, (format('lectura %s: %s filas ajenas visibles', t, n))::text); end if;
    end loop;

    select count(*) into n from public.empresas where id <> ea;
    if n > 0 then fallas := array_append(fallas, (format('lectura empresas: %s ajenas visibles', n))::text); end if;

    select count(*) into n from public.movimientos where producto_id = pb;
    if n > 0 then fallas := array_append(fallas, ('lectura movimientos de B visible')::text); end if;

    select count(*) into n from public.producto_atributos where producto_id = pb;
    if n > 0 then fallas := array_append(fallas, ('lectura producto_atributos de B visible')::text); end if;

    -- sync_queue: sin GRANT para authenticated (permission denied) o RLS sin políticas (0 filas)
    begin
        select count(*) into n from public.sync_queue;
        if n > 0 then fallas := array_append(fallas, (format('lectura sync_queue: %s filas visibles', n))::text); end if;
    exception when insufficient_privilege then null; end;

    -- 2. ESCRITURA sobre filas de B: UPDATE/DELETE deben afectar 0 filas
    update public.productos set nombre = 'hackeado' where id = pb;
    get diagnostics n = row_count;
    if n > 0 then fallas := array_append(fallas, ('update productos de B')::text); end if;

    update public.movimientos set nota = 'hackeado' where producto_id = pb;
    get diagnostics n = row_count;
    if n > 0 then fallas := array_append(fallas, ('update movimientos de B')::text); end if;

    delete from public.bodegas where empresa_id = eb;
    get diagnostics n = row_count;
    if n > 0 then fallas := array_append(fallas, ('delete bodegas de B')::text); end if;

    delete from public.producto_atributos where producto_id = pb;
    get diagnostics n = row_count;
    if n > 0 then fallas := array_append(fallas, ('delete producto_atributos de B')::text); end if;

    -- 3. INSERCIÓN apuntando a B: debe ser rechazada por RLS
    begin
        insert into public.productos (empresa_id, bodega_id, nombre)
        values (eb, 'b0000000-0000-4000-8000-0000000000b2', 'intruso');
        fallas := array_append(fallas, ('insert productos en B')::text);
    exception when insufficient_privilege then null; end;

    begin
        insert into public.movimientos (producto_id, tipo, cantidad, nota)
        values (pb, 'AJUSTE', -10, 'intruso');
        fallas := array_append(fallas, ('insert movimientos en producto de B')::text);
    exception when insufficient_privilege then null; end;

    begin
        insert into public.producto_atributos (producto_id, template_id, valor)
        values (pb, 'b0000000-0000-4000-8000-0000000000b4', 'intruso');
        fallas := array_append(fallas, ('insert producto_atributos en producto de B')::text);
    exception when insufficient_privilege or unique_violation then null; end;

    begin
        insert into public.sync_queue (entidad_tipo, entidad_id, operacion, payload)
        values ('x', gen_random_uuid(), 'INSERT', '{}');
        fallas := array_append(fallas, ('insert sync_queue')::text);
    exception when insufficient_privilege then null; end;

    if array_length(fallas, 1) > 0 then
        raise exception 'AISLAMIENTO VIOLADO: %', array_to_string(fallas, ' | ');
    end if;
end $$;

-- ---------------------------------------------------------------- usuario sin empresa intenta unirse a B
select set_config('request.jwt.claims',
    '{"sub":"c0000000-0000-4000-8000-0000000000c1","role":"authenticated"}', true);

do $$
begin
    begin
        execute 'select public.unirse_a_empresa($1, $2, $3)'
            using 'b0000000-0000-4000-8000-00000000000b'::uuid, 'intruso', 'c@test.invalid';
        raise exception 'AISLAMIENTO VIOLADO: un usuario sin empresa se unió a B vía unirse_a_empresa';
    exception
        when undefined_function or insufficient_privilege then null;
    end;

    -- P4: una cuenta autenticada sin fila en usuarios no puede insertarse en B
    begin
        insert into public.usuarios (id, empresa_id, nombre, email, rol)
        values ('c0000000-0000-4000-8000-0000000000c1', 'b0000000-0000-4000-8000-00000000000b',
                'intruso', 'c@test.invalid', 'ADMIN');
        raise exception 'AISLAMIENTO VIOLADO: una cuenta sin empresa se insertó en usuarios de B';
    exception
        when insufficient_privilege then null;
    end;
end $$;

select 'OK aislamiento_empresas' as resultado;
rollback;
