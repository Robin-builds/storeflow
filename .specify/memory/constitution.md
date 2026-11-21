<!--
Sync Impact Report
- Versión: 1.1.0 → 1.2.0 (MINOR: nueva sección "Alcance de los cambios: solo lo solicitado")
- Principios creados: I Stock derivado, II Historial inmutable, III Aislamiento entre empresas,
  IV Montos en CLP enteros, V Identidad global de registros
- Secciones agregadas: Convenciones, Puertas de calidad, Decisiones fuera de la constitución,
  Deuda de cumplimiento, Propiedad de specs, Gobernanza
- Plantillas revisadas: plan-template.md (usa "Constitution Check" → leer la tabla de
  enforcement de esta constitución); spec-template.md y tasks-template.md sin cambios
- Pendientes: todo lo listado en "Deuda de cumplimiento" (D1–D9)
-->

# Constitución — StoreFlow (Android)

**Versión**: 1.2.0 | **Ratificada**: 2026-11-20 | **Última enmienda**: 2026-11-20

## Fallo capital

**Que el stock que ve una empresa no cuadre con lo que realmente pasó en su bodega, o que
una empresa vea o altere datos de otra.** Todo lo demás es negociable; esto no. Los
principios I, II y III existen para que ese fallo sea imposible por construcción, no por
cuidado.

## Niveles de enforcement

| Nivel | Mecanismo | Garantía |
|---|---|---|
| 1 | Prosa / revisión humana | Se cumple si alguien se acuerda |
| 2 | Validación en la app | Sorteable (REST directo, otro cliente) |
| 3 | Mecánico: constraint/trigger/RLS en Postgres, test que corre en el hook `pre-push`, hook `commit-msg` | Imposible de violar sin desactivarlo explícitamente |

> **Alcance del nivel 3 en este proyecto:** no hay CI (decisión 2026-11-20). El nivel 3 de
> la app se apoya en git hooks versionados en `.githooks/` (`git config core.hooksPath
> .githooks`). Un hook se puede saltar con `--no-verify`: hacerlo es una violación de esta
> constitución, no un atajo.

## Principios

| # | Principio | Tipo | Cumple hoy | Enforcement (objetivo) | Nivel hoy → objetivo |
|---|---|---|---|---|---|
| I | **Stock siempre derivado** — **NO NEGOCIABLE** | Negocio | ✅ Sí | Postgres sin columna de stock + test de esquema Room en `pre-push` | 1 → 3 |
| II | **Historial de movimientos inmutable** — **NO NEGOCIABLE** | Negocio | ⚠️ Solo en la app | Trigger Postgres + test de `MovimientoDao` en `pre-push` | 2 → 3 |
| III | **Aislamiento entre empresas** — **NO NEGOCIABLE** | Negocio | ⚠️ RLS sí, con un agujero | RLS en todas las tablas + test de aislamiento A↔B | 3 parcial → 3 |
| IV | **Montos en pesos chilenos enteros** | Negocio | ✅ Sí | Tipo `integer` en Postgres + test de esquema Room | 3 (servidor) → 3 |
| V | **Identidad global de registros** | Negocio | ✅ Sí | Tipo `uuid` en Postgres + test de esquema Room | 3 (servidor) → 3 |

### I. Stock siempre derivado — NO NEGOCIABLE

El stock de un producto (y de un lote) es **siempre** la suma de las cantidades de sus
movimientos. No existe un número de stock guardado que pueda divergir del historial.

- **Evidencia de cumplimiento:** `ProductoEntity.kt` no tiene campo de stock (comentario
  explícito en la entidad). Postgres no tiene columna de stock en ninguna tabla. Las vistas
  `productos_con_stock` y `bodegas_resumen` lo calculan con `SUM(movimientos.cantidad)`
  y tienen `security_invoker=true` (respetan RLS). En Room lo calculan `ProductoDao` y
  `LoteDao.obtenerConStockFefo`.
- **Enforcement:**
  - Servidor: no hay columna de stock en ninguna tabla. Agregarla exige una migración, que
    debe enmendar esta constitución (MAJOR).
  - App: `EsquemaInvariantesTest` lee el último `app/schemas/.../<versión>.json` y falla si
    alguna tabla tiene una columna cuyo nombre contenga `stock`, salvo `stock_minimo`.
    Corre en `pre-push`. **[Deuda D5]**
