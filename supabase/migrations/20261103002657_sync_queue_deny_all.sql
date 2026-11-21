-- ============================================================================
-- Recuperada del historial remoto (supabase_migrations.schema_migrations) el
-- 2026-11-20: se aplicó en producción el 03/11 pero no estaba versionada en el repo.
-- Cierra la política abierta sync_queue_auth que crea la migración del 08/08.
-- La cola de sync vive solo en Room; la tabla remota no tiene uso (deuda D7).
-- ============================================================================
do $$
begin
  if to_regclass('public.sync_queue') is not null then
    alter table public.sync_queue enable row level security;
    drop policy if exists sync_queue_auth on public.sync_queue;
    revoke all on table public.sync_queue from anon, authenticated;
  end if;
end
$$;
