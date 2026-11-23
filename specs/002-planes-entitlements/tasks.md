---

description: "Lista de tareas de Planes & Entitlements v1"
---

# Tasks: Planes & Entitlements v1

**Input**: Documentos de diseño en `specs/002-planes-entitlements/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Incluidos, porque la spec exige trazabilidad requisito → test. Se escriben primero y deben fallar antes de implementar.

**Organization**: Tareas agrupadas por historia de usuario, para implementar y probar cada una por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: historia de usuario de la tarea (US1 a US5)

## Path Conventions

- **App**: `app/src/main/java/cl/storeflow/warehouse/` (abreviado `<app>/`)
- **Tests de app**: `app/src/test/java/cl/storeflow/warehouse/` (abreviado `<test>/`)
- **Servidor**: `supabase/`
- **Panel web**: repo hermano `C:\Users\Windows 11\Documents\dev\stockflow-web` (abreviado `<web>/`)

## Reglas para ejecutar estas tareas

- Las migraciones usan timestamp real en `<ts>`. Una vez aplicadas en Sandbox **no se editan**: un cambio va en una migración nueva.
- Toda migración va **primero a Sandbox**. Producción solo se toca en la Fase 9, con confirmación explícita del dueño.
- Toda función nueva usa `set search_path = ''` y nombres calificados. Las internas van en el esquema `private`. Las funciones de trigger que leen o bloquean `suscripciones` son `SECURITY DEFINER`, porque los clientes no tienen privilegios sobre esa tabla.
- Toda tabla nueva tiene PK `id uuid`, RLS activo y `REVOKE` explícito de los privilegios que no use.
- Textos de UI en español neutro LatAm, sin voseo. Logs con Timber. Sin capa UseCase. `*Entity` no sale de `data/`.
- Los textos de la app no incluyen precios, enlaces de compra ni llamados a comprar (FR-027).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Ramas y soporte de pruebas

- [X] T001 Crear la rama `feat/planes-entitlements` en este repo, partiendo de la rama que contiene los commits de la constitución, y, con autorización del dueño, commitear `specs/002-planes-entitlements/` antes de T109, que la necesita en un clon limpio
- [X] T002 Crear la rama `feat/planes-entitlements` en `<web>/`, sin incluir la carpeta sin seguimiento `<web>/StoreFlow/`
- [x] T003 Instalar `psql` y definir `STOREFLOW_SANDBOX_DB_URL` y `STOREFLOW_PROD_DB_URL` como variables de entorno locales, sin guardarlas en el repo. Las requieren `.githooks/pre-push` y `scripts/promover-migracion.sh`
- [X] T004 [P] Crear los fixtures en `supabase/tests/helpers/planes_fixtures.sql`: crear empresa con N bodegas y M usuarios, y vencer un trial moviendo `trial_inicio` y `trial_fin` juntos para respetar su check. Va en subcarpeta para que el glob de `pre-push` no lo ejecute como test, y se incluye con `\ir`
- [X] T005 [P] Crear `scripts/test-planes-concurrencia.sh`, que abre conexiones `psql` simultáneas contra `STOREFLOW_SANDBOX_DB_URL`, limpia sus datos al terminar y sale con código distinto de 0 si falla
- [X] T006 Ejecutar `scripts/test-planes-concurrencia.sh` desde `.githooks/pre-push`, dentro del bloque que corre los tests SQL cuando el push toca `supabase/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Corregir los defectos P1–P4 y crear el soporte local que usan todas las historias

**⚠️ CRITICAL**: Ninguna historia puede comenzar antes de completar esta fase. Si P1–P4 ya se corrigieron por separado, marcar sus tareas como hechas tras verificarlo.

### P2 y P3: la cola no pierde trabajo

- [X] T007 [P] Escribir `<test>/data/sync/ClasificadorRechazoTest.kt` con la tabla de `contracts/codigos-rechazo.md`: 2xx, cada `SFP01`–`SFP06`, `23503`, `23505` de PK en un `INSERT`, red, 408, 429, 5xx, 401 y otro 4xx, separando `movimientos` y `lotes` del resto
- [X] T008 Implementar el clasificador puro en `<app>/data/sync/ClasificadorRechazo.kt`, que clasifica por el campo `code` y no por status
- [X] T009 [P] Escribir `<test>/data/sync/ResolverRetencionTest.kt` con las reglas de `contracts/sync-cola.md`, incluido `DELETE` de bodega con un ítem anterior de `productos` retenido o pendiente, y que un `UPDATE` retenido de un producto **no** retenga sus movimientos (FR-028)
- [X] T010 Implementar el resolvedor puro en `<app>/data/sync/ResolverRetencion.kt`

### Evaluador de plan