- **Prueba de violación:** agregar `val stock: Int` a `ProductoEntity` → el test falla.

### II. Historial de movimientos inmutable — NO NEGOCIABLE

Un movimiento registrado (entrada, salida, ajuste) **no se edita ni se borra**. Un error
se corrige con un movimiento de AJUSTE nuevo. Campos de negocio protegidos: `producto_id`,
`tipo`, `cantidad`, `nota`, `lote_id`, `usuario_id`, `created_at`. Los campos de
sincronización (`synced`, `synced_at`) sí pueden cambiar.

- **Evidencia de cumplimiento:**
  - App ✅: `MovimientoDao.insertar` usa `ABORT`. El único `UPDATE` (`marcarSincronizado`)
    toca solo `synced`/`synced_at`. `SyncPayloads` solo encola `INSERT` para movimientos.
    `upsertAll` (REPLACE) solo reescribe la fila con la copia del servidor del mismo
    movimiento.
  - Servidor ❌: la política `movimientos_empresa` es `FOR ALL` y no hay triggers. Cualquier
    usuario de la empresa puede hacer `PATCH`/`DELETE` vía REST. **[Deuda D2]**
- **Enforcement:**
  - Servidor: trigger `BEFORE UPDATE OR DELETE` en `movimientos` que lanza excepción si
    cambia un campo de negocio o si el borrado no viene de un `CASCADE` (ver D2 y D3).
  - App: `MovimientoDaoInmutableTest` inspecciona `MovimientoDao` por reflexión y falla si
    hay un `@Update`/`@Delete`, o un `@Query` con `UPDATE`/`DELETE` sobre `movimientos` que
    toque columnas fuera de `synced`/`synced_at`. Corre en `pre-push`. **[Deuda D5]**
- **Prueba de violación:** `PATCH /rest/v1/movimientos?id=eq.X {"cantidad":999}` con un JWT
  válido → hoy pasa; con el trigger debe responder error.

### III. Aislamiento entre empresas — NO NEGOCIABLE

Un usuario solo puede leer y escribir datos de **su** empresa. La seguridad depende
**exclusivamente** del servidor (RLS). Ninguna consulta remota se considera segura por
filtrar `empresa_id` en el cliente. Filtrar por `empresa_id` en Room está permitido: Room
contiene datos de una sola empresa y se vacía en el logout.

- **Mecanismo real:** `get_empresa_id()` (`SECURITY DEFINER`) obtiene la empresa desde la
  membresía en `public.usuarios` (`id = auth.uid()`). **No** se usa el claim
  `app_metadata.empresa_id` del JWT.
- **Evidencia de cumplimiento:**
  - ✅ RLS activo en las 10 tablas. Las 9 tablas de negocio filtran por `get_empresa_id()`
    (directo o vía `productos`). `sync_queue` no tiene políticas, así que nadie accede.
  - ✅ Las vistas usan `security_invoker=true`.
  - ❌ `unirse_a_empresa(p_empresa_id, …)` es `SECURITY DEFINER`, ejecutable por cualquier
    usuario autenticado, y lo asigna a **cualquier** empresa cuyo UUID conozca. **[Deuda D1]**
- **Enforcement:**
  - Servidor: RLS (nivel 3). El event trigger `ensure_rls` de la organización activa RLS en
    tablas nuevas.
  - Verificación: test pgTAP en `supabase/tests/aislamiento_empresas.sql`. Crea empresas A
    y B y, como usuario de A, intenta `SELECT/INSERT/UPDATE/DELETE` sobre cada tabla y vista
    de B **sin filtro de cliente**. Espera 0 filas o error. También intenta
    `unirse_a_empresa(B)` y espera error. Se ejecuta con `supabase test db`, y el
    `pre-push` lo corre cuando el push toca `supabase/`. **[Deuda D4]**
- **Prueba de violación:** borrar o aflojar cualquier política → el test pgTAP falla.

### IV. Montos en pesos chilenos enteros

Los precios y montos se expresan en CLP **sin decimales**, en toda la cadena (UI → dominio →
Room → Postgres).

- **Evidencia:** `ProductoEntity.precio: Int`. Postgres `productos.precio integer not null`.
  Migración Room 3→4 (`REAL` → `INTEGER`).
