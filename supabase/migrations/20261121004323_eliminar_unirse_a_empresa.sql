-- ============================================================================
-- Deuda D1 — Constitución v1.0.0, principio III (aislamiento entre empresas)
--
-- unirse_a_empresa(p_empresa_id, ...) era SECURITY DEFINER y ejecutable por anon y
-- authenticated: cualquier usuario autenticado que conociera el UUID de una empresa
-- podía asignarse a ella como OPERADOR y leer todo su inventario.
--
-- No la usa la app Android (solo llama al RPC registrar_empresa) ni stockflow-web
-- (no hace llamadas .rpc). El alta de usuarios en una empresa existente la hace la Edge
-- Function registrar-usuario-empresa con service_role, validando que el que llama sea
-- ADMIN de esa empresa.
--
-- Verificación: supabase/tests/aislamiento_empresas.sql
-- ============================================================================

drop function if exists public.unirse_a_empresa(uuid, text, text);