- [X] T011 [P] Escribir `<test>/domain/model/EvaluadorPlanTest.kt` para FR-024: última validación + 7 días − 1 s conserva el plan, + 7 días aplica `degradacion_free`, atrasar el reloj no extiende, un desfase fijo no altera la cuenta (R11), y una bodega fuera de `bodegas_activas`, incluida una creada offline, queda en solo lectura (R22)
- [X] T012 Implementar `<app>/domain/model/EvaluadorPlan.kt` como clase pura con reloj inyectado, sin dependencias de Android

### Esquema Room

- [X] T013 Agregar a `<app>/data/local/entity/SyncEntity.kt` `rechazo_codigo: String?` (`SFP01`–`SFP06` u otro código de error permanente) y `rechazado_at: Date?`, ambos nulos por defecto
- [X] T014 [P] Crear `<app>/data/local/entity/PlanCacheEntity.kt` (tabla `plan_cache`) con `empresa_id` TEXT como PK, **no** `id = 1`, y los campos de `data-model.md`, incluidos `puede_crear_bodega`, `puede_crear_usuario`, `historial_desde`, `degradacion_free` y `dispositivo_id`. **Sin columnas de valores de límites** (FR-011)
- [X] T015 [P] Crear `<app>/data/local/entity/AvisoPlanEntity.kt` (tabla `avisos_plan`) con `id` TEXT PK generado como UUID (Principio V), `empresa_id`, `momento`, `cerrado`, `cierre_pendiente` e índice único (`empresa_id`, `momento`)
- [X] T016 Agregar a `<app>/data/local/dao/SyncDao.kt` consultas de ítems activos, retenidos, marcar rechazo y limpiar rechazos. `observarPendientes` debe excluir los retenidos, con un contador aparte
- [X] T017 Crear `<app>/data/local/dao/PlanCacheDao.kt`
- [X] T018 Crear `<app>/data/local/dao/AvisoPlanDao.kt`
- [X] T019 En `<app>/data/local/AppDatabase.kt`, registrar las entidades y DAOs, subir a la siguiente versión libre y agregar su `Migration`. Si la deuda D3 ya tomó la v10, usar la v11
- [X] T020 En `<app>/di/DatabaseModule.kt`, registrar la migración y proveer los DAOs nuevos, **sin cambiar** `PRAGMA foreign_keys = OFF` (R13)
- [X] T021 Compilar para exportar el esquema a `app/schemas/cl.storeflow.warehouse.data.local.AppDatabase/` y correr `app/src/test/java/cl/storeflow/warehouse/constitucion/EsquemaInvariantesTest.kt`

### P1: la sesión vencida sin red no borra datos

- [X] T022 [P] Escribir `<test>/data/repository/SesionRefreshTest.kt`: un error de red o timeout **no** se clasifica como sesión inválida; un refresh token rechazado por Auth sí. Con trabajo pendiente o retenido nunca se borra la base, y solo el mismo `user_id` puede retomar la sesión, incluidos `logout()`, `login` y `registrar()` (FR-030)
- [X] T023 Crear `<app>/data/sync/GestorSesion.kt`, singleton con `Mutex` y única vía de refresh, con Room como fuente e `importSession(session, autoRefresh = false)` (R17). Configurar `alwaysAutoRefresh = false` y `autoLoadFromStorage = false` en `<app>/data/remote/SupabaseClient.kt`. Usarlo en `checkSession()` de `<app>/data/repository/AuthRepository.kt`, en `<app>/data/sync/SyncWorker.kt` y en `<app>/data/sync/PullWorker.kt`. `clearAllTables()` corre solo ante sesión inválida confirmada **y** sin trabajo pendiente ni retenido. Ante error de red, devolver la sesión guardada. Al arrancar, importar la sesión de Room al cliente Auth aunque el token siga vigente

### Procesamiento de la cola

- [X] T024 Escribir `<test>/data/sync/ProcesadorColaTest.kt`: un ítem `SFP*` sigue retenido tras más de 3 ejecuciones, un error transitorio no se descarta, un retenido no provoca `retry` un `DELETE` de bodega no se envía si su traslado está retenido, un movimiento con `23503` no se descarta y se reenvía tras su padre, `23505` cuenta como éxito, y otro 4xx en `movimientos` o `lotes` queda retenido
- [X] T025 Implementar `<app>/data/sync/ProcesadorCola.kt` usando `ClasificadorRechazo` y `ResolverRetencion`
- [X] T026 Integrar `ProcesadorCola` en `<app>/data/sync/SyncWorker.kt`, eliminando el descarte por `MAX_REINTENTOS` para errores transitorios, por plan y para todo error de `movimientos` y `lotes`

### P4: cerrar el ingreso a empresas ajenas

