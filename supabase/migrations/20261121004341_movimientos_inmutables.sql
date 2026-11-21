-- ============================================================================
-- Deuda D2 — Constitución v1.0.0, principio II (historial de movimientos inmutable)
--
-- Un movimiento no se edita ni se borra; los errores se corrigen con un AJUSTE nuevo.
-- Antes de esta migración la política movimientos_empresa (FOR ALL) permitía PATCH y
-- DELETE vía REST a cualquier usuario de la empresa.
--
-- Reglas del trigger:
--   UPDATE: solo pueden cambiar synced, synced_at y updated_at. Cualquier cambio en un
--           campo de negocio lanza excepción.
--   DELETE: prohibido.
--   Excepción: acciones referenciales de FK (pg_trigger_depth() > 1). Hoy esto cubre:
--     - borrar un lote  → lote_id    = NULL (ON DELETE SET NULL)
--     - borrar usuario  → usuario_id = NULL (ON DELETE SET NULL)
--     - borrar producto → borra sus movimientos (ON DELETE CASCADE)
--   El tercer caso es temporal: con el borrado lógico de productos (deuda D3) los
--   productos dejan de borrarse físicamente y la cascada deja de ocurrir.
--
-- Aplica a todos los roles, incluido service_role. Una corrección administrativa
-- excepcional exige desactivar el trigger de forma explícita, y debe quedar registrada.
--
-- Verificación: supabase/tests/movimientos_inmutables.sql
-- ============================================================================

create or replace function public.proteger_movimiento_inmutable()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    -- acción referencial de FK (cascade / set null) disparada desde otra tabla
    if pg_trigger_depth() > 1 then
        return coalesce(new, old);
    end if;

    if tg_op = 'DELETE' then
        raise exception 'Los movimientos son inmutables: no se pueden borrar (id=%). Registra un AJUSTE.', old.id
            using errcode = 'P0001';
    end if;

    if new.id          is distinct from old.id
    or new.producto_id is distinct from old.producto_id
    or new.tipo        is distinct from old.tipo
    or new.cantidad    is distinct from old.cantidad
    or new.nota        is distinct from old.nota
    or new.lote_id     is distinct from old.lote_id
    or new.usuario_id  is distinct from old.usuario_id
    or new.created_at  is distinct from old.created_at then
        raise exception 'Los movimientos son inmutables: solo se pueden marcar campos de sincronización (id=%). Registra un AJUSTE.', old.id
            using errcode = 'P0001';
    end if;

    return new;
end;
$$;

-- create or replace (PG14+) en vez de drop + create: mismo efecto, idempotente.
create or replace trigger movimientos_inmutables
    before update or delete on public.movimientos
    for each row execute function public.proteger_movimiento_inmutable();
