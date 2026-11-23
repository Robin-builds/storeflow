-- ============================================================================
-- Fixtures de Planes & Entitlements. Se incluye con:  \ir helpers/planes_fixtures.sql
-- Vive en una subcarpeta para que el glob de pre-push no lo ejecute como test.
-- Las funciones son temporales (pg_temp): desaparecen al cerrar la conexión.
-- Usar dentro de una transacción que termine en ROLLBACK.
-- ============================================================================

-- Crea una empresa con N bodegas y M usuarios (el primero es ADMIN). Devuelve su id.
create or replace function pg_temp.crear_empresa_prueba(
    p_nombre text,
    p_bodegas integer default 1,
    p_usuarios integer default 1
) returns uuid
language plpgsql
as $$
declare
    v_empresa uuid := gen_random_uuid();
    i integer;
begin
    insert into public.empresas (id, nombre) values (v_empresa, 'TEST ' || p_nombre);

    for i in 1..p_usuarios loop
        insert into public.usuarios (id, empresa_id, nombre, email, rol)
        values (
            gen_random_uuid(),
            v_empresa,
            format('usuario %s', i),
            format('%s-%s@test.invalid', v_empresa, i),
            case when i = 1 then 'ADMIN' else 'OPERADOR' end
        );
    end loop;

    for i in 1..p_bodegas loop
        insert into public.bodegas (id, empresa_id, nombre)
        values (gen_random_uuid(), v_empresa, format('bodega %s', i));
    end loop;

    return v_empresa;
end;
$$;

-- Vence el trial moviendo trial_inicio y trial_fin juntos, para respetar su check.
create or replace function pg_temp.vencer_trial(
    p_empresa uuid,
    p_hace interval default interval '1 second'
) returns void
language plpgsql
as $$
declare
    v_fin timestamptz := now() - p_hace;
begin
    update public.suscripciones
    -- 720 horas exactas: '30 days' varía con el cambio de hora según la zona de la sesión.
    set trial_inicio = v_fin - interval '720 hours',
        trial_fin = v_fin
    where empresa_id = p_empresa;

    if not found then
        raise exception 'La empresa % no tiene suscripción', p_empresa;
    end if;
end;
$$;

-- Actúa como un usuario autenticado, igual que un request REST.
create or replace function pg_temp.actuar_como(p_usuario uuid) returns void
language sql
as $$
    select set_config(
        'request.jwt.claims',
        json_build_object('sub', p_usuario, 'role', 'authenticated')::text,
        true
    );
$$;
