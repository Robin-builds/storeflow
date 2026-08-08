# Migración de proyecto Supabase (cambio de cuenta/organización)

Registro del proceso usado para migrar StockFlow de un proyecto Supabase a otro
en una organización distinta. Sirve como guía reutilizable si hay que repetirlo.

## Caso realizado (referencia histórica)

- **Origen**: proyecto "StockFlow" (`eygbgykglovbivthyqfb`), organización `qmvlkqgapwsybqeowicc`
- **Destino**: proyecto `quvkxpjstzssivsaqimu`, organización nueva
- **Fecha**: 2026-08-08
- **Resultado**: 10 tablas, RLS, políticas, 3 funciones RPC y ~20,111 filas migradas sin pérdida. Edge function desplegada manualmente (ver limitación de MCP más abajo). **`auth.users`/`auth.identities` se migraron después, vía `postgres_fdw`** (ver sección 7) — validado que funciona, incluyendo el hash de contraseña (`encrypted_password`). Solo la primera cuenta (`sara@cym.cl`, que ya había creado una cuenta nueva antes de migrar auth) necesitó el fix manual de la sección 7.b; el resto de usuarios entra directo con su email/contraseña original.

## Contexto: dos conectores MCP de Supabase distintos

Este repo puede tener acceso simultáneo a **dos** MCP servers de Supabase que apuntan a
cuentas/organizaciones diferentes:

1. `claude_ai_Supabase` (herramienta nativa de Claude) — da acceso a todos los proyectos
   de la cuenta ya vinculada a Claude, tomando `project_id` como parámetro. Trae permisos
   de lectura **y escritura** (`apply_migration`, `execute_sql`, `deploy_edge_function`, etc.)
2. `supabase` (definido en `.mcp.json`, vía `https://mcp.supabase.com/mcp?project_ref=...`) —
   apunta a un proyecto específico fijado en la URL, y puede quedar en modo
   `read_only=true`. **Este flag se configura en `.mcp.json` y solo se aplica al
   reconectar el servidor MCP (reinicio de Claude Code)**, no en caliente.

Si el proyecto destino es de una cuenta/organización distinta a la que Claude ya tiene
vinculada, casi seguro llega por el conector `supabase` (`.mcp.json`), no por
`claude_ai_Supabase`.

### Limitación encontrada: no se pudo reautorizar sin reiniciar

Se quitó `read_only=true` de `.mcp.json`, pero el usuario no podía reiniciar Claude Code
(riesgo de perder contexto de la sesión). Como el cambio de `.mcp.json` no se aplica sin
reconexión, **el conector del proyecto nuevo quedó en solo lectura toda la migración**.
Esto bloqueó `apply_migration`, `execute_sql` (escritura) y `deploy_edge_function` contra
el proyecto nuevo. Se resolvió así:

- **Esquema**: el usuario lo corrió manualmente en el SQL Editor del dashboard del
  proyecto nuevo (ver script abajo).
- **Datos**: se copiaron directo entre bases con `postgres_fdw`, ejecutado desde el
  proyecto **origen** (que sí tenía escritura vía `claude_ai_Supabase`), conectándose
  como foreign server al proyecto destino. Esto no depende del modo read-only del
  conector destino.
- **Edge Functions**: no hay forma de sortear el read-only para esto. Requiere Supabase
  CLI corrida por el usuario, o resolver el reinicio del MCP.

## Paso a paso

### 1. Inventariar el proyecto origen

Con el conector de escritura (`claude_ai_Supabase`), extraer:

```sql
-- Columnas, nullability, defaults
select table_name, column_name, is_nullable, column_default, data_type
from information_schema.columns where table_schema = 'public'
order by table_name, ordinal_position;

-- Foreign keys
select tc.table_name, kcu.column_name, ccu.table_name as foreign_table,
       ccu.column_name as foreign_column, rc.update_rule, rc.delete_rule
from information_schema.table_constraints tc
join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema
join information_schema.constraint_column_usage ccu on tc.constraint_name = ccu.constraint_name
join information_schema.referential_constraints rc on tc.constraint_name = rc.constraint_name
where tc.constraint_type = 'FOREIGN KEY' and tc.table_schema = 'public';

-- Índices
select indexname, indexdef from pg_indexes where schemaname = 'public';

-- RLS policies
select policyname, tablename, cmd, permissive, roles::text, qual, with_check
from pg_policies where schemaname = 'public';

-- Funciones/RPCs
select p.proname, pg_get_functiondef(p.oid)
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public';

-- Triggers y event triggers
select trigger_name, event_manipulation, event_object_table, action_statement
from information_schema.triggers where trigger_schema = 'public';
select evtname, p.proname from pg_event_trigger et join pg_proc p on p.oid = et.evtfoid;
```

También revisar `list_edge_functions` + `get_edge_function` para funciones desplegadas
(en este repo ya están versionadas en `supabase/functions/`).

### 2. Generar el script de esquema

Armar un `.sql` con: `CREATE TABLE` (en orden de dependencia de FKs), índices,
`ALTER TABLE ... ENABLE ROW LEVEL SECURITY`, políticas, funciones RPC. Ejemplo real:
`supabase/migrations/20260808000000_migracion_completa_organizacion_nueva.sql`.