- [X] T027 Ampliar `supabase/tests/aislamiento_empresas.sql`: una cuenta autenticada sin fila en `usuarios` no puede insertarse en la empresa B. Crear `supabase/tests/bodega_sin_cascada.sql` para SC-017: eliminar una bodega con productos falla con `SFP06`, sin productos pasa, y eliminar una empresa sigue funcionando
- [X] T028 Crear `supabase/migrations/<ts>_cerrar_insert_usuarios.sql` (M2), que elimina la política `usuario_insert_propio` y agrega el trigger `BEFORE DELETE` en `bodegas` que rechaza con `SFP06` si tiene productos, eximiendo acciones FK con `pg_trigger_depth() > 1` (FR-031, R18)
- [X] T029 Aplicar M2 en Sandbox y correr `supabase/tests/*.sql`, confirmando que `registrar_empresa` y la Edge Function `supabase/functions/registrar-usuario-empresa/index.ts` siguen funcionando
- [X] T030 Solo con confirmación explícita del dueño, promover M2 a Producción por separado con `scripts/promover-migracion.sh`, para cerrar P3 y P4 sin esperar el resto de la feature

**Checkpoint**: La app no pierde trabajo offline y el agujero de aislamiento está cerrado en Sandbox.

---

## Phase 3: User Story 1 - Reverse trial (Priority: P1) 🎯 MVP

**Goal**: Toda empresa nueva o existente obtiene Standard por 30 días sin pagar, y la app muestra su plan.

**Independent Test**: Crear una empresa y comprobar su plan efectivo y fecha de término, sin datos de pago. Vencer su trial y comprobar que el plan efectivo es Free.

### Tests for User Story 1 ⚠️

- [X] T031 [P] [US1] Escribir `supabase/tests/planes_modelo.sql`: SC-001, SC-002 (T − 1 s Standard, T Free), trial único (FR-004), catálogo con exactamente 3 filas, ADMIN sin escritura (FR-019), usuario de A no lee B (FR-020) y marca de tester no cae a Free (FR-018)
- [X] T032 [P] [US1] Escribir `<test>/data/repository/PlanRepositoryTest.kt`: guarda la respuesta, conserva la caché sin red, queda en "sin plan" ante `PGRST202`, y guarda a un usuario bloqueado con su `empresa_id` y `rol`
- [X] T033 [P] [US1] Escribir `<test>/ui/plan/PlanViewModelTest.kt`: el estado contiene tipo de plan, estado y días restantes del trial, sin valores de límites

### Implementation for User Story 1

- [X] T034 [US1] Crear `supabase/migrations/<ts>_planes_modelo.sql` (M1) con el esquema `private` y `planes_catalogo`: `plan` unique en `FREE`, `STANDARD`, `PREMIUM`; `max_bodegas`, `max_usuarios` nulos o `>= 1`; `historial_dias` nulo o `>= 1`; y los valores de R15
- [X] T035 [US1] Agregar a `supabase/migrations/<ts>_planes_modelo.sql` (M1) la tabla `suscripciones` de `data-model.md`, incluidos `primer_pago_standard_at` y `cobertura_fin`, con los checks `trial_fin = trial_inicio + interval '30 days'` y "`pagado_hasta` nulo si y solo si `plan_pagado` es nulo", RLS de lectura propia y `REVOKE` de `INSERT/UPDATE/DELETE`
- [X] T036 [US1] Agregar a `supabase/migrations/<ts>_planes_modelo.sql` (M1) `private.plan_efectivo(suscripcion, instante)`, función pura con el orden de R1. Fundador se agrega en M5
- [X] T037 [US1] Agregar a `supabase/migrations/<ts>_planes_modelo.sql` (M1) el trigger `AFTER INSERT` en `empresas` que crea la suscripción en la misma transacción (FR-001)
- [X] T038 [US1] Agregar a `supabase/migrations/<ts>_planes_modelo.sql` (M1) el backfill de FR-026: cada empresa existente recibe trial desde la migración, con `activado_en = now()`, `usaba_lotes` según tenga lotes y `es_tester = false`
- [X] T039 [US1] Agregar a `supabase/migrations/<ts>_planes_modelo.sql` (M1) `public.obtener_entitlement()` según `contracts/entitlement-rpc.md`, sin parámetros, sin precios y con `EXECUTE` solo para `authenticated`. En M1 devuelve `bloqueado = false` y listas vacías (R20)
- [X] T040 [US1] Aplicar M1 en Sandbox y correr `supabase/tests/*.sql`
- [X] T041 [US1] Implementar `<app>/data/repository/PlanRepository.kt`: validar, guardar en `plan_cache`, observar el plan y aplicar `EvaluadorPlan`
- [X] T042 [US1] En `<app>/data/sync/SyncWorker.kt`, revalidar el plan antes de revisar si la cola está vacía (`contracts/sync-cola.md`)
- [X] T043 [US1] En `<app>/data/repository/AuthRepository.kt`, consultar `obtener_entitlement` tras autenticar y **antes** de leer `usuarios` en `login`, y validar el plan tras `registrar`. Si `bloqueado = true`, guardar la sesión con `empresa_id`, `rol` y `bodega_id` vacío, sin leer más datos. Un fallo de red no impide el ingreso (R20)
- [X] T044 [US1] Implementar `<app>/ui/plan/PlanViewModel.kt`
- [X] T045 [US1] Implementar `<app>/ui/plan/PlanScreen.kt`: tipo de plan, estado y días restantes, sin valores de límites ni contenido de compra
- [X] T046 [US1] Agregar la ruta en `<app>/MainActivity.kt` y el acceso desde `<app>/ui/configuracion/ConfiguracionScreen.kt`

