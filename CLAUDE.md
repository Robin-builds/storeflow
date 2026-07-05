
# 🤖 CLAUDE.md — Contexto Persistente del Proyecto
**Pegar al inicio de CADA sesión de implementación.**
**Última actualización:** Julio 2026 — Sistema de temas composable (paleta × oscuridad) reemplazando los 4 temas fijos, reestructuración del Dashboard y sección Apariencia en ConfiguracionScreen — validado en dispositivo físico

---

## ⚙️ COMANDOS

```bash
# En Windows usar gradlew.bat (NO ./gradlew — es script Unix)
gradlew.bat assembleDebug         # build APK debug
gradlew.bat installDebug          # instalar en dispositivo
gradlew.bat test                  # unit tests
gradlew.bat connectedAndroidTest  # tests instrumentados
gradlew.bat lint                  # lint
gradlew.bat clean                 # limpiar build
```

---

## 🎯 PROYECTO

**Nombre:** StoreFlow (package: `cl.storeflow.warehouse`)
**Tipo:** Micro-SaaS de inventario para pequeñas empresas chilenas
**Estado:** Fases 0–10 completas. Features adicionales implementadas y validadas en dispositivo físico.
**Rama activa:** `feat/ui-polish` — sin tags publicados. Último commit real en `develop`: `2929f72` feat: paginación "Cargar más" en ProductosListScreen. El sistema de temas composable (paleta × oscuridad) está implementado y **validado en dispositivo físico**, pero todavía sin commitear en esta rama.
**Working tree:** sigue habiendo cambios extensos sin commitear en casi todo el árbol de fuentes (normalización de BOM/line-endings, contenido idéntico — confirmado comparando diffs, no es trabajo real en progreso). No commitear eso junto con features nuevas: al confirmar el commit del sistema de temas, stagear explícitamente solo los archivos de `ui/theme/`, `ui/dashboard/`, `ui/configuracion/ConfiguracionScreen.kt`, `MainActivity.kt`, `data/repository/TemaRepository.kt`, `di/AppModule.kt` y los 6 archivos con colores hardcodeados corregidos (`ProductosListScreen.kt`, `UsuariosScreen.kt`, `BodegasScreen.kt`, `AtributosScreen.kt`, `MovimientosScreen.kt`, `components/BackButton.kt`) — no `git add -A`.

---

## 🛠️ STACK EXACTO (verificado en `gradle/libs.versions.toml` y `app/build.gradle.kts`)

```
Kotlin:              2.0.21
AGP:                 8.13.2
JDK:                 11
Min SDK:             27 (Android 8.1)
Target SDK:          36 (Android 16)
Compile SDK:         36
Compose BOM:         2024.09.00
KSP:                 2.0.21-1.0.28
Room:                2.6.1
Hilt:                2.51.1 + hilt-navigation-compose:1.1.0 + hilt-work:1.2.0
Navigation Compose:  2.7.6
Supabase BOM:        1.4.6  (postgrest-kt, gotrue-kt/auth, realtime-kt)
Ktor Client:         2.3.7  (ktor-client-android)
Coroutines:          1.7.3 + kotlinx-coroutines-test:1.7.3
Lifecycle:           2.10.0
Activity Compose:    1.13.0
Core KTX:            1.18.0
WorkManager:         work-runtime-ktx:2.9.1
DataStore:           datastore-preferences:1.1.1
ML Kit:              barcode-scanning:17.3.0
CameraX:             1.3.4 (core, camera2, lifecycle, view)
Timber:              5.0.1
mockk:               1.13.12  (testImplementation)
JUnit4:               4.13.2
Espresso:             3.7.0
Kotlin serialization: plugin activo (kotlinx.serialization) — usado para DTOs de sync/pull
```

⚠️ **Nota vs. versión anterior de este documento:** Hilt subió de 2.50 → 2.51.1, Lifecycle de 2.7.0 → 2.10.0, Activity Compose de 1.8.1 → 1.13.0. **Gson ya no está en el proyecto** (no aparece en `build.gradle.kts` ni en el código — la serialización usa `kotlinx.serialization`).

---

## 🏗️ ARQUITECTURA

**Patrón:** Clean Architecture (sin capa UseCase explícita — ver más abajo)

```
ui/
  auth/         → LoginScreen, RegistroScreen, AuthViewModel
  dashboard/    → DashboardScreen, DashboardViewModel
  productos/    → ProductosListScreen, ProductoViewModel
  movimientos/  → MovimientosScreen, MovimientoViewModel
  alertas/      → AlertasScreen, AlertasViewModel
  bodegas/      → BodegasScreen, BodegaViewModel
  atributos/    → AtributosScreen, AtributoViewModel
  usuarios/     → UsuariosScreen, UsuariosViewModel
  reportar/     → ReportarErrorScreen, ReportarErrorViewModel
  configuracion/→ ConfiguracionScreen
  theme/        → ThemeModels.kt (PaletaAcento/NivelOscuridad + instancias), ThemePreferences.kt (PaletaId/OscuridadId + DataStore keys),
                   TemaViewModel, Theme.kt (crearColorScheme + StoreFlowColoresExtendidos), Color.kt, Shape.kt, Spacing.kt, Type.kt
  components/   → BackButton, BarcodeScannerDialog
domain/
  model/        → SesionUsuario, Usuario, Bodega, Producto, ProductoConStock, AtributoTemplate, Rol, TipoAtributo
data/
  local/
    entity/     → 10 entidades Room (Empresa, Usuario, Bodega, Proveedor, Producto, Movimiento, Sync,
                   AuthSession, AtributoTemplate, ProductoAtributo)
    dao/        → 10 DAOs (uno por entidad)
    AppDatabase.kt  (versión 7, migraciones 1→2 … 6→7)
    DateConverters.kt
  remote/       → SupabaseClient
  sync/         → SyncWorker, PullWorker, SyncTrigger, PullTrigger, SyncPayloads, PullDtos
  repository/   → AuthRepository, ProductoRepository, MovimientoRepository, BodegaRepository,
                   UsuarioRepository, AtributoRepository, TemaRepository
di/             → AppModule, DatabaseModule
```