No hace falta recrear el event trigger `ensure_rls` / `rls_auto_enable` — es un default
de organización que ya viene en proyectos nuevos de Supabase.

### 3. Aplicar el esquema en destino

- Si el conector MCP del destino tiene escritura: `apply_migration`.
- Si está en solo lectura (caso típico al cambiar de cuenta): el usuario pega y corre el
  `.sql` en el SQL Editor del dashboard del proyecto nuevo.

Verificar con `list_tables` (funciona igual en modo lectura) que las tablas quedaron
creadas con 0 filas antes de seguir.

### 4. Migrar datos con `postgres_fdw` (sin pasar los datos por el chat)

Ejecutado **desde el proyecto origen** (necesita su propia contraseña de base de datos,
la del proyecto **destino**, obtenida de Dashboard → Project Settings → Database):

```sql
create extension if not exists postgres_fdw;

create server fdw_destino
  foreign data wrapper postgres_fdw
  options (host 'db.<ref-destino>.supabase.co', port '5432', dbname 'postgres', sslmode 'require');

create user mapping for current_user
  server fdw_destino
  options (user 'postgres', password '<password-destino>');

create schema fdw_destino_schema;

import foreign schema public
  limit to (tabla1, tabla2, ...)
  from server fdw_destino into fdw_destino_schema;
```

Luego, **en orden de dependencia de FKs** (padres antes que hijos):

```sql
insert into fdw_destino_schema.<tabla> select * from public.<tabla>;
```

**Importante — tablas grandes (miles de filas) truncan la conexión.** No mandar todos
los `INSERT` de golpe en una sola query (un timeout revierte toda la transacción y no
se sabe qué quedó aplicado). Usar lotes de ~1300 filas con `ORDER BY <pk> LIMIT n OFFSET m`,
verificando el conteo con `list_tables` (columna `rows`) entre lotes. Fue el tamaño que
funcionó de forma estable para tablas de ~10k filas en este caso.

Al terminar, limpiar la conexión temporal (la contraseña no debe quedar guardada):

```sql
drop schema fdw_destino_schema cascade;
drop user mapping for current_user server fdw_destino;
drop server fdw_destino;
```

Verificar conteos finales con `list_tables` contra el proyecto destino y compararlos
con los del proyecto origen.

### 5. Redesplegar Edge Functions

Si el MCP del destino no tiene escritura, el usuario debe correr, con Supabase CLI:

```
supabase functions deploy <nombre-funcion> --project-ref <ref-destino>
```

**Alternativa sin CLI — Dashboard → Edge Functions → "Deploy a new function":**
el campo que pide el nombre **al crearla** es el `slug` real (define la URL
`/functions/v1/<slug>`), y **no se puede cambiar después**. Si se despliega con el
nombre por defecto (ej. `clever-action`) y luego se edita el campo "Name" desde la
función ya creada, **solo cambia el label visual, no el slug** — la app seguirá
recibiendo 404. Verificar con `list_edge_functions` que el campo `slug` (no `name`)
coincide exactamente con lo que espera el cliente (buscar la URL invocada, ej.
`grep -rn "functions/v1/<nombre>"` en el código). Si el slug quedó mal, no hay
edición posible: borrar la función y crearla de nuevo con el slug correcto desde el
primer paso.

### 6. Actualizar la app

Cambiar `SUPABASE_URL` y `SUPABASE_ANON_KEY` en
`app/src/main/java/cl/storeflow/warehouse/data/remote/SupabaseClient.kt` recién cuando
esquema + datos + edge functions estén confirmados en el proyecto nuevo. La anon key se
obtiene con `get_publishable_keys` (o Dashboard → Settings → API).

### 7. `auth.users` / `auth.identities` — hay que migrarlos aparte, con su propio `postgres_fdw`

`import foreign schema public` (paso 4) solo trae las tablas del schema `public`. Las
cuentas reales de Supabase Auth viven en `auth.users` / `auth.identities`, en el schema
`auth` — **si solo se hace el paso 4, no quedan migradas**. Efecto práctico observado
la primera vez (antes de hacer este paso):

- Un usuario que existía en el proyecto viejo (ej. `sara@cym.cl`, con fila en
  `public.usuarios` ya migrada, ligada a su `empresa_id`) intenta iniciar sesión en la
  app apuntando al proyecto nuevo → falla, porque `auth.users` está vacío ahí.
- Si en cambio usa "Crear cuenta" con el mismo email, Supabase Auth genera un
  **`auth.users.id` nuevo** (distinto al `id` que tenía en `public.usuarios`) y el flujo
  de registro (`registrar_empresa` RPC) crea una **empresa nueva vacía** — el usuario
  entra a la app pero no ve ninguno de sus datos migrados (0 productos).
- La fila vieja en `public.usuarios` (con el `id` viejo) queda huérfana: ya no
  corresponde a ninguna cuenta de Auth real.

**7.a — Fix a mano para cuentas que ya se registraron desde cero antes de migrar auth
("re-parentar" la cuenta nueva a la empresa vieja):**