**Checkpoint**: US1 funciona y se puede probar sola. Todavía no se aplica ningún límite.

---

## Phase 4: User Story 2 - Downgrade seguro (Priority: P1)

**Goal**: Al caer a Free no se borra nada, lo excedente queda en solo lectura y se reactiva al volver a un plan que lo cubra.

**Independent Test**: Vencer el trial de una empresa con 3 bodegas y comparar conteos y stock antes y después. Volver a un plan que las cubra y editar.

### Tests for User Story 2 ⚠️

- [X] T047 [P] [US2] Escribir `supabase/tests/planes_solo_lectura.sql`: SC-003, SC-004, SC-006, SC-013, SC-014, regla de selección de FR-007, eliminar bodega en solo lectura, eliminar la única activa con excedentes, editar o borrar movimientos tras el downgrade (FR-022), producto nuevo en bodega en solo lectura rechazado aunque su fecha esté en la ventana (FR-028), stock de `productos_con_stock` sin cambios en Free con movimientos de más de 90 días (FR-021), y que `obtener_entitlement` entregue los conjuntos activos sin valores de límites (FR-011)
- [X] T048 [P] [US2] Ampliar `<test>/data/repository/BodegaRepositoryTest.kt`: no permite editar, eliminar ni trasladar hacia o desde una bodega en solo lectura
- [X] T049 [P] [US2] Ampliar `<test>/data/repository/MovimientoRepositoryTest.kt`: no registra en una bodega en solo lectura según el plan cacheado

### Implementation for User Story 2

- [X] T050 [US2] Crear `supabase/migrations/<ts>_planes_solo_lectura.sql` (M3) con helpers `private`: vencimiento con `coalesce(cobertura_fin, greatest(trial_fin, pagado_hasta))` (R7), bodegas activas y usuarios activos por (`created_at`, `id`), con el ADMIN más antiguo primero y la excepción del trial de activación (R8)
- [X] T051 [US2] Agregar a `supabase/migrations/<ts>_planes_solo_lectura.sql` (M3) triggers en `bodegas` y `usuarios`: `INSERT` asigna `created_at = clock_timestamp()` y `UPDATE` conserva el valor anterior sin error (R6). Usar `clock_timestamp()` y no `now()`, para que las altas de una misma transacción no empaten
- [X] T052 [US2] Agregar a `supabase/migrations/<ts>_planes_solo_lectura.sql` (M3) los triggers `BEFORE` de solo lectura en `bodegas`, `productos`, `lotes` y `producto_atributos` con código `SFP04`, validando origen y destino en traslados y eximiendo acciones FK con `pg_trigger_depth() > 1`
- [X] T053 [US2] Agregar a `supabase/migrations/<ts>_planes_solo_lectura.sql` (M3) la tabla `movimientos_fuera_plan` (`movimiento_id` unique, FK con `on delete cascade`) y los triggers de `movimientos`: en bodega en solo lectura o de usuario excedente, identificado siempre con `auth.uid()` y nunca con `usuario_id` del payload, acepta con fecha anterior a `vencimiento + 7 días` y marca; en caso contrario rechaza con `SFP04` o `SFP05` (FR-028, FR-029)
- [X] T054 [US2] Agregar a `supabase/migrations/<ts>_planes_solo_lectura.sql` (M3) el reemplazo de `public.obtener_entitlement` con `bloqueado`, `permisos`, `historial_desde`, `bodegas_solo_lectura` y `degradacion_free`, sin exponer valores de límites. A un excedente le devuelve solo `bloqueado`, `empresa_id`, `rol` y `ahora_servidor` (R20, R22)
- [X] T055 [US2] Aplicar M3 en Sandbox y correr `supabase/tests/*.sql`
- [X] T056 [US2] Agregar el bloqueo por plan cacheado a `<app>/data/repository/BodegaRepository.kt`, incluido eliminar la única bodega activa con excedentes
- [X] T057 [US2] Agregar el bloqueo a `<app>/data/repository/MovimientoRepository.kt`
- [X] T058 [P] [US2] Agregar el bloqueo a `<app>/data/repository/ProductoRepository.kt`
- [X] T059 [P] [US2] Mostrar el estado de solo lectura en `<app>/ui/bodegas/BodegaViewModel.kt` y `<app>/ui/bodegas/BodegasScreen.kt`
- [X] T060 [US2] Deshabilitar crear, editar y registrar en bodegas en solo lectura en `<app>/ui/productos/ProductosListScreen.kt` y `<app>/ui/movimientos/MovimientosScreen.kt`, manteniendo visible el stock

**Checkpoint**: US1 y US2 funcionan. Un downgrade no altera datos.

---