- **Enforcement:** tipo `integer` en Postgres (nivel 3). `EsquemaInvariantesTest` verifica
  que `productos.precio` sea `INTEGER` en el esquema Room. **[Deuda D5]**

### V. Identidad global de registros

Todo registro se puede crear offline con un identificador único global, sin coordinarse
con el servidor. Las PK son UUID generados en el cliente. Única excepción:
`auth_sessions` (fila única local, `id = 1`, nunca se sincroniza).

- **Evidencia:** `UUID.randomUUID().toString()` en todas las entidades sincronizables. Todas
  las PK de Postgres son `uuid`. `producto_atributos` tiene PK compuesta de dos UUID.
- **Enforcement:** tipo `uuid` en Postgres (nivel 3: rechaza IDs no UUID).
  `EsquemaInvariantesTest` verifica que toda PK del esquema Room sea `TEXT`, salvo
  `auth_sessions`. **[Deuda D5]**

## Convenciones (no son principios: no tienen un test que falle al violarlas)

| Convención | Enforcement |
|---|---|
| Variables y comentarios en español | Revisión (nivel 1) |
| Textos UI en español neutro LatAm, sin voseo ni rioplatensismos | Revisión (nivel 1) |
| Commits semánticos en inglés: `feat|fix|refactor|docs|test|chore|perf|build|ci|style|revert|merge` | Hook `commit-msg` (nivel 3 local). **[Deuda D5]** |
| Logs con Timber, no `Log.d` | Revisión (nivel 1) |
| Sin capa UseCase; la lógica de negocio vive en Repositories | Revisión (nivel 1) |
| `*Entity` no sale de `data/` | Revisión (nivel 1) |
| Tablas SQL en plural y minúsculas | Revisión (nivel 1) |
| Cada feature en su propia rama | Revisión (nivel 1) |

## Ambientes: Sandbox → Producción — **NO NEGOCIABLE**

- **Sandbox:** proyecto Supabase `storeflow-sandbox` (`ihmseolpajzqnddldffc`), separado. Replica el **esquema** de producción (tablas,
  RLS, funciones, triggers, vistas y Edge Functions), reconstruido **solo** desde
  `supabase/migrations/`. Usa datos de prueba sintéticos: los datos reales de clientes
  nunca se copian a Sandbox.
- **Producción:** `quvkxpjstzssivsaqimu`. Único proyecto productivo.
- **Regla:** todo cambio de base de datos (migración, RLS, función, trigger, vista) y toda
  Edge Function se aplica **primero en Sandbox**. Ahí pasan los tests de `supabase/tests/`
  y la validación en dispositivo con un build de la app apuntando a Sandbox. **Recién
  después** se aplica la **misma** migración, sin editar, en Producción. Nunca se aplica
  un cambio directo a Producción.
- **Deriva:** Sandbox y Producción deben tener el mismo historial de migraciones
  (`list_migrations`). Si difieren, no se aplica nada nuevo en Producción hasta igualarlas.
- **Enforcement:**
  - `scripts/promover-migracion.sh <archivo>`: solo aplica en Producción una migración
    ya registrada en Sandbox, con historial previo idéntico y tests en verde. La aplica
    sin editar y la registra con la misma versión. Requiere `psql` y las variables
    `STOREFLOW_SANDBOX_DB_URL` y `STOREFLOW_PROD_DB_URL`.
  - Cuando la promoción la hace un agente vía MCP, sigue los mismos 6 pasos del script y
    pide confirmación explícita antes del paso 5.
  - App: el build `sandbox` (`gradlew installSandbox`) instala "StoreFlow Sandbox"
    (`cl.storeflow.app.sandbox`), que apunta solo al proyecto Sandbox.

## Puertas de calidad previas al merge a `main`

0. **Si el cambio toca `supabase/`:** aplicado y validado en Sandbox antes que en
   Producción (ver "Ambientes").
1. **Tests unitarios en verde.** Hook `pre-push` → `gradlew testDebugUnitTest`. Estado al
   2026-11-20: verde. **[Deuda D5]**
2. **Si el cambio toca `supabase/`:** `supabase test db` en verde (incluye el test de
   aislamiento). **[Deuda D4, D5]**
3. **Validación en dispositivo físico** de la funcionalidad cambiada, confirmada por el
   dueño del proyecto. **"BUILD SUCCESSFUL" no es criterio de aceptación.** Es nivel 1 (no
   automatizable) y por eso es puerta de proceso, no principio.
