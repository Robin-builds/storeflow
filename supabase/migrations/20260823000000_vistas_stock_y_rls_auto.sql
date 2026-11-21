-- ============================================================================
-- Deuda D6 — reproducibilidad del esquema (Constitución, sección Ambientes)
--
-- Objetos que existen en Producción pero nunca se versionaron. Recuperados del catálogo
-- en vivo el 2026-11-20 (pg_get_viewdef / pg_get_functiondef):
--   - vistas productos_con_stock y bodegas_resumen (creadas el 23/08 desde el lado web,
--     ver .harness/PUENTE.md)
--   - event trigger ensure_rls + función rls_auto_enable (activa RLS automáticamente en
--     toda tabla nueva de public)
--
-- ⚠️ En Producción ya existen. Este archivo es para reconstruir Sandbox (o una base
-- local) desde cero. Es idempotente: ejecutarlo de nuevo no cambia nada.
-- ============================================================================

-- ---------------------------------------------------------------- vistas de stock
-- Principio I: stock derivado de movimientos. security_invoker = true → respetan RLS.

create or replace view public.productos_con_stock
with (security_invoker = true) as
select p.id,
       p.empresa_id,
       p.bodega_id,
       p.nombre,
       p.descripcion,
       p.sku,
       p.precio,
       p.stock_minimo,
       p.synced,
       p.synced_at,
       p.created_at,
       p.updated_at,
       p.es_perecedero,
       (coalesce(sum(m.cantidad), 0::bigint))::integer as stock_actual,
       (coalesce(sum(m.cantidad), 0::bigint) <= p.stock_minimo) as bajo_stock
from public.productos p
left join public.movimientos m on m.producto_id = p.id
group by p.id;

create or replace view public.bodegas_resumen
with (security_invoker = true) as
select b.id as bodega_id,
       b.empresa_id,
       b.nombre,
       b.ubicacion,
       count(pcs.id) as total_productos,
       coalesce(sum(pcs.stock_actual), 0::bigint) as stock_total,
       count(*) filter (where pcs.bajo_stock) as productos_stock_bajo
from public.bodegas b
left join public.productos_con_stock pcs on pcs.bodega_id = b.id
group by b.id;

-- ---------------------------------------------------------------- RLS automático
-- Principio III: ninguna tabla nueva de public nace sin RLS.

create or replace function public.rls_auto_enable()
returns event_trigger
language plpgsql
security definer
set search_path to 'pg_catalog'
as $function$
declare
  cmd record;
begin
  for cmd in
    select *
    from pg_event_trigger_ddl_commands()
    where command_tag in ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      and object_type in ('table','partitioned table')
  loop
     if cmd.schema_name is not null and cmd.schema_name in ('public') and cmd.schema_name not in ('pg_catalog','information_schema') and cmd.schema_name not like 'pg_toast%' and cmd.schema_name not like 'pg_temp%' then
      begin
        execute format('alter table if exists %s enable row level security', cmd.object_identity);
        raise log 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      exception
        when others then
          raise log 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      end;
     else
        raise log 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     end if;
  end loop;
end;
$function$;

do $$
begin
  if not exists (select 1 from pg_event_trigger where evtname = 'ensure_rls') then
    create event trigger ensure_rls on ddl_command_end
      when tag in ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      execute function public.rls_auto_enable();
  end if;
end
$$;
