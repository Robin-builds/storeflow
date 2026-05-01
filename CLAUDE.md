# 🤖 CLAUDE.md — Contexto Persistente del Proyecto
**Pegar al inicio de CADA sesión de implementación.**
**Última actualización:** Mayo 2026 — Fase 8 S1 completa (Usuario domain object)

---

## ⚙️ COMANDOS

```bash
# En Windows usar gradlew.bat (NO ./gradlew — es script Unix)
gradlew.bat assembleDebug                                          # build APK debug
gradlew.bat installDebug                                           # instalar en dispositivo
gradlew.bat test                                                   # unit tests
gradlew.bat test --tests "cl.stockflow.warehouse.ExampleUnitTest"  # test específico
gradlew.bat connectedAndroidTest                                   # tests instrumentados
gradlew.bat lint                                                   # lint
gradlew.bat clean                                                  # limpiar build
```

---

## 🎯 PROYECTO

**Nombre:** StockFlow (package: `cl.stockflow.warehouse`)
**Tipo:** Micro-SaaS de inventario para pequeñas empresas chilenas
**Estado:** Fases 0–5B completas. En planificación: Fase 6 (Multi-bodega + Roles).

---

## 🛠️ STACK EXACTO (no asumir versiones)

**Stack actual del proyecto:**
```
Kotlin:         2.0.21
AGP:            8.13.2
JDK:            11
Min SDK:        27 (Android 8.1)
Target SDK:     36 (Android 16)
Compile SDK:    36
Compose BOM:    2024.09.00
```

**Dependencias agregadas en Fase 0:**
```
KSP:            2.0.21-1.0.28
Room:           2.6.1
Hilt:           2.50
Hilt Navigation Compose: 1.1.0
```

**Dependencias agregadas en Fase 1:**
```
Navigation Compose: 2.7.6
Supabase BOM:   1.4.6  (postgrest-kt, realtime-kt, auth-kt)
Ktor Client:    2.3.7  (ktor-client-android)
Coroutines:     1.7.3
Lifecycle:      2.7.0
Activity Compose: 1.8.1
Gson:           2.10.1
```

**Dependencias de test agregadas en Fase 8 S1:**
```
mockk:                  1.13.12  (testImplementation — mocking para unit tests)
kotlinx-coroutines-test:1.7.3   (testImplementation — runTest + Flow testing)
```

---

## 🏗️ ARQUITECTURA

**Patrón:** Clean Architecture
```
ui/
  auth/         → LoginScreen, RegistroScreen, AuthViewModel
  dashboard/    → DashboardScreen (consume AlertasViewModel para count)
  productos/    → ProductosListScreen, ProductoViewModel
  movimientos/  → MovimientosScreen, MovimientoViewModel
  alertas/      → AlertasScreen, AlertasViewModel
domain/
  model/      → SesionUsuario, ProductoConStock
data/
  local/
    entity/   → 8 entidades Room (incluye AuthSessionEntity)
    dao/      → 8 DAOs
    AppDatabase.kt  (versión 3)
    DateConverters.kt
  remote/     → SupabaseClient
  repository/ → AuthRepository, ProductoRepository, MovimientoRepository
di/           → DatabaseModule
```

**Multi-tenancy:** JWT custom claims (`empresa_id` en `app_metadata`)
→ RLS en Supabase filtra por empresa automáticamente
→ El código Kotlin NO filtra manualmente por empresa_id

**Roles de usuario:**
→ Enum `Rol` en `domain/model/Rol.kt`: `ADMIN`, `OPERADOR`
→ `rol` se persiste en `AuthSessionEntity` (se lee de tabla `usuarios` al hacer login)
→ `SesionUsuario` expone el `rol` al ViewModel/UI
→ RLS en Supabase NO usa el rol — solo la app Android lo usa para control de acceso en UI
→ Regla: solo ADMIN puede crear/eliminar bodegas; OPERADOR solo puede seleccionar bodega activa
→ El primer usuario de cada empresa es ADMIN (asignado por RPC `registrar_empresa`)

**Auth:** Supabase Auth (`auth-kt`) — email/password
→ Token + `empresa_id` + `bodega_id` se guardan en Room (`auth_sessions`) — NO en memoria
→ Al abrir app: si hay sesión en Room → Dashboard, si no → Login
→ Registro crea atómicamente via RPC `registrar_empresa` (SECURITY DEFINER): empresa + usuario (ADMIN) + bodega "Bodega Principal"
→ El cliente Supabase NO persiste sesión entre reinicios — solo Room es fuente de verdad local

**Decisión crítica — FK constraints en Room:**
→ `PRAGMA foreign_keys = OFF` en `DatabaseModule.addCallback`
→ Room es caché offline-first con datos parciales; integridad referencial la garantiza Supabase
→ Sin esto: error 787 SQLITE_CONSTRAINT_FOREIGNKEY al insertar productos (empresas/bodegas vacías en Room local)

**Decisión — MovimientoEntity.nota nullable en DB, obligatoria en negocio:**
→ El esquema SQLite permite NULL; la obligatoriedad se garantiza en `MovimientoRepository`
→ Excepción conocida: stock inicial en `ProductoRepository.crear()` usa `nota = "Stock inicial"` directo al DAO (evita dependencia circular con MovimientoRepository)
→ No agregar esa dependencia circular — la excepción es legítima y está documentada

**Decisión — `precio: Int` en toda la cadena (no Double):**
→ CLP (peso chileno) no usa decimales → `Int` es suficiente y más simple
→ `ProductoEntity.precio: Int`, `ProductoConStock.precio: Int`; UI con `KeyboardType.Number`
→ Columna Supabase migrada de `numeric(12,2)` a `integer` (migración aplicada 2026-04-26)
→ `ProductoDto.precio: Double` en el DTO para tolerar si PostgREST devuelve `20000.00`; `.toInt()` en `toEntity()`

**Decisión — Room migration 4→5 para agregar `rol` en `auth_sessions`:**
→ `ALTER TABLE auth_sessions ADD COLUMN rol TEXT NOT NULL DEFAULT 'ADMIN'`
→ DEFAULT 'ADMIN' es seguro: todos los usuarios existentes fueron creados vía RPC como ADMIN
→ Migración no requiere recrear tabla (sin FK constraints en `auth_sessions`)

**Decisión — Token JWT: logout forzado, sin refresh por ahora:**
→ `checkSession()` valida `expires_at`; si expiró → limpia sesión → usuario re-hace login
→ Refresh completo (usando `refresh_token` con el cliente Supabase) se implementa en Fase 5
→ JWT de Supabase dura 1 hora por defecto; riesgo bajo hasta Fase 5