4. **Cambio de esquema Room:** migración explícita `MIGRATION_N_N+1` + esquema exportado en
   `app/schemas/` + prueba de upgrade real (no reinstalación limpia) en dispositivo.

## Decisiones conscientes fuera de la constitución

- **Resolución de conflictos de sincronización.** Hoy gana el último en llegar al servidor
  (POST/PATCH/DELETE simples, sin comparar `updated_at` ni `device_id`). Se acepta así: la
  probabilidad de que dos usuarios editen el mismo producto en el mismo instante es
  prácticamente nula, y los movimientos, que determinan el stock, nunca chocan (solo
  inserción con UUID propio). No es principio ni deuda. Si cambia el perfil de uso, se
  reevalúa con una enmienda MINOR. (Decisión del 2026-11-20.)
- **Roles (ADMIN/OPERADOR) solo en la app, no en RLS.** Un OPERADOR podría hacer por REST lo
  que la UI le oculta, siempre dentro de su propia empresa. Es un riesgo aceptado: no
  rompe el principio III.

## Deuda de cumplimiento

| ID | Prioridad | Principio | Violación / hueco | Fix propuesto |
|---|---|---|---|---|
| D1 | ✅ Cerrada 2026-11-20 | III | `unirse_a_empresa` permitía a cualquier usuario autenticado unirse a cualquier empresa conociendo su UUID. | Función eliminada: `20261121004323_eliminar_unirse_a_empresa.sql`. Verificado con `aislamiento_empresas.sql`. ⚠️ Se aplicó directo en Producción, antes de existir Sandbox (excepción histórica). |
| D2 | ✅ Cerrada 2026-11-20 (pendiente validación en dispositivo) | II | Postgres permitía `UPDATE`/`DELETE` de movimientos. | Trigger `movimientos_inmutables`: `20261121004341_movimientos_inmutables.sql`. Verificado con `movimientos_inmutables.sql`. ⚠️ Misma excepción histórica que D1. |
| D3 | Media — **decidido: borrado lógico** | II | Borrar un producto borra en cascada su historial de movimientos (Room y Postgres). | `productos.eliminado_at` en Postgres y Room (migración Room v10). `eliminar`/`eliminarVarios` pasan a `UPDATE` vía sync. Las consultas de app y web excluyen los productos eliminados. Al terminar, quitar la excepción de cascada del trigger D2. Feature COMPLEJA: requiere `docs/plans/` y pasar por Sandbox. |
| D4 | Media | III | Los tests SQL existen y pasan (`supabase/tests/`), pero no corren solos. | Ejecutarlos automáticamente contra Sandbox desde el `pre-push` o `scripts/promover-migracion` (depende de D10). |
| D5 | ✅ Cerrada 2026-11-20 | I, II, IV, V, convenciones | — | `EsquemaInvariantesTest` y `MovimientoDaoInmutableTest` (6 tests; verificado que fallan ante violaciones inyectadas). `.githooks/commit-msg` y `.githooks/pre-push` (tests unitarios; si el push cambia `supabase/`, tests SQL contra Sandbox vía `STOREFLOW_SANDBOX_DB_URL`, o `SANDBOX_OK=1` tras correrlos a mano). Activación documentada en el README. |
| D10 | Media — casi cerrada | Ambientes | Sandbox creado el 2026-11-20: reconstruido solo desde `supabase/migrations/` (348 objetos idénticos a Producción), tests SQL en verde, 2 Edge Functions desplegadas, build `sandbox` de la app, script de promoción. | **Falta:** (1) desactivar "Confirm email" en Sandbox, como en Producción; (2) instalar `psql` y probar el script de punta a punta; (3) borrar la Edge Function duplicada `clever-action` de Producción (panel → Edge Functions → clever-action → Delete; el MCP no permite borrar funciones). ✅ Historial de migraciones Sandbox = Producción desde el 2026-11-20. |
| D6 | ✅ Cerrada 2026-11-20 (salvo el registro de historial, en D10) | Ambientes / III | Faltaban en el repo las vistas, el event trigger `ensure_rls` y la migración `sync_queue_deny_all` del 03/11. | Agregadas `20260823000000_vistas_stock_y_rls_auto.sql` y `20261103002657_sync_queue_deny_all.sql` (definiciones tomadas del catálogo en vivo; tablas, columnas, constraints e índices verificados idénticos). **Falta:** probar la reconstrucción completa en Sandbox (D10) y registrar en el historial de Producción las migraciones 20260808 y 20260823 (aplicadas a mano, no figuran en `list_migrations`). |
| D7 | Baja | III | La tabla remota `sync_queue` no tiene uso (la cola vive solo en Room). | Eliminarla en la migración de D6, o documentar por qué existe. |
| D8 | ✅ Cerrada 2026-11-20 | — | `.harness/CLAUDE.md` describía el multi-tenant vía JWT y la regla "no filtrar `empresa_id`". | Actualizado: multi-tenant vía `usuarios`, regla reformulada según el principio III, conteo de tests, sección Constitución y Ambientes. |
| D9 | ✅ No aplicaba | — | `ExampleInstrumentedTest` ya estaba corregido (`1c11811`); el Paso 0 se basó en un `ESTADO.md` desactualizado. | Ahora compara contra `BuildConfig.APPLICATION_ID`, así funciona también con el build `sandbox`. |