**No existe capa UseCase** — la lógica de negocio vive en los Repositories, y los ViewModels los invocan directamente. No introducir UseCases salvo que el usuario lo pida explícitamente (evitar abstracción prematura).

**Multi-tenancy:** JWT custom claims (`empresa_id` en `app_metadata`)
→ RLS en Supabase filtra por empresa automáticamente
→ El código Kotlin NO filtra manualmente por empresa_id

**Roles:** Enum `Rol` (`ADMIN`, `OPERADOR`) — control de acceso solo en UI/Android, no en RLS
→ `rol` persiste en `AuthSessionEntity` (leído de tabla `usuarios` en login)
→ Solo ADMIN puede crear/eliminar bodegas y atributos; OPERADOR solo selecciona bodega activa
→ El primer usuario de cada empresa es ADMIN (asignado por RPC `registrar_empresa`)

**Auth:** Supabase Auth — Token + `empresa_id` + `bodega_id` + `rol` + `correo` guardados en Room (`auth_sessions`, PK fija `id = 1`, fila única)
→ Al abrir app: si hay sesión en Room → Dashboard, si no → Login
→ Registro vía RPC `registrar_empresa` (SECURITY DEFINER): empresa + usuario ADMIN + bodega "Bodega Principal"
→ `checkSession()` valida `expires_at`; auto-refresca con `gotrue.refreshCurrentSession()`; si el refresh falla → limpia Room → re-login
→ **Navegación post-logout ya corregida:** `MainActivity.kt` maneja explícitamente `AuthUiState.SesionCerrada` en el `LaunchedEffect` del `NavHost` y navega a `Rutas.LOGIN` con `popUpTo(0)`. El bug histórico donde cerrar sesión desde ConfiguracionScreen no redirigía al login **está resuelto** — `SesionCerrada` existe en `AuthUiState` y se maneja explícitamente (ya no es una nota de precaución, es el comportamiento actual verificado en código).

**Decisiones críticas:**
- `PRAGMA foreign_keys = OFF` en `DatabaseModule` — Room es caché offline-first; integridad la garantiza Supabase (evita error 787)
- `MovimientoEntity.nota`: nullable en DB, obligatoria en repositorio (`MovimientoRepository` valida `nota` no vacía). Excepción: stock inicial en `ProductoRepository.crear()` usa `nota = "Stock inicial"` directo al DAO — **no crear dependencia circular con MovimientoRepository**
- `MovimientoDao.insertar()` usa `OnConflictStrategy.ABORT` (no REPLACE) — refuerza la inmutabilidad de movimientos a nivel DAO
- `precio: Int` en toda la cadena (CLP sin decimales)
- `*Entity` nunca sale de la capa `data/` — la UI solo conoce objetos de dominio
- `ProductoConStock` se mantiene como tipo interno de Room (JOIN con `movimientos`, `stock_actual` calculado con `SUM(cantidad)`); `.toDomain()` es el puente
- `Bodega.esActiva` lo setea `BodegaRepository` (`observarBodegas()` usa `flatMapLatest` sobre la sesión para recalcular cuál bodega es la activa) — nunca la UI ni el ViewModel
- `Usuario.rol` se lee de `AuthSessionEntity` (login value, fuente de verdad antes de que el `PullWorker` cargue) — no de `UsuarioEntity` (pull value, puede divergir)
- MVP de atributos: solo tipo `TEXT` — `NUMBER` y `DATE` existen en enum `TipoAtributo` pero no en UI
- `UsuarioRepository.eliminar/cambiarRol` usan REST directo (PATCH/DELETE) sin pasar por `SyncWorker` — operaciones síncronas críticas que no admiten cola offline
- **Sistema de temas composable** (`ui/theme/ThemeModels.kt`) — reemplaza los 4 temas fijos anteriores (`TemaApp` enum, eliminado). El acento de color (`PaletaAcento`: Forja/Planta/Búnker) y el nivel de oscuridad (`NivelOscuridad`: Penumbra/Nocturno/Abismo) son dos ejes independientes que se combinan en runtime vía `crearColorScheme(paleta, oscuridad)`. `StoreFlowColoresExtendidos` (gradiente de fondo, glass effect de cards, sombras coloreadas) se expone vía `CompositionLocal` (`StoreFlowTheme.coloresExtendidos`), no como parámetros explícitos — evita prop-drilling. Dark-only, no hay light mode. `TemaRepository` persiste `PaletaId`/`OscuridadId` en `DataStore<Preferences>` (dos keys separadas); ya no hay migración desde `SharedPreferences` legacy (se eliminó junto con `TemaApp`, y `AppModule.provideSharedPreferences` se retiró por quedar sin uso)
- **Todos los íconos de acento en toda la app usan `paleta.primario`** (no solo un componente "principal") — decisión explícita del usuario tras validar en dispositivo: al elegir "Búnker" todo el acento debe verse cian, no una mezcla de cian (Dashboard) + verde hardcodeado (el resto de pantallas). Por eso los FABs de Productos/Usuarios/Bodegas/Atributos, el ícono del escáner y el botón "ver movimientos" en Productos, el segmented button de Movimientos, y el `BackButton` global leen `StoreFlowTheme.coloresExtendidos.paleta.primario` en vez de la constante `Verde700` (eliminada de `Color.kt`). Excepción intencional: el semáforo de stock en `ProductoItem` (`Rojo600`/`Ambar500`/`Verde400` para cero/bajo/normal) se mantiene fijo — es un indicador semántico de severidad, no un color de marca, igual que la alerta siempre roja en las 3 paletas
- `SyncWorker` tiene lógica especial batch para `producto_atributos`: borra todos los del `producto_id` y reinserta, en vez de UPDATE fila por fila
- `AuthSessionDao` es una tabla de fila única (`WHERE id = 1`) — no un historial de sesiones