---

## 🗄️ MODELO DE DATOS (7 entidades, 5 niveles)

```
Nivel 0: EmpresaEntity          (independiente)
Nivel 1: UsuarioEntity          (FK → empresa)
         BodegaEntity           (FK → empresa)
         ProveedorEntity        (FK → empresa)
Nivel 2: ProductoEntity         (FK → empresa + bodega)
Nivel 3: MovimientoEntity       (FK → producto) — INMUTABLE
Nivel 4: SyncEntity             (FK → cualquier entidad)
```

**Regla crítica de stock:**
Stock NUNCA se almacena. Siempre se calcula:
```sql
SELECT COALESCE(SUM(cantidad), 0) FROM movimientos WHERE producto_id = :id
```

**Campos obligatorios en TODAS las entidades:**
```kotlin
val synced: Boolean = false
val synced_at: Date? = null
val created_at: Date = Date()
val updated_at: Date = Date()
```

---

## 🔐 PATRÓN DE ERRORES

```kotlin
// En Repository: try-catch, retorna Result<T>
// En ViewModel: maneja Result, expone StateFlow<UiState>
// En UI: observa UiState, nunca llama suspend directo

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```

---

## 📐 CONVENCIONES

```
Idioma variables:   español (nombre_producto, no productName)
Idioma comentarios: español
Idioma commits:     inglés semántico (feat:, fix:, refactor:)
Logs:               Timber (no Log.d)
Nombres tablas SQL: plural minúsculas (empresas, productos, movimientos)
PKs:                String UUID (viene de Supabase)
```

---

## 📁 DOCUMENTACIÓN DE REFERENCIA

