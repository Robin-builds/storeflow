-- ============================================================================
-- Planes & Entitlements, US-3: pre-request de usuarios excedentes (FR-029, R2).
--
-- Simula las variables que PostgREST define en cada request.
-- Todo corre en una transacción que termina en ROLLBACK.
-- Resultado esperado: una fila 'OK planes_bloqueo'.
-- ============================================================================

begin;

insert into public.empresas (id, nombre) values
    ('d1000000-0000-4000-8000-00000000000d', 'TEST bloqueo');

insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('d1000000-0000-4000-8000-0000000000a1', 'd1000000-0000-4000-8000-00000000000d', 'admin', 'bl1@test.invalid', 'ADMIN');
insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('d1000000-0000-4000-8000-0000000000a2', 'd1000000-0000-4000-8000-00000000000d', 'operador', 'bl2@test.invalid', 'OPERADOR');
insert into public.usuarios (id, empresa_id, nombre, email, rol) values
    ('d1000000-0000-4000-8000-0000000000a3', 'd1000000-0000-4000-8000-00000000000d', 'excedente', 'bl3@test.invalid', 'OPERADOR');

insert into public.bodegas (id, empresa_id, nombre) values
    ('d1000000-0000-4000-8000-0000000000b1', 'd1000000-0000-4000-8000-00000000000d', 'bodega');

insert into public.productos (id, empresa_id, bodega_id, nombre) values
    ('d1000000-0000-4000-8000-0000000000c1', 'd1000000-0000-4000-8000-00000000000d', 'd1000000-0000-4000-8000-0000000000b1', 'producto');

update public.suscripciones
set trial_inicio = now() - interval '721 hours', trial_fin = now() - interval '1 hour'
where empresa_id = 'd1000000-0000-4000-8000-00000000000d';

-- Define el request y devuelve el code del rechazo, o null si pasa.
create function pg_temp.request(p_metodo text, p_ruta text, p_prefer text default null) returns text
language plpgsql
as $$
declare
    v_estado text;
    v_mensaje text;
begin
    perform set_config('request.method', p_metodo, true);
    perform set_config('request.path', p_ruta, true);
    perform set_config('request.headers',
        case when p_prefer is null then '{}' else json_build_object('prefer', p_prefer)::text end, true);
    begin
        perform public.verificar_acceso_plan();
    exception when others then
        get stacked diagnostics v_estado = returned_sqlstate, v_mensaje = message_text;
        if v_estado <> 'PGRST' then
            raise exception 'Error inesperado % (%) en % %', v_estado, v_mensaje, p_metodo, p_ruta;
        end if;
        return v_mensaje::jsonb->>'code';
    end;
    return null;
end;
$$;

create function pg_temp.exige(p_obtenido text, p_esperado text, p_caso text) returns void
language plpgsql
as $$
begin
    if p_obtenido is distinct from p_esperado then
        raise exception '%: se esperaba %, se obtuvo %', p_caso, coalesce(p_esperado, 'pasar'), coalesce(p_obtenido, 'pasar');
    end if;
end;
$$;

-- ---------------------------------------------------------------- usuario excedente
set local role authenticated;
select set_config('request.jwt.claims',
    '{"sub":"d1000000-0000-4000-8000-0000000000a3","role":"authenticated"}', true);

select pg_temp.exige(pg_temp.request('GET', '/productos'), 'SFP05', 'GET productos');
select pg_temp.exige(pg_temp.request('HEAD', '/movimientos'), 'SFP05', 'HEAD movimientos');
select pg_temp.exige(pg_temp.request('GET', '/movimientos'), 'SFP05', 'GET movimientos');
select pg_temp.exige(pg_temp.request('PATCH', '/productos'), 'SFP05', 'PATCH productos');
select pg_temp.exige(pg_temp.request('DELETE', '/bodegas'), 'SFP05', 'DELETE bodegas');
select pg_temp.exige(pg_temp.request('POST', '/productos', 'return=minimal'), 'SFP05', 'POST productos');
select pg_temp.exige(pg_temp.request('POST', '/rpc/otra_funcion'), 'SFP05', 'otra RPC');

-- POST permitido solo con return=minimal y sin resolution=
select pg_temp.exige(pg_temp.request('POST', '/movimientos'), 'SFP05', 'POST sin Prefer');
select pg_temp.exige(pg_temp.request('POST', '/movimientos', 'return=representation'), 'SFP05', 'POST con representación');
select pg_temp.exige(pg_temp.request('POST', '/movimientos', 'return=minimal, resolution=merge-duplicates'), 'SFP05', 'POST con resolution');
select pg_temp.exige(pg_temp.request('POST', '/lotes', 'resolution=ignore-duplicates,return=minimal'), 'SFP05', 'POST lotes con resolution');
select pg_temp.exige(pg_temp.request('PATCH', '/movimientos', 'return=minimal'), 'SFP05', 'PATCH con minimal');

select pg_temp.exige(pg_temp.request('POST', '/movimientos', 'return=minimal'), null, 'POST movimientos');
select pg_temp.exige(pg_temp.request('POST', '/lotes', 'return=minimal'), null, 'POST lotes');
select pg_temp.exige(pg_temp.request('POST', '/rpc/obtener_entitlement'), null, 'obtener_entitlement');
select pg_temp.exige(pg_temp.request('POST', '/rpc/reportar_retenidos'), null, 'reportar_retenidos');

-- Movimiento del excedente: dentro de la ventana entra y queda marcado; fuera se rechaza
do $$
declare
    n bigint;
    v_estado text;
begin
    insert into public.movimientos (id, producto_id, tipo, cantidad, nota, created_at) values
        ('d1000000-0000-4000-8000-0000000000f1', 'd1000000-0000-4000-8000-0000000000c1', 'ENTRADA', 1, 'ventana', now() - interval '30 minutes');
    select count(*) into n from public.movimientos_fuera_plan
    where movimiento_id = 'd1000000-0000-4000-8000-0000000000f1';
    if n <> 1 then raise exception 'FR-029: el movimiento no quedó marcado'; end if;

    begin
        insert into public.movimientos (producto_id, tipo, cantidad, nota, created_at) values
            ('d1000000-0000-4000-8000-0000000000c1', 'ENTRADA', 1, 'fuera', now() + interval '8 days');
        raise exception 'FR-029: se aceptó un movimiento fuera de la ventana' using errcode = 'XX000';
    exception when sqlstate 'SFP05' then null;
    end;
end $$;

-- ---------------------------------------------------------------- usuario activo
select set_config('request.jwt.claims',
    '{"sub":"d1000000-0000-4000-8000-0000000000a2","role":"authenticated"}', true);

select pg_temp.exige(pg_temp.request('GET', '/productos'), null, 'activo GET');
select pg_temp.exige(pg_temp.request('PATCH', '/productos'), null, 'activo PATCH');

-- ---------------------------------------------------------------- service role y sin JWT
reset role;
set local role service_role;
select set_config('request.jwt.claims', '{"role":"service_role"}', true);
select pg_temp.exige(pg_temp.request('GET', '/productos'), null, 'service role');

reset role;
set local role anon;
select set_config('request.jwt.claims', '', true);
select pg_temp.exige(pg_temp.request('GET', '/productos'), null, 'sin JWT');

reset role;
select 'OK planes_bloqueo' as resultado;
rollback;