## Propiedad de specs

Proyecto de un solo desarrollador: **Robin** es dueño de todas las specs en `specs/`. No se
usa `specs/MAPA.md`. Si entra otra persona o se usan agentes en paralelo sobre features
distintas, se crea `specs/MAPA.md` con un propietario por spec (enmienda MINOR).

Toda spec que toque `movimientos`, el cálculo de stock, RLS, funciones `SECURITY DEFINER`
o el esquema de Room debe citar el principio que afecta (I–V) en su sección de requisitos.

## Alcance de los cambios: solo lo solicitado — **NO NEGOCIABLE**

**Ningún cambio se aplica sin que el dueño del proyecto lo haya solicitado explícitamente.**
Vale para personas y agentes de IA, y para código, base de datos (Sandbox y Producción),
configuración, documentación, dependencias y servicios externos.

- Una solicitud cubre **solo** lo que nombra. Pedir un análisis o un documento (por
  ejemplo, esta constitución) no autoriza a implementar lo que ese documento propone.
- Un hallazgo (bug, deuda, mejora) se **reporta**, no se corrige. Se corrige solo cuando se
  solicita.
- Un "sí" a una propuesta cubre **esa** propuesta, no las siguientes.
- No se cierra una entrega con preguntas que propongan trabajo nuevo ("¿sigo con…?",
  "¿arranco…?"). Si hay pasos pendientes, se informan; el dueño decide si los pide.
- **Criterio de verificación:** cada cambio (commit, migración, despliegue, cambio de
  configuración) debe poder vincularse a una solicitud explícita del dueño. Un cambio sin
  esa solicitud es una violación y se revierte si el dueño lo pide.
- **Enforcement:** nivel 1 (proceso, revisión del dueño). No existe un mecanismo
  automático que distinga un cambio solicitado de uno no solicitado. Los cambios en la base
  de Producción tienen además la barrera de la sección Ambientes.

## Gobernanza

- Esta constitución prevalece sobre `.harness/CLAUDE.md` y cualquier otra guía si hay
  conflicto.
- Cada `plan.md` (`/speckit-plan`) completa su "Constitution Check" contra la tabla de
  principios. Un plan que viola un principio NO NEGOCIABLE no avanza, salvo que incluya una
  enmienda aprobada.
- **Versionado SemVer:**
  - **MAJOR:** se quita o cambia el significado de un principio (rompe specs que lo citan).
  - **MINOR:** se agrega un principio, una puerta de calidad o una sección.
  - **PATCH:** aclaraciones, evidencia actualizada, deuda cerrada.
- Cerrar una deuda (D1–D9) actualiza la columna "Nivel hoy" y sube PATCH.

### Historial

| Versión | Fecha | Cambio |
|---|---|---|
| 1.0.0 | 2026-11-20 | Ratificación inicial a partir de auditoría código ↔ doc ↔ base en vivo. |
| 1.1.0 | 2026-11-20 | Se agrega la sección "Ambientes: Sandbox → Producción" (NO NEGOCIABLE) y la puerta 0. Cierre de D1 y D2, decisión de D3 (borrado lógico), avance de D4 y D5, nueva deuda D10. |
| 1.2.0 | 2026-11-20 | Se agrega la sección "Alcance de los cambios: solo lo solicitado" (NO NEGOCIABLE), a pedido del dueño. |