Ruta base: `C:\Users\Windows 11\Documents\dev\manegenet_inventory_MSaas_v0.0.1\documentacion\`

```
01_PROYECTO_INICIAL.md        → Setup Android, Gradle, estructura
02_ENTITY_CREATION_ORDER.md   → Entities, DAOs, orden de creación
03_DEFINITION_OF_DONE.md      → Checklist por feature (LEER antes de cada fase)
04_GIT_WORKFLOW.md            → Ramas, commits semánticos, releases
05_SYNC_ALGORITHM_DETAILED.md → Algoritmo offline-first (Fase 5)
IMPLEMENTATION_PLAN.md        → Plan de sesiones atómicas
DEVELOPMENT_LOG.md            → Estado actual del proyecto
HUECOS_Y_SOLUCIONES.md        → Decisiones y problemas resueltos
```

---

## 📊 ESTADO ACTUAL

> **Actualizar en cada sesión antes de pegar**

```
FASE 0 (Setup):           ✅ Completa
FASE 1 (Auth):            ✅ Completa
FASE 2 (Productos CRUD):  ✅ Completa
FASE 3 (Movimientos):     ✅ Completa
FASE 4 (Alertas):         ✅ Completa
FASE 5A (Sync push):      ✅ Completa — validada en dispositivo físico
FASE 5B (Sync pull):      ✅ Completa — validada en dispositivo físico (2 cuentas por separado)
FASE 6 (Multi-bodega):    ✅ Completa — validada en dispositivo físico (Mayo 2026)
FASE 7 (Pulido UI):       ☐ Pendiente — puede ir antes del lanzamiento
FASE 8 S1 (Usuario obj):  ✅ Completa — 12 unit tests verdes
FASE 8 S2 (Bodega obj):   ☐ Próxima sesión
FASE 8 S3 (Producto obj): ☐ Pendiente
FASE 8 S4 (UI migración): ☐ Pendiente
FASE 8 S5 (Atributos):    ☐ Pendiente
WHATSAPP (Notif.):        ☐ Pendiente — requiere Fase 5 + aprobación Meta (iniciar trámite ya)
```

**Último commit:**  `6e6d9ac` — test: Phase 8 S1 — unit tests for Usuario domain object and UsuarioRepository
**Rama activa:**    `dev-rich-domain`
**Próxima sesión:** Fase 8 — S2: `Bodega` domain object

**Lo construido en Fase 1:**
- `AuthSessionEntity` (campos: access_token, refresh_token, user_id, empresa_id, **bodega_id**) → `AppDatabase` v2→v3
- `SupabaseClient` con anon key JWT correcta (sin session storage — Room es fuente de verdad)
- `AuthRepository`: login (guarda empresa_id + bodega_id), logout, checkSession, registrar con recuperación de huérfanos
- `AuthViewModel` expone `AuthUiState` vía `StateFlow`
- `LoginScreen`, `RegistroScreen`, `DashboardScreen` en Compose
- `SesionUsuario` domain model
- Función SQL `registrar_empresa` (SECURITY DEFINER) en Supabase
- RLS corregida: `get_empresa_id()` lee de tabla `usuarios`
- Verificado end-to-end en dispositivo físico: login ✅ registro ✅

**Lo construido en Fase 2:**
- `ProductoConStock` domain model (producto + stock calculado por JOIN)
- `ProductoDao`: CRUD + `observarConStock()` (JOIN con movimientos) + `contarConNombre()` (validación duplicado)
- `BodegaDao`: `obtenerPrimeraParaEmpresa()` agregado
- `ProductoRepository`: CRUD, validación duplicado, stock inicial via `MovimientoEntity(ENTRADA)`
- `ProductoViewModel`: `ProductosUiState` + `FormUiState(mensaje)`, búsqueda en memoria con `combine`
- `ProductosListScreen`: lista con stock/alerta ⚠️, buscador, dialog crear/editar/eliminar, Snackbar
- `DatabaseModule`: `PRAGMA foreign_keys = OFF` + migrations 1→2→3
- Verificado en dispositivo: crear ✅ stock inicial ✅ editar ✅ eliminar ✅ buscar ✅ snackbar ✅ duplicado ✅

**Lo construido en Fase 3:**
- `MovimientoRepository`: registrarEntrada / registrarSalida (valida stock disponible) / registrarAjuste (delta a objetivo); nota obligatoria en los tres tipos
- `MovimientoViewModel`: SavedStateHandle para productoId, `combine` de producto+movimientos en un solo Flow
- `MovimientosScreen`: tarjeta de stock actual (roja si bajo mínimo), 3 botones de acción, historial con tipo/cantidad/nota/fecha, dialogs por tipo con placeholder contextual
- `ProductoDao`: query `observarProductoConStock(productoId)` para live updates por producto individual
- `ProductosListScreen`: agrega flecha → por producto para navegar a movimientos; tap en fila sigue abriendo edición
- `MainActivity`: ruta `movimientos/{productoId}`
- Decisión: nota obligatoria en ENTRADA/SALIDA/AJUSTE — toda modificación de inventario debe tener razón documentada

**Lo construido en Fase 4:**
- `ProductoDao.observarBajoMinimo(bodegaId)`: mismo JOIN de `observarConStock` con `HAVING stock_actual < stock_minimo AND stock_minimo > 0`, ordenado por ratio crítico ascendente (más urgente primero)
- `AlertasViewModel`: reutiliza `ProductoRepository.obtenerContexto()` + `observarBajoMinimo`; expone `AlertasUiState`
- `AlertasScreen`: lista con icono ⚠️, stock actual en rojo, flecha directa a movimientos; empty state "Todo en orden"
- `DashboardScreen`: consume `AlertasViewModel` — tarjeta roja visible solo si `count > 0`, navega a `AlertasScreen`
- `MainActivity`: ruta `alertas`
- Notificaciones push locales diferidas — requieren `WorkManager` + permiso `POST_NOTIFICATIONS`

**Fix entre Fase 3 y Fase 4:**
- `AuthRepository.checkSession()`: valida `expires_at` antes de devolver sesión — si expiró limpia Room y retorna `null` → `AuthViewModel` redirige a LoginScreen en lugar de fallo silencioso
- Refresh de token diferido a Fase 5 (Sync) — es donde vive la lógica completa

**Lo construido en Fase 5A (Sync push):**
- `gradle/libs.versions.toml` + `app/build.gradle.kts`: dependencias `work-runtime-ktx:2.9.1` y `hilt-work:1.2.0`
- `SupabaseClient.kt`: constantes `SUPABASE_URL` y `SUPABASE_ANON_KEY` cambiadas de `private` a `internal` (visibles en `data/sync/`)
- `ProductoDao.marcarSincronizado(id, ahora)` + `MovimientoDao.marcarSincronizado(id, ahora)`: actualizan `synced=1` y `synced_at` tras push exitoso
- `SyncPayloads.kt`: extensiones `toSyncInsert/Update/Delete` para `ProductoEntity`; `toSyncInsert` para `MovimientoEntity`; serialización ISO 8601 UTC con `SimpleDateFormat`; excluye campos `synced/synced_at` del payload
- `SyncWorker.kt` (`@HiltWorker`): lee sesión de Room, refresca token via Ktor si expirado, procesa cola FIFO, máx 3 reintentos (`MAX_REINTENTOS=3`), llama `marcarSincronizado` + `syncDao.eliminar` en éxito
- `ProductoRepository.kt`: inyecta `SyncDao`; encola `toSyncInsert/Update/Delete` en `crear/actualizar/eliminar`
- `MovimientoRepository.kt`: inyecta `SyncDao`; encola `toSyncInsert` en `registrarEntrada/Salida/Ajuste`
- `StockFlowApp.kt`: implementa `Configuration.Provider` + inyecta `HiltWorkerFactory`; encola `SyncWorker` con `ExistingWorkPolicy.KEEP` y constraint `CONNECTED` en `onCreate()`
- `AndroidManifest.xml`: elimina `WorkManagerInitializer` del startup para evitar conflicto con `Configuration.Provider`
- Fix timing: `SyncTrigger` singleton inyectable — encola con `REPLACE` después de cada escritura en repositorios
- Fix: `jsonPrimitive.long` → `jsonPrimitive.content.toLongOrNull()` (propiedad sin import explícito en kotlinx.serialization.json 1.x)
- Verificado en dispositivo físico: crear ✅ editar ✅ eliminar ✅ movimiento ✅ offline→online ✅

**Fixes aplicados en sesión de depuración Fase 5A:**
- Bug seguridad: `AuthRepository.logout()` ahora llama `db.clearAllTables()` (no solo `authSessionDao.limpiarSesion()`) — evita que datos de empresa A sean visibles al usuario de empresa B
- `clearAllTables()` envuelto en `withContext(Dispatchers.IO)` — era llamado en hilo principal y crasheaba
- `ksp("androidx.hilt:hilt-compiler:1.2.0")` agregado a `build.gradle.kts` — faltaba el procesador KSP de `@HiltWorker`; sin él `HiltWorkerFactory` devolvía null y WorkManager caía al fallback por reflexión

**Lo construido en Fase 5B (Sync pull):**
- `PullDtos.kt`: DTOs `@Serializable` para las 6 tablas (Empresa, Bodega, Proveedor, Usuario, Producto, Movimiento); parseo de fechas ISO 8601 via `OffsetDateTime` (minSdk 27); `synced/synced_at` no vienen de Supabase — se setean localmente a `true/Date()` en `toEntity()`
- `PullWorker.kt` (`@HiltWorker`): hace GET `/rest/v1/<tabla>?select=*` con Ktor en orden de dependencia FK (empresas → bodegas/proveedores/usuarios → productos → movimientos); usa `Json { ignoreUnknownKeys = true }` para tolerar columnas extra; retorna `Result.retry()` si cualquier tabla falla
- `PullTrigger.kt`: singleton inyectable, encola `PullWorker` con `ExistingWorkPolicy.REPLACE` (corregido de KEEP — ver fixes 5B)
- `[6 DAOs]`: agregado `upsertAll(List<Entity>)` con `OnConflictStrategy.REPLACE` en EmpresaDao, BodegaDao, ProveedorDao, UsuarioDao, ProductoDao, MovimientoDao
- `StockFlowApp.kt`: encola `PullWorker` (además de `SyncWorker`) en `onCreate()`
- `AuthRepository.kt`: inyecta `PullTrigger`; llama `pullTrigger.trigger()` tras login exitoso

**Fixes aplicados en sesión de depuración Fase 5B (2026-04-26) — bug persiste tras todos estos fixes:**

Fix 1 — `EmpresaDto.rut: String` → nullable
- `registrar_empresa` no recibe `p_rut`, campo es null en Supabase → crash serialización → short-circuit bloqueaba toda la cadena. Fix: `rut: String? = null`, `rut ?: ""`

Fix 2 — `ExistingWorkPolicy.KEEP` → `REPLACE` en `PullTrigger`
- Race condition: si WorkManager no había ejecutado el PullWorker de `onCreate()` cuando login completaba, el segundo enqueue se ignoraba. Fix: `REPLACE`

Fix 3 — Short-circuit `&&` eliminado en `PullWorker`
- Con `&&` en cadena, un fallo en `usuarios` bloqueaba el pull de `productos`. Fix: cada tabla se asigna a `val e1..e6` independientes antes de combinar el resultado

Fix 4 — `UsuarioDto.nombre` y `rol` → nullable
- Campo `usuarios.nombre` potencialmente null si RPC no lo setea. Fix: `nombre: String? = null`, `rol: String? = null`, valores vacíos como fallback

Fix 5 — ~~`ProductoDto.precio: String`~~ **DIAGNOSIS INCORRECTA — revertida en Fix 7**
- El `SerializationException` que se atribuyó al tipo de `precio` era en realidad el error del plugin faltante (Fix 6). Se aplicó `precio: String` pero no resolvió el bug. Fix 7 revierte esto.

**Lo confirmado via Supabase MCP (herramienta disponible en sesión):**
- 3 empresas, 3 usuarios, 3 bodegas, 5 productos, 7 movimientos en Supabase
- `get_empresa_id()` es SECURITY DEFINER ✅ — RLS correcta
- Logs API Supabase confirman: PullWorker ejecuta todas las tablas, todos los GETs retornan 200
- El bug al cierre de sesión está entre `upsertAll` y la UI — no en red/auth

**Estado al cierre de sesión 2026-04-26:** Fase 5B completa y validada.

**Deuda técnica conocida:**
- Refresh de token JWT: actualmente logout forzado al expirar — refresh completo pendiente
- `SyncPayloads.kt` solo cubre `productos` y `movimientos` — empresas/bodegas/proveedores sin push todavía (se crean vía RPC, no necesitan push por ahora)

**Lo construido en Fase 8 S1:**
- `domain/model/Usuario.kt` — objeto rico con `esAdmin()`, `puedeGestionarBodegas()`, `puedeEliminarProductos()`, `puedeRegistrarMovimientos()`
- `data/repository/UsuarioRepository.kt` — `observarUsuarioActual(): Flow<Usuario?>` + `obtenerUsuarioActual(): Usuario?`; usa `sesion.rol` como fuente autoritativa (funciona antes del primer pull si `UsuarioEntity` no existe aún)
- `BodegaViewModel.kt` — ahora combina 3 flows; `esAdmin` viene de `usuario?.esAdmin()` en lugar del check manual `Rol.fromString(...) == Rol.ADMIN`
- Tests: `UsuarioTest` (5 casos) + `UsuarioRepositoryTest` (7 casos) — 12/12 verdes

---

## 🗺️ ROADMAP DE FEATURES ADICIONALES

Features identificadas fuera del plan original. Cada una tiene su prerequisito y consideraciones clave.

---

### 🏗️ PLAN FASE 6 — Multi-bodega + Roles (rama: `dev-warehouse`)

**Objetivo:** Permitir que una empresa opere con múltiples bodegas y que la app controle acceso según el rol del usuario (ADMIN vs. OPERADOR).

**Principio guía:** La capa de datos ya está lista. El trabajo es casi exclusivamente en nuevos archivos de lógica y UI, con cambios mínimos sobre los existentes.

---

#### S1 — Rol enum + Room migration + AuthRepository
**Archivos a crear:**
- `domain/model/Rol.kt` — `enum class Rol { ADMIN, OPERADOR }`

**Archivos a modificar:**
- `domain/model/SesionUsuario.kt` — agregar campo `val rol: Rol`
- `data/local/entity/AuthSessionEntity.kt` — agregar `val rol: String = "ADMIN"` (Room v4→v5)
- `data/local/AppDatabase.kt` — versión 5, MIGRATION_4_5: `ALTER TABLE auth_sessions ADD COLUMN rol TEXT NOT NULL DEFAULT 'ADMIN'`
- `data/repository/AuthRepository.kt`:
  - `login()`: leer campo `rol` de tabla `usuarios` junto con `empresa_id`; guardarlo en `AuthSessionEntity`
  - `registrar()`: después del RPC, leer `rol` del usuario recién creado y guardarlo en sesión
  - Agregar `obtenerRolActual(): Rol?` — lee `AuthSessionEntity.rol` y lo mapea al enum

**DoD:** Compila. Login guarda `rol` en sesión. `obtenerRolActual()` retorna `Rol.ADMIN` para usuarios existentes.
**Commit:** `feat: Phase 6 S1 — Rol enum, Room migration v5, AuthRepository reads rol`

---

#### S2 — BodegaRepository
**Archivos a crear:**
- `data/repository/BodegaRepository.kt`
  - `observarBodegas(): Flow<List<BodegaEntity>>` — lee `empresa_id` de sesión, usa `BodegaDao.observarPorEmpresa()`
  - `obtenerBodegaActiva(): BodegaEntity?` — por `AuthSessionEntity.bodega_id`
  - `crear(nombre: String, ubicacion: String?)` — inserta en Room + encola `SyncDao INSERT`
  - `eliminar(id: String)` — elimina en Room + encola `SyncDao DELETE`
  - `cambiarBodegaActiva(bodegaId: String)` — actualiza `AuthSessionEntity.bodega_id` en Room
  - Inyectado con Hilt vía `DatabaseModule`

**DoD:** Compila. Métodos retornan `Result<Unit>`. `cambiarBodegaActiva()` actualiza la sesión en Room.
**Commit:** `feat: Phase 6 S2 — BodegaRepository with CRUD and active-bodega switch`

---

#### S3 — BodegaViewModel
**Archivos a crear:**
- `ui/bodegas/BodegaViewModel.kt`
  - `BodegasUiState`: `Cargando`, `Listo(bodegas: List<BodegaEntity>, activa: BodegaEntity?, esAdmin: Boolean)`
  - Expone `uiState: StateFlow<BodegasUiState>`
  - `crear(nombre, ubicacion)` — solo si `esAdmin`; Snackbar si OPERADOR intenta
  - `eliminar(id)` — solo si `esAdmin`; no permite eliminar la bodega activa
  - `cambiarBodegaActiva(bodegaId)` — disponible para cualquier rol; emite evento de navegación

**Evento de navegación al cambiar bodega:** `SharedFlow<Unit>` → UI hace `navController.navigate("dashboard") { popUpTo(0) { inclusive = true } }` (vacía el backstack para que los ViewModels se reinicialicen)

**DoD:** Compila. `esAdmin` refleja correctamente el rol de sesión. Cambio de bodega emite el evento.
**Commit:** `feat: Phase 6 S3 — BodegaViewModel with role-gated CRUD`

---

#### S4 — BodegasScreen + DashboardScreen actualizado
**Archivos a crear:**
- `ui/bodegas/BodegasScreen.kt`
  - Lista de bodegas con nombre + ubicación (si existe)
  - Fila activa destacada (check o chip "Activa")
  - Tap en fila → `cambiarBodegaActiva()` → navega a Dashboard
  - FAB "Nueva bodega" visible solo si `esAdmin`
  - Botón eliminar (ícono papelera) visible solo si `esAdmin` y bodega no es la activa
  - Dialog confirmación antes de eliminar
  - Dialog form crear: campo `nombre` (obligatorio) + campo `ubicacion` (opcional)

**Archivos a modificar:**
- `ui/dashboard/DashboardScreen.kt`
  - Mostrar nombre de bodega activa en el header (subtítulo o chip)
  - Botón "Gestionar bodegas" → navega a `bodegas`
- `MainActivity.kt`
  - Nueva ruta `bodegas`
  - Al evento de cambio de bodega: `popUpTo("dashboard") { inclusive = true }` + navigate a dashboard

**DoD:** Golden path: ADMIN crea bodega → aparece en lista → tap → inventario cambia → dashboard muestra nombre nuevo. OPERADOR no ve FAB ni botón eliminar.
**Commit:** `feat: Phase 6 S4 — BodegasScreen and Dashboard with bodega selector`

---

#### S5 — Sync push para bodegas
**Archivos a modificar:**
- `data/sync/SyncPayloads.kt` — agregar extensiones para `BodegaEntity`:
  - `BodegaEntity.toSyncInsert()` — payload JSON para POST a `/rest/v1/bodegas`
  - `BodegaEntity.toSyncUpdate()` — payload JSON para PATCH
  - `BodegaEntity.toSyncDelete()` — payload para DELETE
- `data/local/dao/BodegaDao.kt` — agregar `marcarSincronizado(id: String, ahora: Date)`
- `data/sync/SyncWorker.kt` — procesar `SyncEntity` con `tabla = "bodegas"` igual que productos

**DoD:** Crear bodega offline → sync → aparece en Supabase. Eliminar bodega → sync → desaparece en Supabase.
**Commit:** `feat: Phase 6 S5 — Sync push for bodegas`

---

#### Restricciones globales de la Fase 6
- No agregar roles a la lógica de RLS de Supabase — el control de rol es solo en UI/Android
- No cambiar el patrón `obtenerContexto()` en repositorios existentes — solo lee `bodega_id`
- Al cambiar bodega, vaciar el backstack completo para reinicializar todos los ViewModels
- `eliminar()` debe validar que la bodega no sea la activa antes de proceder
- Si la empresa solo tiene 1 bodega, no mostrar botón eliminar (aunque sea ADMIN)

---

### 📦 Multi-bodega
**Prerequisito:** Fase 5 (Sync) completa
**Por qué esperar:** sin Sync, los movimientos de múltiples bodegas no llegan a Supabase de forma confiable; construir la lógica de selección de bodega activa antes del algoritmo de sync obliga a reescribirla.
**Lo que ya existe:** `BodegaEntity` (FK → empresa, campo `ubicacion`), `BodegaDao` con CRUD completo y `observarPorEmpresa()`. El modelo está listo.
**Lo que falta:**
- UI para crear/listar/eliminar bodegas (nueva pantalla desde Dashboard)
- Selector de bodega activa — actualmente `AuthSessionEntity.bodega_id` guarda solo una; necesita mecanismo de cambio (ej. menú en Dashboard)
- Al cambiar de bodega activa, todo el inventario visible cambia (productos, movimientos, alertas)
- Roles: considerar si todos los usuarios de la empresa pueden cambiar de bodega o solo ADMIN

---

### 📷 Escaneo de códigos de barra / QR
**Prerequisito:** ninguno — puede implementarse ahora (Fase 3 ya completa)
**Por qué es independiente:** es solo una capa de input; el escáner rellena el campo `sku` al crear/editar producto, o la cantidad en un movimiento. No toca arquitectura.
**Biblioteca recomendada:** ML Kit Barcode Scanning (Google) — sin dependencia de app externa, funciona offline
**Puntos de integración:**
- `ProductoFormDialog`: botón de cámara junto al campo SKU → escanea y rellena
- `MovimientoDialog`: escanear SKU para identificar el producto destino (útil en futuras pantallas de movimiento masivo)
**Consideración:** agregar permiso `CAMERA` en `AndroidManifest.xml` y request en runtime

---

### 💬 Notificaciones WhatsApp
**Prerequisito:** Fase 5 (Sync) completa + aprobación WhatsApp Business API (Meta)
**Por qué esperar Sync:** el trigger vive en Supabase — solo puede detectar stock bajo cuando los movimientos ya están en la base de datos remota. Sin Sync, los datos solo existen en Room y el servidor nunca se entera.
**Arquitectura:** cero impacto en código Android. Todo es server-side:
```
Movimiento synced a Supabase
  → DB trigger o Supabase Realtime detecta stock_actual < stock_minimo
  → Supabase Edge Function (Deno/TypeScript)
  → WhatsApp Business API (Meta Cloud API)
  → Mensaje al teléfono del usuario/admin