---

## 🗄️ MODELO DE DATOS

**AppDatabase — versión actual: 7** (`storeflow.db`, `TypeConverters(DateConverters::class)`)

**10 entidades registradas**, 5 niveles de dependencia FK:

```
Nivel 0: EmpresaEntity                                                            (sin FK)
Nivel 1: UsuarioEntity, BodegaEntity, ProveedorEntity, AtributoTemplateEntity      (FK → empresa_id, CASCADE)
         AuthSessionEntity                                                        (fila única, sin FK)
Nivel 2: ProductoEntity                                                           (FK → empresa_id + bodega_id, CASCADE)
Nivel 3: MovimientoEntity (FK → producto_id, CASCADE) — INMUTABLE (insert ABORT, nunca UPDATE)
         ProductoAtributoEntity (FK → producto_id + template_id, CASCADE) — PK compuesta
Nivel 4: SyncEntity                                                               (cola de sync, sin FK)
```

**Campos obligatorios en todas las entidades sincronizables:**
```kotlin
val synced: Boolean = false
val synced_at: Date? = null
val created_at: Date = Date()
val updated_at: Date = Date()
```
(`AuthSessionEntity` y `SyncEntity` son la excepción — no llevan `synced`/`synced_at` porque no se sincronizan como entidad de negocio.)

### Migraciones Room (en `AppDatabase.kt` / `DatabaseModule.kt`)
| Migración | Cambio |
|---|---|
| 1→2 | Crea tabla `auth_sessions` |
| 2→3 | `ALTER TABLE auth_sessions ADD COLUMN bodega_id TEXT NOT NULL DEFAULT ''` |
| 3→4 | Recrea `productos`: `precio` de `REAL` → `INTEGER` (`CAST(precio AS INTEGER)`) |
| 4→5 | `ALTER TABLE auth_sessions ADD COLUMN rol TEXT NOT NULL DEFAULT 'ADMIN'` |
| 5→6 | Crea `atributo_templates` (FK empresa CASCADE) y `producto_atributos` (PK compuesta producto_id+template_id, FK CASCADE a ambos) + índices |
| 6→7 | `ALTER TABLE auth_sessions ADD COLUMN correo TEXT NOT NULL DEFAULT ''` |

### Entidades — campos y FKs

**EmpresaEntity** (`empresas`) — `id: String` PK · `nombre` · `rut` · + campos sync. Sin FK.

**UsuarioEntity** (`usuarios`) — `id: String` PK · `empresa_id` (FK→empresas, CASCADE) · `nombre` · `email` · `rol: String` · + campos sync.

**BodegaEntity** (`bodegas`) — `id: String` PK · `empresa_id` (FK→empresas, CASCADE) · `nombre` · `ubicacion: String?` · + campos sync.

**ProveedorEntity** (`proveedores`) — `id: String` PK · `empresa_id` (FK→empresas, CASCADE) · `nombre` · `contacto: String?` · + campos sync. (Sin UI todavía — solo infraestructura de datos.)

**AtributoTemplateEntity** (`atributo_templates`) — `id: String` PK · `empresa_id` (FK→empresas, CASCADE) · `clave` · `etiqueta` · `tipo: String` · `obligatorio: Boolean` · `orden: Int` · + campos sync. Índice `empresa_id`.

**ProductoEntity** (`productos`) — `id: String` PK · `empresa_id` (FK→empresas, CASCADE) · `bodega_id` (FK→bodegas, CASCADE) · `nombre` · `descripcion: String?` · `sku: String?` · `precio: Int = 0` · `stock_minimo: Int = 0` · + campos sync. Índices `empresa_id`, `bodega_id`. **`stock` NO es un campo — siempre se calcula.**

**MovimientoEntity** (`movimientos`) — `id: String` PK · `producto_id` (FK→productos, CASCADE) · `tipo: TipoMovimiento` (ENTRADA/SALIDA/AJUSTE) · `cantidad: Int` · `nota: String?` · + campos sync. Índice `producto_id`. INMUTABLE.

**ProductoAtributoEntity** (`producto_atributos`) — PK compuesta `(producto_id, template_id)`, ambos FK CASCADE (→productos, →atributo_templates) · `valor: String`. Índices en ambas columnas.

**SyncEntity** (`sync_queue`) — `id: String` PK · `entidad_tipo` · `entidad_id` · `operacion: OperacionSync` (INSERT/UPDATE/DELETE) · `payload: String` (JSON) · `reintentos: Int = 0` · `created_at`/`updated_at`. Sin FK.

**AuthSessionEntity** (`auth_sessions`) — `id: Int = 1` PK fija · `access_token` · `refresh_token` · `user_id` · `empresa_id` · `bodega_id = ""` · `rol = "ADMIN"` · `expires_at: Date` · `correo = ""` · `created_at`/`updated_at`. Sin FK, fila única.

