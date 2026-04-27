# 🤖 CLAUDE.md — Contexto Persistente del Proyecto
**Pegar al inicio de CADA sesión de implementación.**
**Última actualización:** Abril 2026 — sesión Fase 5B completada

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
**Estado:** Fase 4 completa. Alertas de stock mínimo con tarjeta en Dashboard y pantalla de detalle.

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
FASE 0 (Setup):           ✅ Completa
FASE 1 (Auth):            ✅ Completa
FASE 2 (Productos CRUD):  ✅ Completa
FASE 3 (Movimientos):     ✅ Completa
FASE 4 (Alertas):         ✅ Completa
FASE 5A (Sync push):      ✅ Completa — validada en dispositivo físico
FASE 5B (Sync pull):      ✅ Completa — validada en dispositivo físico (2 cuentas por separado)
FASE 6 (Multi-bodega):    ☐ Pendiente
FASE 7 (Pulido UI):       ☐ Pendiente — puede ir antes del lanzamiento
WHATSAPP (Notif.):        ☐ Pendiente — requiere Fase 5 + aprobación Meta (iniciar trámite ya)
```

**Último commit:**  `1000d61` — fix: security isolation on logout and unblock SyncWorker Hilt injection
**Rama activa:**    `develop` (Fase 5B completa — pendiente commit)
**Próxima sesión:** Fase 6 (Multi-bodega) o Fase 7 (Pulido UI)

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

Fix 5 — `ProductoDto.precio: String` (PostgREST serializa `NUMERIC` como string)
- PostgREST devuelve `"precio": "100000.00"` (string JSON), no número. Confirmado con `execute_sql` via Supabase MCP. `precio: Double` lanzaba `SerializationException` → el worker reintentaba infinitamente pero productos nunca entraban a Room. Fix: `precio: String = "0"` + `precio.toDoubleOrNull() ?: 0.0`

**Lo confirmado via Supabase MCP (herramienta disponible en sesión):**
- 3 empresas, 3 usuarios, 3 bodegas, 5 productos, 7 movimientos en Supabase
- `get_empresa_id()` es SECURITY DEFINER ✅ — RLS correcta
- Logs API Supabase confirman: PullWorker ejecuta todas las tablas, todos los GETs retornan 200
- El bug al cierre de sesión está entre `upsertAll` y la UI — no en red/auth

**Estado al cierre de sesión 2026-04-26:** Fase 5B completa y validada.

**Deuda técnica conocida:**
- Refresh de token JWT: actualmente logout forzado al expirar — refresh completo pendiente
- `SyncPayloads.kt` solo cubre `productos` y `movimientos` — empresas/bodegas/proveedores sin push todavía (se crean vía RPC, no necesitan push por ahora)

---

## 🗺️ ROADMAP DE FEATURES ADICIONALES

Features identificadas fuera del plan original. Cada una tiene su prerequisito y consideraciones clave.

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