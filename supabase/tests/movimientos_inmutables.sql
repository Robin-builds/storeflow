-- ============================================================================
-- Test de inmutabilidad de movimientos — Constitución, principio II
--
-- Actuando como un usuario DE LA MISMA empresa (RLS lo deja pasar), intenta editar y
-- borrar un movimiento propio. Ambas cosas deben fallar. Verifica también lo que SÍ debe
-- seguir funcionando: marcar campos de sync y los efectos en cascada de FK (borrar un
-- lote → lote_id NULL; borrar un producto → borra sus movimientos, hasta que exista el
-- borrado lógico de D3).
--
-- Corre en una transacción con ROLLBACK: no deja datos.
-- Ejecutar:  psql "$STOREFLOW_DB_URL" -v ON_ERROR_STOP=1 -f supabase/tests/movimientos_inmutables.sql
-- Resultado esperado: una fila 'OK movimientos_inmutables'.
-- ============================================================================

begin;

insert into public.empresas (id, nombre) values
    ('d0000000-0000-4000-8000-00000000000d', 'TEST inmutables');
insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('d0000000-0000-4000-8000-0000000000d1', 'd0000000-0000-4000-8000-00000000000d', 'test d', 'd@test.invalid', 'ADMIN');
insert into public.bodegas (id, empresa_id, nombre) values
    ('d0000000-0000-4000-8000-0000000000d2', 'd0000000-0000-4000-8000-00000000000d', 'bodega D');
insert into public.productos (id, empresa_id, bodega_id, nombre) values
    ('d0000000-0000-4000-8000-0000000000d5', 'd0000000-0000-4000-8000-00000000000d', 'd0000000-0000-4000-8000-0000000000d2', 'producto D'),
    ('d0000000-0000-4000-8000-0000000000d6', 'd0000000-0000-4000-8000-00000000000d', 'd0000000-0000-4000-8000-0000000000d2', 'producto D2');
insert into public.lotes (id, producto_id, empresa_id, fecha_caducidad) values
    ('d0000000-0000-4000-8000-0000000000d7', 'd0000000-0000-4000-8000-0000000000d5', 'd0000000-0000-4000-8000-00000000000d', now() + interval '30 days');
insert into public.movimientos (id, producto_id, tipo, cantidad, nota, lote_id, usuario_id) values
    ('d0000000-0000-4000-8000-0000000000d8', 'd0000000-0000-4000-8000-0000000000d5', 'ENTRADA', 10, 'test', 'd0000000-0000-4000-8000-0000000000d7', 'd0000000-0000-4000-8000-0000000000d1'),
    ('d0000000-0000-4000-8000-0000000000d9', 'd0000000-0000-4000-8000-0000000000d6', 'ENTRADA', 5, 'test', null, null);

set local role authenticated;
select set_config('request.jwt.claims',
    '{"sub":"d0000000-0000-4000-8000-0000000000d1","role":"authenticated"}', true);

do $$
declare
    m  constant uuid := 'd0000000-0000-4000-8000-0000000000d8';
    m2 constant uuid := 'd0000000-0000-4000-8000-0000000000d9';
    fallas text[] := '{}';
    n bigint;
    v_lote uuid;
begin
    -- PROHIBIDO: editar campos de negocio
    begin
        update public.movimientos set cantidad = 999 where id = m;
        fallas := array_append(fallas, ('se pudo editar cantidad')::text);
    exception when others then
        if sqlstate <> 'P0001' then fallas := array_append(fallas, (('editar cantidad falló por otra causa: ' || sqlerrm))::text); end if;
    end;

    begin
        update public.movimientos set nota = 'reescrita', tipo = 'SALIDA' where id = m;
        fallas := array_append(fallas, ('se pudo editar nota/tipo')::text);
    exception when others then
        if sqlstate <> 'P0001' then fallas := array_append(fallas, (('editar nota falló por otra causa: ' || sqlerrm))::text); end if;
    end;

    -- PROHIBIDO: borrar un movimiento directamente
    begin
        delete from public.movimientos where id = m;
        fallas := array_append(fallas, ('se pudo borrar un movimiento')::text);
    exception when others then
        if sqlstate <> 'P0001' then fallas := array_append(fallas, (('borrar falló por otra causa: ' || sqlerrm))::text); end if;
    end;

    -- PERMITIDO: campos de sincronización
    begin
        update public.movimientos set synced = true, synced_at = now(), updated_at = now() where id = m;
        get diagnostics n = row_count;
        if n <> 1 then fallas := array_append(fallas, ('marcar synced no afectó la fila')::text); end if;
    exception when others then
        fallas := array_append(fallas, (('marcar synced fue bloqueado: ' || sqlerrm))::text);
    end;

    -- PERMITIDO: cascada FK — borrar lote deja lote_id en NULL
    begin
        delete from public.lotes where id = 'd0000000-0000-4000-8000-0000000000d7';
        select lote_id into v_lote from public.movimientos where id = m;
        if v_lote is not null then fallas := array_append(fallas, ('lote_id no quedó en NULL')::text); end if;
    exception when others then
        fallas := array_append(fallas, (('borrar lote fue bloqueado: ' || sqlerrm))::text);
    end;

    -- PERMITIDO (temporal, hasta D3): borrar producto borra sus movimientos en cascada
    begin
        delete from public.productos where id = 'd0000000-0000-4000-8000-0000000000d6';
        select count(*) into n from public.movimientos where id = m2;
        if n <> 0 then fallas := array_append(fallas, ('cascada de producto no borró sus movimientos')::text); end if;
    exception when others then
        fallas := array_append(fallas, (('borrar producto fue bloqueado: ' || sqlerrm))::text);
    end;

    if array_length(fallas, 1) > 0 then
        raise exception 'INMUTABILIDAD VIOLADA: %', array_to_string(fallas, ' | ');
    end if;
end $$;

select 'OK movimientos_inmutables' as resultado;
rollback;