## Phase 5: User Story 3 - Enforcement server-side (Priority: P1)

**Goal**: Los límites no se pueden eludir desde un cliente, y lo rechazado queda retenido.

**Independent Test**: Con una empresa en Free, intentar crear una segunda bodega por REST sin la app. Con un usuario excedente, intentar leer y escribir con su JWT.

### Tests for User Story 3 ⚠️

- [X] T061 [P] [US3] Escribir `supabase/tests/planes_limites.sql`: SC-005, límite exacto, service role sujeto a límites, capacidad de lotes (`SFP03`), altas sobre Standard durante el trial de activación (FR-026) parametrización por tier (FR-011) y operación rechazada según el plan real aunque el cliente tenga un plan cacheado obsoleto (FR-010)
- [X] T062 [P] [US3] Escribir `supabase/tests/planes_bloqueo.sql` para la función de pre-request: excedente rechazado con `SFP05`, las rutas permitidas de `contracts/codigos-rechazo.md`, `POST` sin `return=minimal` o con `resolution=` rechazado, movimiento de excedente aceptado y marcado dentro de la ventana y rechazado fuera, usuario activo y service role sin bloqueo, y sin error ante un request sin JWT
- [X] T063 [US3] Agregar a `scripts/test-planes-concurrencia.sh` el caso de creaciones simultáneas de la última bodega: nunca se supera el límite
- [X] T064 [P] [US3] Ampliar `<test>/data/repository/MovimientoRepositoryTest.kt` para FR-025: sin lotes, la entrada de un perecedero se registra sin lote y la salida descuenta de lotes existentes
- [X] T065 [P] [US3] Ampliar `<test>/ui/movimientos/` con `HistorialMovimientosViewModelTest.kt`: un movimiento anterior a `historial_desde` no aparece; con `NULL` aparecen todos
- [X] T066 [P] [US3] Ampliar `<test>/ui/plan/PlanViewModelTest.kt`: sin el permiso correspondiente no se llama al repositorio de creación, y el ADMIN ve la cantidad retenida por usuario, sin el detalle (FR-023)

### Implementation for User Story 3