**Regla crítica de stock** — nunca almacenar, siempre calcular:
```sql
SELECT COALESCE(SUM(cantidad), 0) FROM movimientos WHERE producto_id = :id
```

### DAOs — queries principales (10 DAOs, uno por entidad)

- **EmpresaDao / BodegaDao / ProveedorDao / UsuarioDao / AtributoTemplateDao** — patrón CRUD estándar: `insertar` (REPLACE), `actualizar`, `eliminar`, `obtenerPorId`, `observarPorEmpresa(empresaId): Flow<List<...>>`, `upsertAll` (pull), `obtenerNoSincronizados/as` (push). `BodegaDao` además: `obtenerPrimeraParaEmpresa`, `obtenerMasAntiguaExcluyendo` (usado al eliminar bodega, transfiere productos a la más antigua restante), `marcarSincronizado`.
- **ProductoDao** — el más grande: además del CRUD estándar, `contarConSku` (unicidad SKU case-insensitive por empresa), `calcularStock`, `observarConStock(bodegaId)` (JOIN + SUM), `observarProductoConStock`, `observarBajoMinimo` (HAVING stock < stock_minimo), `observarSinMovimientoReciente(bodegaId, desde, limite=3)`, `contarTodos`, `contarPorBodega`, `obtenerListaPorBodega`, `transferirABodega` (bulk UPDATE bodega_id), `obtenerPorIds`, `transferirSeleccionadosABodega`.
- **MovimientoDao** — solo `insertar` (ABORT, nunca UPDATE), `observarPorProducto` (DESC), `marcarSincronizado`, `upsertAll`, `obtenerNoSincronizados`, `contarTodos`.
- **ProductoAtributoDao** — `obtenerClavesValores(productoId)` (JOIN con `atributo_templates` para traer `clave`+`valor`), `upsertAll`, `eliminarPorProducto`.
- **SyncDao** — `encolar`, `eliminar`, `obtenerCola() ORDER BY created_at ASC`, `incrementarReintentos`, `observarPendientes(): Flow<Int>` (badge de pendientes).
- **AuthSessionDao** — `observarSesion`/`obtenerSesion` (`WHERE id = 1`), `guardarSesion` (REPLACE), `actualizarBodegaActiva`, `limpiarSesion`.

---

## 🧩 REPOSITORIES (7, todos `@Singleton`)

- **AuthRepository** — `observarSesion()`, `login()`, `registrar()` (incluye recuperación de cuentas "huérfanas"), `registrarUsuarioEnEmpresa()` (Edge Function), `logout()` (limpia todas las tablas Room), `checkSession()` (auto-refresh), `obtenerRolActual()`.
- **ProductoRepository** — `obtenerContexto()`, `observarProductos/BajoMinimo/SinMovimientoReciente/Producto`, `obtenerPorId`, `crear()` (valida SKU único, crea movimiento inicial si aplica, guarda atributos), `actualizar()`, `eliminar()`, `eliminarVarios()`, `transferirSeleccionados()`.
- **MovimientoRepository** — `observarProducto`, `observarMovimientos`, `registrarEntrada/Salida/Ajuste` (todas validan `nota` no vacía y reglas de cantidad/stock).
- **BodegaRepository** — `observarBodegas()` (recalcula `esActiva` reactivamente), `obtenerBodegaActiva()`, `crear()`, `eliminar()` (transfiere productos a la bodega más antigua restante), `cambiarBodegaActiva()`.
- **UsuarioRepository** — `observarUsuarioActual/UsuariosDeEmpresa`, `obtenerUsuarioActual`, `eliminar()`/`cambiarRol()` (REST directo, sin cola sync), `insertarLocal()` (usado por PullWorker).
- **AtributoRepository** — `observarTemplates()`, `crear()`, `eliminar()`.
- **TemaRepository** — `paletaFlow: Flow<PaletaId>`, `oscuridadFlow: Flow<OscuridadId>`, `setPaleta()`, `setOscuridad()` (persistidos en DataStore, dos keys separadas).

**No existe capa UseCase** — confirmado por auditoría del árbol completo `app/src/main/java`. No crear una salvo pedido explícito.

---

## 🔄 SYNC (`data/sync/`)

- **SyncWorker** (HiltWorker) — push de `sync_queue` a Supabase REST; auto-refresca token expirado; hasta 3 reintentos, luego descarta; lógica batch especial para `producto_atributos` (delete-all + re-insert).
- **PullWorker** (HiltWorker) — GET de 8 tablas (empresas, bodegas, proveedores, usuarios, productos, movimientos, atributo_templates, producto_atributos) → DTOs (`ignoreUnknownKeys=true`) → `upsertAll`. Falla individual no bloquea las demás tablas.
- **SyncTrigger / PullTrigger** — encolan sus Workers vía WorkManager (`ExistingWorkPolicy.REPLACE`, constraint `CONNECTED`). `PullTrigger` se dispara tras login exitoso.

---

## 🧠 VIEWMODELS (10, todos `@HiltViewModel`)

