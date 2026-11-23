-- ============================================================================
-- SC-017 / FR-031 — Constitución, principio II
--
-- Eliminar una bodega con productos debe fallar con SFP06, para que la cascada no borre
-- productos ni movimientos. Una bodega vacía se elimina, y eliminar una empresa completa
-- sigue funcionando.
--
-- Todo corre en una transacción que termina en ROLLBACK.
-- Resultado esperado: una fila 'OK bodega_sin_cascada'.
-- ============================================================================

begin;

insert into public.empresas (id, nombre) values
    ('d0000000-0000-4000-8000-00000000000d', 'TEST bodega sin cascada');

insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('d0000000-0000-4000-8000-0000000000d1', 'd0000000-0000-4000-8000-00000000000d',
     'test d', 'd@test.invalid', 'ADMIN');

insert into public.bodegas (id, empresa_id, nombre) values
    ('d0000000-0000-4000-8000-0000000000d2', 'd0000000-0000-4000-8000-00000000000d', 'con productos'),
    ('d0000000-0000-4000-8000-0000000000d3', 'd0000000-0000-4000-8000-00000000000d', 'vacia');

insert into public.productos (id, empresa_id, bodega_id, nombre) values
    ('d0000000-0000-4000-8000-0000000000d4', 'd0000000-0000-4000-8000-00000000000d',
     'd0000000-0000-4000-8000-0000000000d2', 'producto');

insert into public.movimientos (id, producto_id, tipo, cantidad, nota) values
    ('d0000000-0000-4000-8000-0000000000d5', 'd0000000-0000-4000-8000-0000000000d4',
     'ENTRADA', 10, 'test');

set local role authenticated;
select set_config('request.jwt.claims',
    '{"sub":"d0000000-0000-4000-8000-0000000000d1","role":"authenticated"}', true);

do $$
declare
    n bigint;
    estado text;
begin
    -- 1. Bodega con productos: rechazada con SFP06, sin borrar nada
    begin
        delete from public.bodegas where id = 'd0000000-0000-4000-8000-0000000000d2';
        raise exception 'FR-031 VIOLADO: se eliminó una bodega con productos' using errcode = 'XX000';
    exception
        when others then
            get stacked diagnostics estado = returned_sqlstate;
            if estado <> 'SFP06' then
                raise exception 'FR-031 VIOLADO: se esperaba SFP06 y se obtuvo % (%)', estado, sqlerrm;
            end if;
    end;

    select count(*) into n from public.productos where id = 'd0000000-0000-4000-8000-0000000000d4';
    if n <> 1 then raise exception 'FR-031 VIOLADO: el producto no sobrevivió'; end if;

    select count(*) into n from public.movimientos where id = 'd0000000-0000-4000-8000-0000000000d5';
    if n <> 1 then raise exception 'FR-031 VIOLADO: el movimiento no sobrevivió'; end if;

    -- 2. Bodega sin productos: se elimina
    delete from public.bodegas where id = 'd0000000-0000-4000-8000-0000000000d3';
    get diagnostics n = row_count;
    if n <> 1 then raise exception 'FR-031: no se pudo eliminar una bodega vacía'; end if;

    -- 3. Tras trasladar sus productos, la bodega se puede eliminar
    insert into public.bodegas (id, empresa_id, nombre) values
        ('d0000000-0000-4000-8000-0000000000d6', 'd0000000-0000-4000-8000-00000000000d', 'destino');
    update public.productos set bodega_id = 'd0000000-0000-4000-8000-0000000000d6'
    where id = 'd0000000-0000-4000-8000-0000000000d4';
    delete from public.bodegas where id = 'd0000000-0000-4000-8000-0000000000d2';
    get diagnostics n = row_count;
    if n <> 1 then raise exception 'FR-031: no se pudo eliminar la bodega tras el traslado'; end if;
end $$;

reset role;

-- 4. Eliminar la empresa sigue funcionando: la cascada de FK queda exenta
do $$
declare
    n bigint;
begin
    delete from public.empresas where id = 'd0000000-0000-4000-8000-00000000000d';
    get diagnostics n = row_count;
    if n <> 1 then raise exception 'FR-031: no se pudo eliminar la empresa'; end if;

    select count(*) into n from public.bodegas where empresa_id = 'd0000000-0000-4000-8000-00000000000d';
    if n <> 0 then raise exception 'FR-031: quedaron bodegas tras eliminar la empresa'; end if;
end $$;

select 'OK bodega_sin_cascada' as resultado;
rollback;