```
**Lo que falta en el modelo de datos:** campo `telefono` en `UsuarioEntity` o `EmpresaEntity` — migración menor de DB, se puede agregar en Fase 5 o 6.
**Proceso administrativo Meta (iniciar ahora, en paralelo):**
- Crear Meta Business Account verificada
- Solicitar acceso a WhatsApp Business API (Cloud API)
- Asignar número de teléfono dedicado (no puede ser número personal activo en WhatsApp)
- Aprobación tarda entre 1 y 4 semanas — conviene iniciar durante Fases 5-6
**Alternativa sin aprobación Meta:** `Intent` de Android que abre WhatsApp con texto pre-llenado — requiere interacción manual del usuario, no es automático.
**Consideración de costos:** WhatsApp Business API cobra por conversación iniciada por la empresa (template messages). Evaluar volumen esperado de alertas antes de producción.

---

### 🏗️ PLAN FASE 8 — Modelo de dominio rico + Atributos personalizables (rama: `dev-rich-domain`)

**Objetivo:** Reemplazar los objetos anémicos de dominio (`ProductoConStock`, uso directo de `BodegaEntity` y `UsuarioEntity` en UI) por objetos ricos con comportamiento propio. Sentar la base para atributos personalizables por empresa.

**Principio guía:** Las entidades Room (`*Entity`) son contratos de persistencia — nunca deben salir de la capa `data/`. Los objetos de dominio son los contratos de negocio — son lo único que la capa `ui/` conoce.

**Estrategia de migración sin romper el build en cada sesión:**
- `ProductoConStock` se mantiene como tipo interno de la capa de datos (Room lo necesita para los `@Query` con JOIN). Se agrega `.toDomain(): Producto` y se elimina toda referencia externa en la misma sesión que se actualiza el repositorio + ViewModels.
- `BodegaEntity` se mapea a `Bodega` en `BodegaRepository`. La sesión S2 actualiza el repositorio, ViewModel y pantalla en un solo commit.
- `UsuarioEntity` se mapea a `Usuario` en `UsuarioRepository`. Sin breaking changes — el ViewModel de bodegas simplemente migra de `sesion.rol` a `usuario.esAdmin()`.

---

#### S1 — `Usuario` domain object + `UsuarioRepository`
**Archivos a crear:**
- `domain/model/Usuario.kt`:
  ```kotlin
  data class Usuario(
      val id: String,
      val nombre: String,
      val email: String,
      val rol: Rol,
      val empresaId: String
  ) {
      fun esAdmin(): Boolean = rol == Rol.ADMIN
      fun puedeGestionarBodegas(): Boolean = esAdmin()
      fun puedeEliminarProductos(): Boolean = esAdmin()
      fun puedeRegistrarMovimientos(): Boolean = true
  }
  ```
- `data/repository/UsuarioRepository.kt`:
  - `observarUsuarioActual(): Flow<Usuario?>` — combina `authSessionDao.observarSesion()` con `usuarioDao.obtenerPorId(sesion.user_id)`; usa `sesion.rol` como fuente autoritativa del rol (leído en login, más confiable que `UsuarioEntity.rol`)
  - `obtenerUsuarioActual(): Usuario?` — versión suspend

**Archivos a modificar:**
- `ui/bodegas/BodegaViewModel.kt` — inyectar `UsuarioRepository`; reemplazar `Rol.fromString(sesion?.rol ?: "OPERADOR") == Rol.ADMIN` por `usuario.esAdmin()`

**Breaking changes:** ninguno — es additive. El ViewModel de bodegas mejora internamente.
**DoD:** Compila. `BodegaViewModel` obtiene `esAdmin` desde `UsuarioRepository`, no desde la sesión directamente.
**Commit:** `feat: Phase 8 S1 — Usuario domain object and UsuarioRepository`

---

#### S2 — `Bodega` domain object
**Archivos a crear:**
- `domain/model/Bodega.kt`:
  ```kotlin
  data class Bodega(
      val id: String,
      val nombre: String,
      val ubicacion: String?,
      val empresaId: String,
      val esActiva: Boolean = false
  ) {
      fun descripcion(): String = ubicacion?.let { "$nombre — $it" } ?: nombre
  }
  ```

**Archivos a modificar:**
- `data/repository/BodegaRepository.kt`:
  - `observarBodegas()` retorna `Flow<List<Bodega>>` — el repositorio setea `esActiva` comparando `bodega.id == sesion.bodega_id`
  - `obtenerBodegaActiva()` retorna `Bodega?`
  - `eliminar(id)` recibe `String` — sin cambios en firma
  - `cambiarBodegaActiva(bodegaId)` — sin cambios en firma
  - Agregar extensión privada `BodegaEntity.toDomain(bodegaActivaId: String): Bodega`
- `ui/bodegas/BodegaViewModel.kt` — `BodegasUiState.Listo` usa `List<Bodega>` y `Bodega?` en lugar de `List<BodegaEntity>` y `BodegaEntity?`
- `ui/bodegas/BodegasScreen.kt` — usa `Bodega` en lugar de `BodegaEntity`; llama `bodega.descripcion()` en lugar de construcción manual

**Breaking changes:** internos a la feature de bodegas — todo se actualiza en el mismo commit.
**DoD:** Compila. `BodegasScreen` usa `Bodega`. El cálculo de `esActiva` vive en el repositorio.
**Commit:** `feat: Phase 8 S2 — Bodega domain object, BodegaRepository returns Bodega`

---

#### S3 — `Producto` domain object + repositorio + ViewModels
**Archivos a crear:**
- `domain/model/Producto.kt`:
  ```kotlin
  data class Producto(
      val id: String,
      val nombre: String,
      val descripcion: String?,
      val sku: String?,
      val precio: Int,
      val stockMinimo: Int,
      val stockActual: Int,
      val bodegaId: String,
      val empresaId: String,
      val atributos: Map<String, String> = emptyMap()
  ) {
      fun esBajoStock(): Boolean = stockActual < stockMinimo
      fun valorInventario(): Int = precio * stockActual
      fun ratioStock(): Float = if (stockMinimo > 0) stockActual.toFloat() / stockMinimo else 1f
      fun tieneStock(): Boolean = stockActual > 0
      fun descripcionCompleta(): String = listOfNotNull(descripcion, sku?.let { "SKU: $it" }).joinToString(" · ")
  }
  ```

**Archivos a modificar:**
- `domain/model/ProductoConStock.kt` — agregar `fun toDomain(): Producto` (se mantiene como tipo Room interno)
- `data/repository/ProductoRepository.kt`:
  - `observarProductos(bodegaId)` retorna `Flow<List<Producto>>`
  - `observarBajoMinimo(bodegaId)` retorna `Flow<List<Producto>>`
  - `observarProducto(productoId)` retorna `Flow<Producto?>`
  - Mapeo interno: `ProductoConStock.toDomain()` en cada colección
- `ui/productos/ProductoViewModel.kt` — usa `Producto` en `ProductosUiState`
- `ui/alertas/AlertasViewModel.kt` — usa `Producto` en `AlertasUiState`
- `ui/movimientos/MovimientoViewModel.kt` — usa `Producto`

**Breaking changes:** repositorio + 3 ViewModels — todo en un commit.
**DoD:** Compila. ViewModels exponen `Producto`. `ProductoConStock` solo existe dentro de `data/`.
**Commit:** `feat: Phase 8 S3 — Producto domain object, repository and ViewModels migrated`

---

#### S4 — Actualizar UI de productos
**Archivos a modificar:**
- `ui/productos/ProductosListScreen.kt` — usa `Producto`; reemplaza checks manuales por `producto.esBajoStock()`, `producto.valorInventario()`
- `ui/alertas/AlertasScreen.kt` — usa `Producto`; `producto.esBajoStock()` para colores
- `ui/movimientos/MovimientosScreen.kt` — usa `Producto`

**Breaking changes:** ninguno — ViewModels ya retornan `Producto` desde S3.
**DoD:** Compila. Las 3 pantallas usan métodos del dominio en lugar de comparaciones inline.
**Commit:** `feat: Phase 8 S4 — UI screens migrated to Producto domain object`

---

#### S5 — Infraestructura de atributos (Room migration v5→v6)
**Archivos a crear:**
- `domain/model/AtributoTemplate.kt`:
  ```kotlin
  data class AtributoTemplate(
      val id: String,
      val empresaId: String,
      val clave: String,       // nombre interno: "principio_activo"
      val etiqueta: String,    // nombre visible: "Principio activo"
      val tipo: TipoAtributo,  // TEXT, NUMBER, DATE (MVP: solo TEXT)
      val obligatorio: Boolean,
      val orden: Int
  )
  enum class TipoAtributo { TEXT, NUMBER, DATE }
  ```
- `data/local/entity/AtributoTemplateEntity.kt` + `AtributoTemplateDao.kt`
  - CRUD + `observarPorEmpresa(empresaId): Flow<List<AtributoTemplateEntity>>`
- `data/local/entity/ProductoAtributoEntity.kt` + `ProductoAtributoDao.kt`
  - `(producto_id, template_id, valor)` — PK compuesta
  - `obtenerPorProducto(productoId): List<ProductoAtributoEntity>`
  - `upsertAll(atributos: List<ProductoAtributoEntity>)`

**Archivos a modificar:**
- `data/local/AppDatabase.kt` — versión 6, `MIGRATION_5_6` (dos CREATE TABLE)
- `di/DatabaseModule.kt` — proveer nuevos DAOs
- `data/repository/ProductoRepository.kt` — cargar `atributos: Map<String, String>` en `toDomain()`, combining `ProductoConStock` + `ProductoAtributoDao.obtenerPorProducto()`

**Breaking changes:** ninguno — `Producto.atributos` ya existe como `emptyMap()` por defecto.
**DoD:** Compila. `Producto.atributos` se llena desde Room. Base lista para UI de configuración (Fase 9).
**Commit:** `feat: Phase 8 S5 — AtributoTemplate and ProductoAtributo entities, attributes load in Producto`

---

#### Restricciones globales de la Fase 8
- `*Entity` nunca sale de la capa `data/` — ningún composable recibe una entidad Room
- `ProductoConStock` se mantiene como tipo interno de Room — no se elimina del `ProductoDao`
- `Bodega.esActiva` lo setea el repositorio — nunca la UI ni el ViewModel
- `Usuario.rol` se lee de `AuthSessionEntity` (login value) — no de `UsuarioEntity` (pull value)
- MVP de atributos: solo tipo `TEXT` — `TipoAtributo.NUMBER` y `DATE` existen en el enum pero no en UI

---

### 🧩 Modelo de dominio rico + Atributos personalizables (Fase 8)
**Prerequisito:** ninguno técnico — puede ir después de Fase 7
**Por qué como fase separada:** requiere refactorizar `ProductoConStock` → `Producto` en toda la cadena (repositorios, ViewModels, UI). Es un cambio deliberado que merece su propio espacio.

**Arquitectura objetivo:**
```kotlin
// Dominio rico — reemplaza ProductoConStock
data class Producto(
    val id: String,
    val nombre: String,
    val stockActual: Int,
    val stockMinimo: Int,
    val precio: Int,
    val atributos: Map<String, String> = emptyMap()   // atributos dinámicos por empresa
) {
    fun esBajoStock(): Boolean = stockActual < stockMinimo
    fun valorInventario(): Int = precio * stockActual
    fun ratioStock(): Float = if (stockMinimo > 0) stockActual.toFloat() / stockMinimo else 1f
}
```

**Nuevas entidades Room:**
- `AtributoTemplateEntity` (`id`, `empresa_id`, `clave`, `tipo`, `obligatorio`, `orden`) — define qué campos tiene cada empresa
- `ProductoAtributoEntity` (`producto_id`, `template_id`, `valor`) — valores por producto

**Lo que falta:**
- Migración Room para las dos tablas nuevas
- `ProductoRepository` ensambla `Producto` desde `ProductoEntity` + `ProductoAtributoEntity`
- `ProductosListScreen` y `MovimientosScreen` consumen `Producto` (renombrar referencias)
- Pantalla de configuración de atributos (acceso solo ADMIN, desde Dashboard)
- `ProductoFormDialog` muestra/edita atributos dinámicos según los templates de la empresa

**Decisión de tipos de atributo:**
Valores como `String` en MVP. Tipos futuros: `NUMBER`, `DATE`, `BOOLEAN`, `SELECT` (lista de opciones).

---

### 🗂️ Selección masiva de productos (Fase 7 o 8)
**Prerequisito:** ninguno
**Por qué se difirió:** no existe multi-select en `ProductosListScreen`; agregar checkboxes + barra de acciones masivas es scope significativo.
**Casos de uso:**
- Eliminar varios productos a la vez desde la bodega destino tras una transferencia
- Mover productos entre bodegas en bloque
**Referencia:** surgió como necesidad al diseñar eliminación segura de bodegas con productos.

---

### 🌐 Dashboard web
**Prerequisito:** Fase 5 (Sync) completa
**Por qué esperar:** el dashboard web consume datos de Supabase; sin Sync los datos están desactualizados o vacíos. Una vez que Sync funciona, los datos ya están en Supabase con RLS activa — el backend es gratuito.
**Stack sugerido:** Next.js + Supabase JS client (misma base de datos, misma RLS, mismo JWT)
**Lo que el backend ya ofrece sin trabajo extra:**
- Autenticación via Supabase Auth
- Filtrado por empresa via RLS (`get_empresa_id()`)
- Tablas: empresas, bodegas, productos, movimientos — todas con datos reales post-Sync
**Consideración:** definir qué rol puede acceder al dashboard web (¿solo ADMIN?, ¿también operadores de bodega?)

---

## 🧪 HISTORIAL DE PRUEBAS FÍSICAS

Registro de validaciones en dispositivos reales. Incluye resultado, dispositivo y bugs encontrados.

---

### Fase 1 — Auth
**Dispositivo:** no registrado
**Resultado:** login ✅ registro ✅
**Bugs encontrados:** ninguno

---

### Fase 2 — Productos CRUD
**Dispositivo:** no registrado
**Resultado:** crear ✅ stock inicial ✅ editar ✅ eliminar ✅ buscar ✅ snackbar ✅ duplicado ✅
**Bugs encontrados:** ninguno

---

### Fase 3 — Movimientos
**Dispositivo:** no registrado
**Resultado:** entrada ✅ salida ✅ ajuste ✅ historial ✅ navegación ✅
**Bugs encontrados:** ninguno

---

### Fase 4 — Alertas
**Dispositivo:** no registrado
**Resultado:** tarjeta dashboard ✅ pantalla detalle ✅ empty state ✅ navegación a movimientos ✅
**Bugs encontrados:** ninguno

---

### Fase 5A — Sync push
**Dispositivo:** no registrado
**Resultado:** crear ✅ editar ✅ eliminar ✅ movimiento ✅ offline→online ✅
**Bugs encontrados (corregidos en misma sesión):**
- Logout limpiaba solo `auth_sessions`, no toda la DB → datos de empresa A visibles a empresa B
- `clearAllTables()` en hilo principal → crash
- Faltaba `ksp("androidx.hilt:hilt-compiler:1.2.0")` → HiltWorkerFactory devolvía null

---

### Fase 5B — Sync pull (primera ronda, 2026-04-26)
**Dispositivo:** Samsung Galaxy S25 FE (dispositivo nuevo, sin datos locales)
**Cuentas probadas:** 2 cuentas de empresas distintas
**Resultado:**
- Login: ✅ funciona
- Carga de datos existentes en Supabase: ❌ no aparecen datos

**Bugs encontrados (pendiente re-validar tras fix):**
1. `EmpresaDto.rut: String` no-nullable → `registrar_empresa` no recibe `p_rut`, campo es `null` en Supabase → `SerializationException` en primer pull → short-circuit detiene todos los pulls restantes → `Result.retry()` indefinido. **Fix: `rut: String? = null`, `rut ?: ""`**
2. `PullTrigger` usaba `ExistingWorkPolicy.KEEP` → race condition: si `PullWorker` de `onCreate()` aún en estado `ENQUEUED` al completar login, el segundo enqueue se ignora → primer worker corre sin sesión → `Result.success()` sin datos. **Fix: `ExistingWorkPolicy.REPLACE`**

**Segunda ronda (2026-04-26) — tras fix precio:**
- Mismo resultado: login ✅, productos no aparecen ❌
- Confirmado via Supabase MCP logs: PullWorker SÍ ejecuta, SÍ llega a Supabase, todos los GETs retornan 200
- El bug NO está en la red ni en la autenticación
- El bug debe estar entre el `upsertAll` y la UI — desconocido al cierre de sesión

**Fixes aplicados en sesión de diagnóstico Fase 5B (2026-04-26) — bug resuelto:**

Fix 6 — Plugin `kotlin("plugin.serialization")` faltante en `build.gradle.kts`
- Sin el plugin, `@Serializable` en los DTOs de `PullDtos.kt` compila pero no genera serializers en runtime
- Error en Logcat: `SerializationException: Serializer for class 'ProductoDto' is not found`
- Fix: agregar alias `kotlin-serialization` en `libs.versions.toml` y `alias(libs.plugins.kotlin.serialization)` en `app/build.gradle.kts`
- Nota: el Fix 5 anterior (`precio: String`) era una diagnosis incorrecta del mismo error del plugin

Fix 7 — `precio: Double` en `ProductoDto` vs `20000.00` (número JSON)
- Con el plugin aplicado, Supabase retorna `"precio":20000.00` — número JSON, no string
- `precio: String` (Fix 5) fallaba; `precio: Double` funciona, luego `.toInt()` en `toEntity()`
- Se cambió `precio` a `Int` en toda la cadena: entity, domain model, repository, viewmodel, UI
- Columna Supabase migrada de `numeric(12,2)` a `integer` via MCP
- Room migración 3→4 agregada (recreación de tabla con FK constraints — sin DEFAULT en columnas)

**Resultado:** inventario visible por cuenta en dispositivo físico (Samsung Galaxy S25 FE) ✅

---

### Fase 5B — Sync pull (validación final, 2026-04-26)
**Dispositivo:** Samsung Galaxy S25 FE
**Cuentas probadas:** 2 cuentas de empresas distintas en el mismo dispositivo
**Resultado:**
- Login cuenta Sara: ✅
- Inventario de Sara visible tras login: ✅
- Login cuenta segunda empresa: ✅
- Inventario separado por empresa (aislamiento multi-tenant): ✅
- Datos en Supabase coinciden con lo visible en la app: ✅

**Bugs encontrados:** ninguno (todos resueltos en sesión)

---

## 🧪 HISTORIAL DE TESTS UNITARIOS

Registro de suites de tests automatizados. Se ejecutan con `gradlew.bat test`.

---

### Fase 8 S1 — Usuario domain object + UsuarioRepository

**Archivo:** `test/.../domain/model/UsuarioTest.kt` — 5 tests
**Archivo:** `test/.../data/repository/UsuarioRepositoryTest.kt` — 7 tests
**Resultado:** 12/12 verdes ✅
**Deps agregadas:** `mockk:1.13.12` + `kotlinx-coroutines-test:1.7.3`

Casos cubiertos:
- `esAdmin()` true para ADMIN, false para OPERADOR
- `puedeGestionarBodegas/puedeEliminarProductos` reflejan `esAdmin()`
- `puedeRegistrarMovimientos()` siempre true
- `observarUsuarioActual()` emite null cuando sesión es null
- `observarUsuarioActual()` usa datos de sesión cuando `UsuarioEntity` no existe aún (pre-pull)
- `observarUsuarioActual()` usa nombre/email de `UsuarioEntity` cuando existe
- **Rol de sesión tiene precedencia sobre rol de `UsuarioEntity`** (caso crítico de divergencia)
- OPERADOR → `esAdmin()` false
- `obtenerUsuarioActual()` retorna null sin sesión
- `obtenerUsuarioActual()` retorna usuario correcto con sesión + entity

---

## ✅ INSTRUCCIONES PARA CLAUDE

1. **No asumir** nada que no esté en este archivo o en los contratos pegados
2. **Un archivo por sesión** — si el scope crece, parar y preguntar
3. **Respetar nombres** — español para variables, inglés para commits
4. **No filtrar por empresa_id** en código Kotlin — RLS lo hace
5. **Stock siempre por query** — nunca campo mutable en ProductoEntity
6. **Si hay duda sobre un contrato** — preguntar antes de asumir
7. **Validación física obligatoria entre fases** — al terminar cada fase:
   - Hacer revisión interna de huecos y decisiones
   - Sugerir al usuario qué probar en el dispositivo físico (golden path + edge cases concretos)
   - Preguntar cómo resultaron las pruebas
   - Solo proponer la siguiente fase cuando el usuario confirme que todo está OK
   - Un BUILD SUCCESSFUL no es suficiente para declarar una fase completa