| ViewModel | Depende de | StateFlows principales | UiState |
|---|---|---|---|
| **AuthViewModel** | AuthRepository | `uiState: StateFlow<AuthUiState>` | `Idle / Cargando / Autenticado / SesionCerrada / Error(msg)` |
| **DashboardViewModel** | AuthSessionDao, ProductoRepository | `nombreUsuario`, `rolActual: StateFlow<Rol>`, `sinMovimientoReciente: StateFlow<List<Producto>>` | — |
| **TemaViewModel** | TemaRepository | `paletaSeleccionada: StateFlow<PaletaId>`, `oscuridadSeleccionada: StateFlow<OscuridadId>`, `cambiarPaleta()`, `cambiarOscuridad()` | — |
| **AlertasViewModel** | ProductoRepository, BodegaRepository | `uiState`, `bodegaNombre` | `Cargando / Listo(alertas)` |
| **ProductoViewModel** | ProductoRepository, AtributoRepository, BodegaRepository | `uiState`, `formState`, `busqueda`, `productosFiltrados`, `productosVisibles` (paginado en memoria, "Cargar más"), `hayMas`, `tamanioPagina`, `templates`, `productoEditando`, `seleccionados: StateFlow<Set<String>>`, `modoSeleccion` (derivado), `bodegas` | `ProductosUiState{Cargando/Listo/Error}` + `FormUiState{Idle/Cargando/Guardado/Error}` |
| **BodegaViewModel** | BodegaRepository, UsuarioRepository | `uiState`, `navegarADashboard: SharedFlow<Unit>`, `mensaje: SharedFlow<String>` | `Cargando / Listo(bodegas, activa, esAdmin) / Error` |
| **AtributoViewModel** | AtributoRepository, UsuarioRepository | `uiState`, `mensaje: SharedFlow<String>` | `Cargando / Listo(templates, esAdmin) / Error` |
| **MovimientoViewModel** | MovimientoRepository, SavedStateHandle (`productoId`) | `uiState`, `formState` | `Cargando / Listo(producto, movimientos) / Error` + `MovFormState{Idle/Cargando/Guardado/Error}` |
| **UsuariosViewModel** | UsuarioRepository, AuthRepository | `uiState`, `mensaje: SharedFlow<String>`, `operando: StateFlow<Boolean>` | `Cargando / Listo(usuarios, usuarioActualId) / Error` |
| **ReportarErrorViewModel** | — (sin deps) | `imagenes: StateFlow<List<Uri>>`, `descripcion: StateFlow<String>` | — |

---

## 📱 SCREENS Y NAVEGACIÓN (11 pantallas, `MainActivity.kt`)

**Rutas (`object Rutas`):** `login`, `registro`, `dashboard`, `productos`, `alertas`, `bodegas`, `atributos`, `usuarios`, `configuracion`, `reportar_error`, `movimientos/{productoId}` (helper `Rutas.movimientos(id)`).
**Start destination:** `login`. Login/Dashboard usan fade; el resto usa slide.

| Ruta | Screen | Notas |
|---|---|---|
| `login` | LoginScreen | email + password |
| `registro` | RegistroScreen | empresa + rubro + email + password (8+) |
| `dashboard` | DashboardScreen | agrega 5 ViewModels vía `hiltViewModel()`: alertas, bodega, producto, atributo, usuarios |
| `configuracion` | ConfiguracionScreen | soporte, dashboard web, apariencia (paleta+oscuridad, en su propia card al final), cerrar sesión (fondo rojo sólido + texto/ícono blanco) |
| `reportar_error` | ReportarErrorScreen | selector de imágenes (maneja permisos <10/10+/13+) |
| `alertas` | AlertasScreen | productos bajo stock mínimo + compartir |
| `productos` | ProductosListScreen | CRUD, atributos dinámicos, selección masiva, escáner, compartir |
| `bodegas` | BodegasScreen | cambiar/crear/eliminar bodega (ADMIN) |
| `atributos` | AtributosScreen | templates de atributos (ADMIN) |
| `usuarios` | UsuariosScreen | gestión de usuarios de la empresa (ADMIN) |
| `movimientos/{productoId}` | MovimientosScreen | tabs ENTRADA/SALIDA/AJUSTE |

Navegación post-login/logout centralizada en `MainActivity` vía `LaunchedEffect(uiState)` sobre `AuthUiState` (ver sección Auth arriba).

---

## 🔌 MÓDULOS HILT (`di/`)

- **AppModule** (`@InstallIn(SingletonComponent::class)`) — `provideSharedPreferences()` (legacy, "storeflow_prefs"), `provideDataStore()` ("storeflow_datastore").
- **DatabaseModule** (`@InstallIn(SingletonComponent::class)`) — `provideAppDatabase()` (Room + migraciones 1→7 + FK off) y un `@Provides` por cada uno de los 10 DAOs.

---

## 🧪 TESTS

**51 tests totales** — 50 unitarios (`app/src/test/`) + 1 instrumentado (`app/src/androidTest/`).

| Archivo | # @Test | Capa |
|---|---|---|
| `domain/model/ProductoTest.kt` | 14 | dominio — stock, valorInventario, ratioStock, descripcionCompleta, mapeo ProductoConStock |
| `domain/model/UsuarioTest.kt` | 5 | dominio — permisos por rol |
| `domain/model/BodegaTest.kt` | 3 | dominio — descripcion/esActiva |
| `domain/model/AtributoTemplateTest.kt` | 5 | dominio — tipo/obligatorio/orden |
| `domain/model/ProductoConAtributosTest.kt` | 5 | dominio — mapa de atributos |
| `data/repository/UsuarioRepositoryTest.kt` | 7 | repositorio — sesión vs entity, precedencia de rol |
| `data/repository/BodegaRepositoryTest.kt` | 6 | repositorio — esActiva reactivo por sesión |
| `data/repository/ProductoAtributosFormTest.kt` | 4 | repositorio — guardar/reemplazar atributos al crear/editar |
| `ExampleUnitTest.kt` | 1 | placeholder |
| `ExampleInstrumentedTest.kt` (androidTest) | 1 | placeholder (packageName) |

