-- ============================================================================
-- M2 — Planes & Entitlements: defectos P3 y P4 (FR-031, R18, principios II y III)
--
-- P4: elimina la política usuario_insert_propio. Ya se eliminó con la migración
--     20261121161643; se repite con IF EXISTS para que esta rama quede correcta
--     aunque todavía no contenga ese archivo.
-- P3: una bodega con productos no se puede eliminar, porque la cascada borraría sus
--     productos y movimientos. Las acciones de FK quedan exentas (pg_trigger_depth() > 1)
--     para que eliminar una empresa siga funcionando.
--
-- Verificación: supabase/tests/aislamiento_empresas.sql y bodega_sin_cascada.sql
-- ============================================================================

drop policy if exists usuario_insert_propio on public.usuarios;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create or replace function private.rechazar_delete_bodega_con_productos()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if pg_trigger_depth() > 1 then
        return old;
    end if;

    if exists (select 1 from public.productos p where p.bodega_id = old.id) then
        raise exception 'La bodega tiene productos y no se puede eliminar'
            using errcode = 'SFP06';
    end if;

    return old;
end;
$$;

revoke all on function private.rechazar_delete_bodega_con_productos()
    from public, anon, authenticated;

create trigger bodegas_sin_cascada
    before delete on public.bodegas
    for each row execute function private.rechazar_delete_bodega_con_productos();