```sql
-- 1. Verificar que no haya movimientos.usuario_id apuntando al id huérfano
--    (si los hay, hay que decidir si repuntarlos al id nuevo o dejar que
--    ON DELETE SET NULL los limpie)
select count(*) from public.movimientos where usuario_id = '<id-huerfano>';

-- 2. Mover la cuenta de Auth recién creada a la empresa real (con los datos)
update public.usuarios
  set empresa_id = '<empresa-real-con-datos>'
  where id = '<id-nuevo-de-auth-uid>';

-- 3. Borrar la fila huérfana duplicada
delete from public.usuarios where id = '<id-huerfano>';

-- 4. Borrar la empresa vacía que se creó de más (cascada borra su bodega auto-creada)
delete from public.empresas where id = '<empresa-vacia-nueva>';
```

Solo hace falta para las cuentas que ya alcanzaron a "crear cuenta desde cero" **antes**
de aplicar el paso 7.b de abajo. Si se migra `auth.users`/`auth.identities` primero (antes
de que nadie inicie sesión en el proyecto nuevo), este parche no hace falta para nadie.

**Nota — MCP en modo lectura bloquea hasta lecturas simples de escritura vía
`execute_sql`:** si el conector del destino sigue en `read_only` (ver limitación de
arriba), estos `UPDATE`/`DELETE` tampoco se pueden correr directo contra el proyecto
destino. Se resuelve reabriendo temporalmente la misma conexión `postgres_fdw` del
paso 4 (o dejándola sin borrar hasta terminar todos los arreglos post-migración) y
corriendo los `UPDATE`/`DELETE` sobre `fdw_destino_schema.<tabla>` en vez de
`public.<tabla>`.

### 7.b Migrar `auth.users` + `auth.identities` con `postgres_fdw` (validado, funciona)

En vez de resolver cuenta por cuenta a mano (7.a), migrar la tabla de Auth completa con
la misma técnica de `postgres_fdw`, preservando `id`, email y **hash de contraseña**
(`encrypted_password`) — las cuentas viejas quedan funcionando en el proyecto nuevo con
las mismas credenciales, sin pasar por "crear cuenta desde cero". **Hacer esto antes de
que cualquier usuario viejo inicie sesión en el proyecto nuevo** para no necesitar 7.a.

Dos columnas son generadas (`GENERATED ALWAYS`) y no se pueden incluir en el `INSERT`:
`auth.users.confirmed_at` y `auth.identities.email`. Hay que listar columnas explícitas
en vez de `select *`:

```sql
import foreign schema auth
  limit to (users, identities)
  from server fdw_destino into fdw_destino_schema;

insert into fdw_destino_schema.users
  (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, invited_at,
   confirmation_token, confirmation_sent_at, recovery_token, recovery_sent_at,
   email_change_token_new, email_change, email_change_sent_at, last_sign_in_at,
   raw_app_meta_data, raw_user_meta_data, is_super_admin, created_at, updated_at,
   phone, phone_confirmed_at, phone_change, phone_change_token, phone_change_sent_at,
   email_change_token_current, email_change_confirm_status, banned_until,
   reauthentication_token, reauthentication_sent_at, is_sso_user, deleted_at, is_anonymous)
select
   instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, invited_at,
   confirmation_token, confirmation_sent_at, recovery_token, recovery_sent_at,
   email_change_token_new, email_change, email_change_sent_at, last_sign_in_at,
   raw_app_meta_data, raw_user_meta_data, is_super_admin, created_at, updated_at,
   phone, phone_confirmed_at, phone_change, phone_change_token, phone_change_sent_at,
   email_change_token_current, email_change_confirm_status, banned_until,
   reauthentication_token, reauthentication_sent_at, is_sso_user, deleted_at, is_anonymous
from auth.users
where id not in (/* ids que ya se hayan resuelto a mano con 7.a, si los hay */);

insert into fdw_destino_schema.identities
  (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at, id)
select
  provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at, id
from auth.identities
where user_id not in (/* mismos ids excluidos arriba */);
```

Si alguna cuenta ya pasó por el parche 7.a antes de correr esto, **excluir su `id` del
`WHERE`** (colisiona con la unique constraint de `email` en `auth.users`, porque esa
cuenta ya existe en el destino con un `id` distinto). Verificar el resultado:

```sql
select u.id, u.email, count(i.id) as identidades
from auth.users u left join auth.identities i on i.user_id = u.id
group by u.id, u.email order by u.email;
```

Cada usuario debe quedar con exactamente 1 identidad. Probado en este caso con 7 de 8
usuarios (el octavo, resuelto antes con 7.a) — inicio de sesión funcionando con el
email/contraseña original tal cual estaban en el proyecto viejo.

### 8. Revisar advisors

Correr `get_advisors(type: security)` contra el proyecto nuevo. Los WARN sobre
`search_path` mutable y funciones `SECURITY DEFINER` ejecutables por `anon`/`authenticated`
ya existían en el proyecto origen (se migran junto con las funciones) — no son
regresiones nuevas, pero quedan como mejora pendiente si se quieren endurecer.