Sin Jacoco/coverage configurado. Stack de test: JUnit4 + mockk + kotlinx-coroutines-test.

---

## 🔐 PATRÓN DE ERRORES

```kotlin
// Repository → Result<T> | ViewModel → StateFlow<UiState> | UI → observa, nunca llama suspend directo
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```
(En la práctica cada feature define su propio sealed class específico — ver tabla de ViewModels arriba — pero todos siguen esta forma Idle/Cargando/Listo·Guardado/Error.)

---

## 📐 CONVENCIONES

```
Variables:   español (nombre_producto, no productName)
Comentarios: español
Commits:     inglés semántico (feat:, fix:, refactor:)
Logs:        Timber (no Log.d)
Tablas SQL:  plural minúsculas (empresas, productos, movimientos)
PKs:         String UUID (viene de Supabase), excepto auth_sessions (Int fijo = 1)
```

---

## 📁 DOCUMENTACIÓN DE REFERENCIA

`C:\Users\Windows 11\Documents\dev\manegenet_inventory_MSaas_v0.0.1\documentacion\`

```
03_DEFINITION_OF_DONE.md      → Checklist por feature (LEER antes de cada fase)
04_GIT_WORKFLOW.md             → Ramas, commits semánticos, releases
05_SYNC_ALGORITHM_DETAILED.md → Algoritmo offline-first
IMPLEMENTATION_PLAN.md         → Plan de sesiones atómicas
DEVELOPMENT_LOG.md             → Estado actual del proyecto
HUECOS_Y_SOLUCIONES.md        → Decisiones y problemas resueltos
```

---

## ✨ FEATURES POST-MVP (verificadas en código)

- 📷 **Escaneo QR/Barcode** — `ui/components/BarcodeScannerDialog.kt` (ML Kit + CameraX), botón "Escanear" en campo SKU
- 🗂️ **Selección masiva** — `ProductoViewModel`: `seleccionados: StateFlow<Set<String>>`, `modoSeleccion` derivado, `eliminarSeleccionados()`, `transferirSeleccionados()`; overlay card en `ProductosListScreen`
- 🎨 **Atributos dinámicos** — `AtributoTemplateEntity`/`ProductoAtributoEntity`, `AtributoRepository`, `AtributosScreen` (ADMIN), formulario de producto con campos dinámicos (solo tipo TEXT en UI)
- 🌗 **Temas composables** — paleta de acento (`PaletaId`: Forja/Planta/Búnker) × nivel de oscuridad (`OscuridadId`: Penumbra/Nocturno/Abismo), combinados en runtime vía `crearColorScheme()`. Reemplaza el antiguo `enum TemaApp` (4 temas fijos, eliminado). Persistido en DataStore vía `TemaRepository` (dos keys). Selector visual en ConfiguracionScreen (cards con swatches / gradiente). Dashboard reestructurado: header invertido (bodega grande, marca discreta, usuario+rol en `primarioSuave`), cards con glass effect, todos los íconos de acento (Dashboard y el resto de pantallas) usan `paleta.primario` — validado en dispositivo físico en Búnker
- 💬 **Compartir stock / dashboard web** — intents `ACTION_SEND` desde `AlertasScreen`, `ProductosListScreen` y `ConfiguracionScreen`
- 🐛 **Reportar error** — `ReportarErrorScreen`/`ReportarErrorViewModel`, selector de imágenes multi-versión Android
- 👥 **Gestión de usuarios (ADMIN)** — `UsuariosScreen`/`UsuariosViewModel`, Edge Function `registrar-usuario-empresa`
- 🌐 **Dashboard web** — proyecto Next.js separado (`stockflow-web`), en producción: `https://stockflow-web-eight.vercel.app`
- 📄 **Paginación "Cargar más" en Productos** — `ProductoViewModel`: `productosVisibles`, `hayMas`, `tamanioPagina` (25/50/100 seleccionable), `cargarMas()` suma el siguiente bloque a lo ya visible (acumulativo, no reemplaza). Paginación **en memoria** — `ProductoDao.observarConStock()` no cambió, sigue sin `LIMIT`. Búsqueda resetea la ventana visible al tamaño base. "Seleccionar todos" ahora opera sobre `productosVisibles` (lo cargado hasta el momento), no sobre el total — decisión consciente para no seleccionar miles de productos invisibles de una vez. "Compartir inventario" no cambió, sigue compartiendo el total filtrado. Validada en dispositivo físico.

---

## 📊 ESTADO ACTUAL (histórico de fases)