- [X] T067 [US3] Crear `supabase/migrations/<ts>_planes_limites_bloqueo.sql` (M4) con los triggers de límite de `bodegas` (`SFP01`) y `usuarios` (`SFP02`): bloquear la suscripción con `FOR NO KEY UPDATE` y luego contar, en función `VOLATILE` y `SECURITY DEFINER` (R5)
- [X] T068 [US3] Agregar a `supabase/migrations/<ts>_planes_limites_bloqueo.sql` (M4) la validación de capacidad de lotes con `SFP03`, aceptando un lote dentro de la ventana si la empresa tenía la capacidad (R7) y la excepción de `usaba_lotes` en el trial de activación
- [X] T069 [US3] Agregar a `supabase/migrations/<ts>_planes_limites_bloqueo.sql` (M4) la tabla `retenidos_resumen` (`cantidad` `> 0`, unique (`usuario_id`, `dispositivo_id`), sin acceso directo) y `public.reportar_retenidos(p_dispositivo uuid, p_cantidad integer)`: usuario y empresa desde la sesión, 0 elimina la fila, negativo responde error y no devuelve datos (R19)
- [X] T070 [US3] Agregar a `supabase/migrations/<ts>_planes_limites_bloqueo.sql` (M4) el reemplazo de `public.obtener_entitlement` que agrega `retenidos` solo para ADMIN, conservando los campos de M3
- [X] T071 [US3] Agregar a `supabase/migrations/<ts>_planes_limites_bloqueo.sql` (M4) `public.verificar_acceso_plan()` y su registro con `alter role authenticator set pgrst.db_pre_request` más `notify pgrst, 'reload config'`. Debe ser mínima, responder 403 con `code = SFP05` usando `raise sqlstate 'PGRST'`, exceptuar solo las rutas de `contracts/codigos-rechazo.md` y seguir los permisos y el manejo de errores de R2
- [X] T072 [US3] Devolver `409` con `code = SFP02` y eliminar el usuario de Auth creado en `supabase/functions/registrar-usuario-empresa/index.ts`
- [x] T073 [US3] Aplicar M4, desplegar la Edge Function en Sandbox, correr `supabase/tests/*.sql` y `scripts/test-planes-concurrencia.sh`, y comprobar por REST que un usuario activo sigue operando
- [X] T074 [US3] En `<app>/data/repository/MovimientoRepository.kt`, ignorar fecha y número de lote si el plan no incluye lotes (FR-025)
- [X] T075 [US3] Ocultar los campos de lote sin la capacidad en `<app>/ui/movimientos/MovimientoViewModel.kt` y `<app>/ui/movimientos/MovimientosScreen.kt`, permitiendo registrar la entrada
- [X] T076 [P] [US3] Bloquear altas según `permisos.crear_bodega` y `permisos.crear_usuario` en `<app>/ui/bodegas/BodegaViewModel.kt` y `<app>/ui/usuarios/UsuariosViewModel.kt`, sin mostrar valores de límites, enlaces ni llamados a comprar
- [X] T077 [US3] Crear `<app>/ui/plan/BloqueoPlanScreen.kt`: acceso bloqueado, sin datos del servidor y sin opción de cerrar sesión si hay trabajo retenido
- [X] T078 [US3] Mostrar el bloqueo en `<app>/MainActivity.kt` y `<app>/ui/auth/AuthViewModel.kt` cuando `bloqueado = true`, sin llamar a `clearAllTables()`
- [X] T079 [US3] En `<app>/data/sync/SyncWorker.kt`, si `bloqueado = true`, enviar solo los `INSERT` de `lotes` y `movimientos` con `Prefer: return=minimal`, sin los que dependan de un `INSERT` de producto o lote aún no aceptado, y conservar el resto de la cola (`contracts/sync-cola.md`)
- [X] T080 [US3] En `<app>/data/sync/PullWorker.kt`, terminar con `Result.success()` sin reintentar si `bloqueado = true` o si un request responde `SFP05`, sin modificar datos locales
- [X] T081 [US3] En `<app>/data/repository/PlanRepository.kt`, al pasar de `bloqueado = true` a `false`, leer el perfil, restaurar `bodega_id` con la primera bodega activa mediante `<app>/data/local/dao/AuthSessionDao.kt`, y lanzar `PullTrigger` y `SyncTrigger` (FR-029)
- [X] T082 [US3] En `<app>/data/repository/PlanRepository.kt` y `<app>/data/sync/SyncWorker.kt`, generar y conservar `dispositivo_id` e informar con `reportar_retenidos` cuando cambie la cantidad retenida (FR-023)
- [X] T083 [US3] Ocultar cerrar sesión a todo usuario con trabajo pendiente o retenido en `<app>/ui/configuracion/ConfiguracionScreen.kt`, y en `<app>/data/repository/AuthRepository.kt` hacer que `logout()` rechace la operación y que `login` y `registrar()` acepten solo al mismo usuario mientras exista ese trabajo (FR-030)
- [X] T084 [US3] Mostrar en `<app>/ui/plan/PlanViewModel.kt` y `<app>/ui/plan/PlanScreen.kt` el detalle del trabajo retenido en el propio dispositivo y, para ADMIN, la cantidad por usuario con su última actualización (FR-023)
- [X] T085 [US3] Aplicar `historial_desde` en `<app>/ui/movimientos/HistorialMovimientosViewModel.kt`, solo en lo visible. **No** cambiar consultas de stock de `<app>/data/local/dao/`
- [X] T086 [P] [US3] Crear `<web>/utils/getPlan.ts`, que llama a `obtener_entitlement` desde el servidor y distingue plan, usuario bloqueado, "sin plan" ante `PGRST202` (no restringe nada) y error (R21)
- [X] T087 [US3] Exigir la capacidad `export` antes de consultar datos en `<web>/app/api/export/movimientos/route.ts`, respondiendo 403 con `code = SFP03`. Ante otro error del plan responder 503. Aplicar `historial_desde` a las filas del CSV
- [X] T088 [US3] Exigir la capacidad `export` en `<web>/app/api/export/productos/route.ts`
- [X] T089 [US3] Aplicar `historial_desde` y ocultar el botón de export sin la capacidad en `<web>/app/dashboard/movimientos/page.tsx`
- [X] T090 [US3] Ocultar el botón de export sin la capacidad en `<web>/app/dashboard/productos/page.tsx`
- [X] T091 [US3] Aplicar `historial_desde` a los movimientos de `<web>/app/dashboard/productos/[id]/page.tsx` y a los recientes de `<web>/app/dashboard/page.tsx`, sin tocar los datos de stock
- [X] T092 [US3] Mostrar acceso bloqueado a un usuario excedente en `<web>/app/dashboard/layout.tsx`

**Checkpoint**: El núcleo (US1 a US3) está completo y validado en Sandbox.

---

## Phase 6: User Story 4 - Plan Fundador (Priority: P2)

**Goal**: Las primeras 100 empresas que pagan Standard obtienen Premium mientras mantengan su suscripción.

**Independent Test**: Con 99 Fundadores, registrar 10 pagos simultáneos y comprobar que el total es 100.

### Tests for User Story 4 ⚠️

- [X] T093 [P] [US4] Escribir `supabase/tests/planes_fundador.sql`: SC-008, SC-009, no reotorgamiento (FR-016), primer pago de Standard tras haber pagado Premium (FR-012), Fundador que pasa a Premium pagado y conserva el estatus (FR-015), `cobertura_fin` tras baja o paso a Free (R7), otorgamiento que sobrevive a eliminar la empresa (FR-013), `plan_cambios` con operador no vacío y funciones sin `EXECUTE` para `authenticated`
- [X] T094 [US4] Agregar a `scripts/test-planes-concurrencia.sh` el caso SC-007: con 99 otorgamientos, 10 pagos concurrentes dan exactamente 1 Fundador y 9 sin cupo

