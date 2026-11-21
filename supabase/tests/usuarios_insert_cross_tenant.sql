-- ============================================================================
-- Regresión: cross-tenant insert en public.usuarios
--
-- 1. Intrusión: un usuario autenticado sin fila en usuarios NO puede insertarse en una
--    empresa existente (42501 por RLS).
-- 2. Happy path: registrar_empresa() sigue creando empresa, usuario ADMIN y bodega.
--
-- Todo corre dentro de una transacción que termina en ROLLBACK: no deja datos.
--
-- Ejecutar:  psql "$STOREFLOW_DB_URL" -v ON_ERROR_STOP=1 -f supabase/tests/usuarios_insert_cross_tenant.sql
--            (o pegar en el SQL editor / MCP execute_sql)
-- Resultado esperado: una fila 'OK usuarios_insert_cross_tenant'.
-- ============================================================================

begin;

insert into public.empresas (id, nombre) values
    ('d0000000-0000-4000-8000-00000000000d', 'TEST empresa victima');

set local role authenticated;

-- ---------------------------------------------------------------- 1. intrusión
select set_config('request.jwt.claims',
    '{"sub":"e0000000-0000-4000-8000-0000000000e1","role":"authenticated"}', true);

do $$
begin
    begin
        insert into public.usuarios (id, empresa_id, nombre, email, rol)
        values ('e0000000-0000-4000-8000-0000000000e1',
                'd0000000-0000-4000-8000-00000000000d',
                'atacante', 'atacante@test.invalid', 'ADMIN');
        raise exception 'AISLAMIENTO VIOLADO: un usuario sin empresa se insertó en una empresa ajena';
    exception
        when insufficient_privilege then
            if sqlerrm not like '%row-level security%' then
                raise exception 'Falló con 42501 pero no por RLS: %', sqlerrm;
            end if;
    end;
end $$;

-- ---------------------------------------------------------------- 2. happy path
select set_config('request.jwt.claims',
    '{"sub":"f0000000-0000-4000-8000-0000000000f1","role":"authenticated"}', true);

do $$
declare
    v_empresa uuid;
    v_rol text;
    v_usuario_empresa uuid;
    v_bodegas bigint;
begin
    v_empresa := (public.registrar_empresa('Test SpA', 'retail', 'nuevo@test.invalid') ->> 'empresa_id')::uuid;

    if v_empresa is null then
        raise exception 'registrar_empresa no devolvió empresa_id';
    end if;

    select rol, empresa_id into v_rol, v_usuario_empresa
    from public.usuarios where id = 'f0000000-0000-4000-8000-0000000000f1';

    if v_rol is distinct from 'ADMIN' or v_usuario_empresa is distinct from v_empresa then
        raise exception 'registrar_empresa no creó el usuario ADMIN en su empresa (rol=%, empresa=%)',
            v_rol, v_usuario_empresa;
    end if;

    select count(*) into v_bodegas from public.bodegas where empresa_id = v_empresa;
    if v_bodegas <> 1 then
        raise exception 'registrar_empresa creó % bodegas, se esperaba 1', v_bodegas;
    end if;
end $$;

select 'OK usuarios_insert_cross_tenant' as resultado;
rollback;