```
FASE 0 (Setup):                       ✅ Completa
FASE 1 (Auth):                        ✅ Completa
FASE 2 (Productos CRUD):              ✅ Completa
FASE 3 (Movimientos):                 ✅ Completa
FASE 4 (Alertas):                     ✅ Completa
FASE 5A (Sync push):                  ✅ Completa — validada en dispositivo físico
FASE 5B (Sync pull):                  ✅ Completa — validada en dispositivo físico (2 cuentas)
FASE 6 (Multi-bodega + Roles):        ✅ Completa — validada en dispositivo físico
FASE 7 (Pulido UI):                   ✅ Completa — validada en dispositivo físico
FASE 8 S1-S5 (Dom. rico + Atributos): ✅ Completa — tests de dominio verdes
FASE 9 S1 (Config. atributos UI):     ✅ Completa — validada en dispositivo físico
FASE 9 S2 (Form. producto atributos): ✅ Completa — validada
FASE 9 S3 (Sync push atributos):      ✅ Completa — validada en dispositivo físico
FASE 9 S4 (Pull atributos):           ✅ Completa — validada en 2 dispositivos (sync demostrado)
FASE 10 S1 (Reg. usuario en empresa): ✅ Completa — Edge Function deployada + AuthRepository
FASE 10 S2 (UsuariosScreen ADMIN):    ✅ Completa — validada en 2 dispositivos físicos
ESCANEO QR/Barcode (SKU):             ✅ Completa — validada en dispositivo físico
SELECCIÓN MASIVA:                     ✅ Completa (overlay card) — validada en dispositivo físico
COMPARTIR STOCK (WhatsApp/share):     ✅ Completa — validada en dispositivo físico
BÚSQUEDA POR SKU/BARCODE:             ✅ Completa — validada en dispositivo físico
LOGIN UX TECLADO:                     ✅ Completo — form sobre centro + imePadding
RENOMBRADO StockFlow→StoreFlow:       ✅ Completo — package cl.storeflow.warehouse (verificado: no quedan referencias a cl.stockflow)
SISTEMA DE TEMAS COMPOSABLE:           ✅ Completo — paleta (Forja/Planta/Búnker) × oscuridad (Penumbra/Nocturno/Abismo), reemplaza TemaApp, Dashboard reestructurado, íconos de acento unificados en toda la app — validado en dispositivo físico
REPORTAR PROBLEMA:                    ✅ Screens + ViewModel presentes y wireados en MainActivity/ConfiguracionScreen (código actual ya no muestra el re-wiring pendiente descrito en versiones previas de este doc)
CARD ALERTAS EN DASHBOARD:            ✅ presente en código actual (DashboardScreen consume AlertasViewModel; ya no aparece como rama separada sin commit)
NOMBRES DUPLICADOS + DESCRIPCIÓN:     ✅ Completo — SKU único por empresa (case-insensitive, vía ProductoDao.contarConSku), nombres pueden repetirse
CONFIGURACION SCREEN (cards):         ✅ Completo — Tema / Soporte / Dashboard web, cerrar sesión anclado al fondo
DASHBOARD WEB:                        ✅ En producción — https://stockflow-web-eight.vercel.app
COMPARTIR DASHBOARD:                  ✅ Completo — botón en ConfiguracionScreen con share intent
PAGINACIÓN "CARGAR MÁS" (Productos):  ✅ Completo — Android, validada en dispositivo físico (ver Features post-MVP)
```

**Proyecto web:** `C:\Users\Windows 11\Documents\dev\stockflow-web`
→ Stack: Next.js · TypeScript · Tailwind · @supabase/ssr
→ Páginas: login, dashboard (resumen), productos, movimientos, **productos/importar** (nuevo)
→ Productos: filtro por bodega, orden Nombre/Precio/Stock/Alerta, exportar CSV (BOM UTF-8)
→ Movimientos: paginación 25/página, orden por columna, **búsqueda por nombre de producto (`?q=`, nuevo)**, badge de tipo ENTRADA/SALIDA/AJUSTE corregido (antes se deducía solo del signo de `cantidad`, ignorando AJUSTE)
→ **Importación masiva de productos por CSV (nuevo, rama `feat/importar-productos-csv`, sin mergear)** — plantilla descargable con atributos dinámicos de la empresa, preview/validación de errores antes de escribir, commit en lotes de 300 vía `supabase-js` desde el navegador (sin RPC — ver rama para el análisis de por qué). Alcance: solo creación, no actualiza productos existentes.
→ Fix: `utils/getRol.ts` consultaba una columna `usuarios.user_id` que no existe (la PK `id` ya es el uid de auth) — el rol siempre caía a OPERADOR y ocultaba "Exportar CSV"/"Importar CSV" incluso para ADMIN. Corregido en `feat/historial-movimientos` e `feat/importar-productos-csv` (ambas ramas, sin mergear a `master` todavía).
→ **Paginación real (`.range()`) en `/dashboard/productos` — pospuesta a pedido del usuario.** Queda con las mismas limitaciones de antes (carga todo sin paginar). Si se retoma: el stock no es columna real (se calcula sumando `movimientos.cantidad` en JS), así que ordenar/paginar por stock a nivel servidor requeriría una vista SQL en Supabase — no se ha creado.
→ **Ramas sin mergear a `master`:** `feat/historial-movimientos` (búsqueda + fix badge tipo + fix getRol, commiteada), `feat/importar-productos-csv` (import CSV, en progreso). Mergear con cuidado — ambas tocan `getRol.ts` con el mismo fix, sin conflicto esperado; `feat/importar-productos-csv` también toca `productos/page.tsx`.

---

## 🗺️ ROADMAP — histórico de sesiones atómicas

### Fase 9 — Atributos personalizables (✅ completa)
- **S2** — Formulario dinámico: `ProductoRepository.crear/actualizar` reciben `atributos: Map<String,String>`; `ProductoViewModel` inyecta `AtributoRepository`, expone `templates`/`atributos`; `ProductosListScreen` renderiza un `OutlinedTextField` por template (solo TEXT en MVP), obligatorios bloquean guardado si vacíos.
- **S3** — Sync push: `AtributoRepository.crear/eliminar` encolan en `SyncEntity`; `SyncWorker` procesa contra `atributo_templates` y `producto_atributos`.
- **S4** — Pull: `PullWorker` extendido con GET a ambas tablas, `upsertAll` en sus DAOs.