### Implementation for User Story 4

- [X] T095 [US4] Crear `supabase/migrations/<ts>_planes_fundador.sql` (M5) con `fundador_olas` (ola 1, cupo 100), `fundador_otorgamientos` (`empresa_id` unique **sin FK**, `motivo_perdida` en `BAJA`, `PASO_A_FREE`, `IMPAGO`) y `plan_cambios` (`operador` no vacío), sin acceso para clientes
- [X] T096 [US4] Agregar a `supabase/migrations/<ts>_planes_fundador.sql` (M5) el reemplazo de `private.plan_efectivo` y del helper de vencimiento: Fundador vigente con `perdido_at` nulo y `ahora < pagado_hasta + 7 días`
- [X] T097 [US4] Agregar a `supabase/migrations/<ts>_planes_fundador.sql` (M5) `private.registrar_pago` siguiendo los 5 pasos de `contracts/operador.md`
- [X] T098 [US4] Agregar a `supabase/migrations/<ts>_planes_fundador.sql` (M5) `private.registrar_baja`, `private.pasar_a_free` y `private.marcar_tester`, con `EXECUTE` revocado a `public`, `anon` y `authenticated`
- [x] T099 [US4] Aplicar M5 en Sandbox y correr `supabase/tests/*.sql` y `scripts/test-planes-concurrencia.sh`

**Checkpoint**: US4 funciona sin cambios en la app, porque la RPC ya entrega Premium como plan efectivo.

---

## Phase 7: User Story 5 - Avisos del ciclo de trial (Priority: P2)

**Goal**: El ADMIN ve dentro de la app un aviso en cada momento del trial.

**Independent Test**: Avanzar el ciclo del trial y comprobar que cada aviso se emite una vez, que no se emite a una empresa pagada y que al cerrarlo no reaparece.

### Tests for User Story 5 ⚠️

- [X] T100 [P] [US5] Escribir `supabase/tests/planes_avisos.sql`: momentos `DIA_0`, `MENOS_7`, `MENOS_1`, `TERMINO`, `MAS_2`, emisión única, sin avisos de vencimiento con plan pagado, solo ADMIN, cierre idempotente y cerrar un aviso cierra los anteriores para ese ADMIN
- [X] T101 [P] [US5] Ampliar `<test>/ui/plan/PlanViewModelTest.kt`: muestra solo el aviso más reciente sin cerrar, cerrarlo cierra los anteriores, un OPERADOR de bodega no lo ve, y sin señal se calcula desde el plan guardado

### Implementation for User Story 5

- [X] T102 [US5] Crear `supabase/migrations/<ts>_planes_avisos.sql` (M6) con `avisos_plan`, unique (`empresa_id`, `momento`), y reemplazar `obtener_entitlement` para emitir y devolver avisos, conservando todos los campos de M4
- [X] T103 [US5] Agregar a `supabase/migrations/<ts>_planes_avisos.sql` (M6) `public.cerrar_aviso_plan(p_momento text)`, solo para ADMIN de la empresa de la sesión, que cierra ese momento y los anteriores, idempotente y sin `empresa_id`
- [X] T104 [US5] Aplicar M6 en Sandbox y correr `supabase/tests/*.sql`
- [X] T105 [US5] En `<app>/data/repository/PlanRepository.kt`, guardar avisos, cerrar localmente ese aviso y los anteriores, y confirmar en el servidor los que tengan `cierre_pendiente`
- [X] T106 [P] [US5] Crear `<app>/ui/plan/AvisoPlanBanner.kt`: para recuperar el plan solo indica contactar a StoreFlow, sin enlaces, precios ni llamados a comprar (FR-027)
- [X] T107 [US5] Exponer el aviso en `<app>/ui/plan/PlanViewModel.kt` y mostrarlo a los ADMIN en `<app>/ui/dashboard/DashboardScreen.kt`

**Checkpoint**: Todas las historias funcionan por separado.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Verificación completa antes de publicar

- [X] T108 [P] Crear `<test>/constitucion/AntiSteeringApkTest.kt`: falla ante dependencia o permiso de billing, y ante precios o términos de compra en strings (FR-027)
- [X] T109 [P] Crear `<test>/constitucion/TrazabilidadSpecTest.kt`: lee `specs/002-planes-entitlements/spec.md` y falla si algún FR no está en la tabla de trazabilidad
- [x] T110 Correr `gradlew testDebugUnitTest`, `supabase/tests/*.sql` contra Sandbox, `scripts/test-planes-concurrencia.sh` y el build de `<web>/`, y reportar cualquier fallo con su salida
- [x] T111 Validar con el build `sandbox` los escenarios 1 a 14, 16 y 17 de `specs/002-planes-entitlements/quickstart.md` en dispositivo físico, con confirmación del dueño (puerta 3)
  - **Excepción aceptada por el dueño (2026-11-22):** el escenario 11 (gracia offline y atraso de reloj) no se ejecutó en dispositivo, porque requiere cambiar la fecha del teléfono.
- [X] T112 Probar el upgrade real desde Room v9 con datos, sin reinstalar, según el escenario 15 de `specs/002-planes-entitlements/quickstart.md` (puerta 4)
- [X] T113 [P] Actualizar `.harness/ESTADO.md` y `.harness/TASKS.md`, y registrar en `.specify/memory/constitution.md` lo que corresponda solo si el dueño lo pide

---

## Phase 9: Rollout

**Purpose**: Llevar la feature a Producción. **Cada tarea requiere una solicitud explícita del dueño.**

- [X] T115 Desplegar la versión compatible de `<web>/`
- [X] T116 Verificar que el historial de migraciones de Sandbox y Producción sea idéntico antes de promover (`scripts/promover-migracion.sh`)
- [ ] T117 Con confirmación explícita, promover las migraciones **en el orden M2 (si no se promovió antes), M1, M3, M4, M5, M6** a Producción con `scripts/promover-migracion.sh` y desplegar `supabase/functions/registrar-usuario-empresa/index.ts`. M1 inicia el trial de 30 días de las 11 empresas
- [ ] T118 Verificar en Producción, según la sección 4 de `specs/002-planes-entitlements/quickstart.md` y solo con `SELECT`: 11 suscripciones en trial, 0 con marca de tester, conteos de negocio sin cambios, y un request normal que sigue funcionando con el pre-request activo

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias
- **Foundational (Fase 2)**: depende de Setup y **bloquea todas las historias**
- **US1 (Fase 3)**: depende de Foundational
- **US2 (Fase 4)**: depende de US1 (usa plan efectivo y caché)
- **US3 (Fase 5)**: depende de US2 (M4 usa los helpers de M3)
- **US4 (Fase 6)**: depende de US1 y US2, porque T096 reemplaza el helper de vencimiento de M3
- **US5 (Fase 7)**: depende de US3, porque T102 reemplaza la RPC de M4
- **Polish (Fase 8)**: depende de las historias elegidas
- **Rollout (Fase 9)**: depende de Polish

### Orden de migraciones

M2 → M1 → M3 → M4 → M5 → M6, según se aplican en Sandbox. T117 debe promoverlas en ese mismo orden. La numeración es de nombre: el orden real lo da `<ts>`.

### Within Each User Story

- Tests primero, y deben fallar
- Migración → aplicar en Sandbox → repositorio → ViewModel → pantalla
- Una migración aplicada no se edita

### Parallel Opportunities

- **Setup**: T004, T005
- **Foundational**: T007, T009, T011, T014, T015, T022
- **User Story 1 - Reverse trial**: T031, T032, T033
- **User Story 2 - Downgrade seguro**: T047, T048, T049, T058, T059
- **User Story 3 - Enforcement server-side**: T061, T062, T064, T065, T066, T076, T086
- **User Story 4 - Plan Fundador**: T093
- **User Story 5 - Avisos del ciclo de trial**: T100, T101, T106
- **Polish & Cross-Cutting Concerns**: T108, T109, T113
- Las tareas marcadas de una misma fase se pueden hacer juntas; las migraciones siempre se aplican en orden.
---

## Parallel Example: User Story 3

```bash
# Tests, antes de implementar:
Task: "Escribir supabase/tests/planes_limites.sql"
Task: "Escribir supabase/tests/planes_bloqueo.sql"
Task: "Ampliar MovimientoRepositoryTest para FR-025"
Task: "Crear HistorialMovimientosViewModelTest"

# Panel web, tras aplicar M4:
Task: "Crear <web>/utils/getPlan.ts"
Task: "Exigir export en <web>/app/api/export/productos/route.ts"
Task: "Ocultar export en <web>/app/dashboard/productos/page.tsx"
```

---

## Implementation Strategy

### MVP

El MVP real es **US1 + US2 + US3**. Por separado no forman un modelo de negocio: US1 sola da un trial que no limita nada.

1. Completar Setup y Foundational. Esto ya corrige P1–P4 y tiene valor propio.
2. Completar US1 y validar.
3. Completar US2 y validar que un downgrade no altera datos.
4. Completar US3 y validar con acceso directo.
5. **Detenerse y validar en dispositivo físico** antes de seguir.

### Incremental Delivery

1. Foundational → la app deja de perder trabajo offline
2. US1 → trial visible
3. US2 → downgrade seguro
4. US3 → límites reales
5. US4 → Fundador
6. US5 → avisos

## Notes

- US4 y US5 se pueden recortar sin afectar el núcleo. Si se recortan, omitir M5 y M6 en T117.
- Ninguna tarea autoriza por sí sola commits, push, despliegues ni cambios en Producción.
- PEND-1 y PEND-2 de [spec.md](./spec.md) están postergados por el dueño, con revisión en marzo de 2027. No generan tareas en v1.