### Fase 10 — Gestión de usuarios (✅ completa)
**Restricción técnica:** Supabase Admin API no disponible en cliente mobile → Edge Function `registrar-usuario-empresa` con `SUPABASE_SERVICE_ROLE_KEY` server-side.
**Por qué NO RPC:** insertar en `auth.users` directo no setea `app_metadata.empresa_id` → JWT del nuevo usuario sin `empresa_id` → RLS falla silenciosamente.
- **S1** — Edge Function deployada (`supabase/functions/registrar-usuario-empresa/index.ts`): ADMIN llama `AuthRepository.registrarUsuarioEnEmpresa()` → POST con JWT del ADMIN como Bearer → función verifica rol ADMIN → `admin.createUser()` con `app_metadata.empresa_id` → INSERT en `public.usuarios` → rollback si falla.
- **S2** — `UsuariosScreen`/`UsuariosViewModel`: lista, registra, cambia rol, elimina usuarios de la empresa.

---

## 🗺️ Features futuras / pendientes

| Feature | Estado | Notas clave |
|---|---|---|
| 💬 WhatsApp notif. push | ☐ Pendiente | requiere aprobación Meta; Edge Function en Supabase, cero impacto código Android |
| 🔄 JWT refresh | ✅ implementado | `gotrue.refreshCurrentSession()` en `checkSession()`; cold start con token expirado aún requiere re-login |
| 📋 Historial global de movimientos | ✅ Web / ☐ Android | Implementado en el dashboard web (`/dashboard/movimientos`, rama `feat/historial-movimientos` sin mergear): lista global, orden desc por fecha, paginación 25/página, búsqueda por nombre de producto. **La app Android no tiene pantalla equivalente** — `MovimientosScreen` sigue siendo por producto (`movimientos/{productoId}`), no una vista global. |
| Atributos NUMBER/DATE | ☐ Pendiente | existen en enum `TipoAtributo` pero no en UI (MVP solo TEXT) |
| Proveedores UI | ☐ Pendiente | `ProveedorEntity`/`ProveedorDao` existen a nivel de datos pero no hay pantalla ni repositorio expuesto en UI |

---

## 🧪 Últimas pruebas físicas conocidas

| Fase | Dispositivo / resultado | Bugs encontrados |
|---|---|---|
| 5B Sync pull final | Samsung S25 FE, 2 cuentas ✅ | Plugin serialización, precio Double→Int, Room FK migration (resueltos) |
| 8 S3+S4 / 8 S5 / 9 S1-S4 | ADMIN ✅ OPERADOR ✅ crear/eliminar ✅ sync push/pull ✅ | ninguno |
| 10 S2 UsuariosScreen | ADMIN registra OPERADOR ✅ OPERADOR login ✅ rol UI correcto ✅ cambiar rol ✅ eliminar ✅ | ninguno |
| 7 Pulido UI | FABs ✅ cards ✅ BackButton ✅ | ChevronRight no en core icons → reemplazado por KeyboardArrowRight |
| Escaneo QR/Barcode, Selección masiva, Compartir stock, Búsqueda SKU/barcode, Login UX teclado, Fix logout navegación, Nombre usuario en Dashboard, ConfiguracionScreen cards, Compartir Dashboard | ✅ todas validadas en dispositivo físico | ninguno |
| Paginación "Cargar más" en Productos (rama `feat/paginacion-productos`) | ✅ validada en dispositivo físico por el usuario | ninguno — `gradlew.bat test` falla por un test preexistente no relacionado (`ProductoAtributosFormTest.kt` referencia `contarConNombre`, método que no existe en `ProductoDao`; falla también en `develop`, confirmado por diff) |
| Sistema de temas composable (rama `feat/ui-polish`) — paleta×oscuridad, Dashboard reestructurado, íconos de acento unificados en todas las pantallas | ✅ validada en dispositivo físico por el usuario, incluyendo Búnker (cian) en Dashboard y en Productos/Usuarios/Bodegas/Atributos/Movimientos | 2 rondas de ajuste post-validación: (1) botón "Cerrar sesión" con bajo contraste → fondo rojo sólido + texto/ícono blanco; sección Apariencia sin card propia → envuelta en su propia Card y movida al final; (2) solo la card "Productos" del Dashboard tenía el ícono con color de paleta, el resto en gris neutro → unificado, todas las cards usan `paleta.primario` |

---

## ✅ INSTRUCCIONES PARA CLAUDE

1. **No asumir** nada que no esté en este archivo, en el código, o en los contratos pegados
2. **Un archivo por sesión** — si el scope crece, parar y preguntar
3. **Respetar nombres** — español para variables, inglés para commits
4. **No filtrar por empresa_id** en código Kotlin — RLS lo hace
5. **Stock siempre por query** — nunca campo mutable en ProductoEntity
6. **No crear capa UseCase** — la lógica vive en Repository/ViewModel; no introducir abstracción nueva sin pedirlo el usuario
7. **Si hay duda sobre un contrato** — preguntar antes de asumir
8. **Validación física obligatoria entre fases** — sugerir pruebas en dispositivo, esperar confirmación antes de proponer la siguiente fase; BUILD SUCCESSFUL no es suficiente
9. **Cada feature va en su propia rama** — `git checkout -b feat/<nombre>` antes de tocar código; solo trabajar directo en develop si el usuario dice explícitamente "aquí mismo" o "en develop directo"
10. **Antes de asumir el estado del working tree** — revisar `git status`; al momento de esta auditoría había cambios extensos sin commitear en casi todo el árbol de fuentes, confirmar con el usuario qué es intencional antes de descartar o commitear nada
