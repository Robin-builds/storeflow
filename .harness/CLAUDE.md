# 🤖 CLAUDE.md — Contexto Estático del Proyecto
> **Uso:** Pegar al inicio de CADA sesión junto con `ESTADO.md`.
> Para sesiones de implementación activa, agregar también `TASKS.md`.
> **Este archivo no cambia salvo upgrades de dependencias o cambios de arquitectura.**

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

**Nombre:** StoreFlow (namespace Kotlin: `cl.storeflow.warehouse` · `applicationId` de publicación: `cl.storeflow.app` desde 15/08/2026 — divergen a propósito, ver `ESTADO.md`)
**Repo:** `https://github.com/Robin-builds/storeflow` (remote `origin`, rama `main`)
**Tipo:** Micro-SaaS de inventario para pequeñas empresas chilenas
**Nota de auth Git:** credential manager puede cachear cuenta `Robinson-dev` (sin acceso de escritura). Si un push falla con 403 → `git credential reject` (protocol=https, host=github.com) y volver a autenticar como `Robin-builds`.
**Working tree:** históricamente hubo cambios extensos sin commitear por normalización de BOM/line-endings (contenido idéntico, no trabajo real). Si reaparecen, no mezclarlos con commits de feature; stagear archivos explícitos, nunca `git add -A`. `.claude/settings.local.json` y `ui-dump.xml` quedan sistemáticamente sin trackear — son ruido local, no feature.

---

## 🛠️ STACK EXACTO (`gradle/libs.versions.toml` + `app/build.gradle.kts`)

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
mockk:               1.13.12
JUnit4:              4.13.2
Espresso:            3.7.0
Kotlin serialization: activo (kotlinx.serialization) — DTOs de sync/pull
```
> Gson **no está** en el proyecto — serialización via `kotlinx.serialization`.

---

## 🏗️ ARQUITECTURA

**Patrón:** Clean Architecture sin capa UseCase explícita.

```
ui/
  auth/          → LoginScreen, RegistroScreen, AuthViewModel
  dashboard/     → DashboardScreen, DashboardViewModel
  productos/     → ProductosListScreen, ProductoViewModel
  movimientos/   → MovimientosScreen, MovimientoViewModel
  alertas/       → AlertasScreen, AlertasViewModel
  bodegas/       → BodegasScreen, BodegaViewModel
  atributos/     → AtributosScreen, AtributoViewModel
  usuarios/      → UsuariosScreen, UsuariosViewModel
  reportar/      → ReportarErrorScreen, ReportarErrorViewModel
  configuracion/ → ConfiguracionScreen
  theme/         → ThemeModels.kt, ThemePreferences.kt, TemaViewModel,
                   Theme.kt, Color.kt, Shape.kt, Spacing.kt, Type.kt
  components/    → BackButton, BarcodeScannerDialog
domain/
  model/         → SesionUsuario, Usuario, Bodega, Producto, ProductoConStock,
                   ProductoConStockYBodega, LoteConStock, AtributoTemplate, Rol, TipoAtributo
data/
  local/
    entity/      → 11 entidades Room
    dao/         → 11 DAOs
    AppDatabase.kt (versión 8)
    DateConverters.kt
  remote/        → SupabaseClient
  sync/          → SyncWorker, PullWorker, SyncTrigger, PullTrigger, SyncPayloads, PullDtos
  repository/    → AuthRepository, ProductoRepository, MovimientoRepository, BodegaRepository,
                   UsuarioRepository, AtributoRepository, TemaRepository, LoteRepository
di/              → AppModule, DatabaseModule
```

**Reglas de arquitectura:**
- **No existe capa UseCase** — lógica de negocio vive en Repositories. No introducir sin pedido explícito.
- `*Entity` nunca sale de `data/` — la UI solo conoce objetos de dominio.
- `ProductoConStock` es tipo interno de Room (JOIN con `movimientos`); `.toDomain()` es el puente.

**Multi-tenancy:** JWT custom claims (`empresa_id` en `app_metadata`) → RLS en Supabase filtra por empresa. El código Kotlin **NO filtra manualmente** por `empresa_id`.

**Roles:** Enum `Rol` (`ADMIN`, `OPERADOR`) — control de acceso solo en UI/Android, no en RLS.
- `rol` persiste en `AuthSessionEntity` (leído de `usuarios` en login).
- Solo ADMIN puede crear/eliminar bodegas y atributos.
- Primer usuario de cada empresa es ADMIN (RPC `registrar_empresa`).

**Auth:** Supabase Auth — Token + `empresa_id` + `bodega_id` + `rol` + `correo` en Room (`auth_sessions`, PK fija `id = 1`).
- Apertura: sesión en Room → Dashboard; sin sesión → Login.
- Registro via RPC `registrar_empresa` (SECURITY DEFINER).
- `checkSession()` valida `expires_at`; auto-refresca con `gotrue.refreshCurrentSession()`; si falla → limpia Room → re-login.
- Logout: `MainActivity.kt` maneja `AuthUiState.SesionCerrada` en `LaunchedEffect` del `NavHost`, navega a `Rutas.LOGIN` con `popUpTo(0)`.

**Decisiones críticas:**
- `PRAGMA foreign_keys = OFF` en `DatabaseModule` — Room es caché offline-first; integridad la garantiza Supabase (evita error 787).
- `MovimientoEntity.nota`: nullable en DB, obligatoria en repositorio. Excepción: stock inicial en `ProductoRepository.crear()` usa `nota = "Stock inicial"` directo al DAO — **no crear dependencia circular con MovimientoRepository**.
- `MovimientoDao.insertar()` usa `OnConflictStrategy.ABORT` — refuerza inmutabilidad de movimientos a nivel DAO.
- `precio: Int` en toda la cadena (CLP sin decimales).
- `Bodega.esActiva` lo setea `BodegaRepository` vía `flatMapLatest` sobre la sesión — nunca la UI.
- `Usuario.rol` se lee de `AuthSessionEntity` (login value, fuente de verdad) — no de `UsuarioEntity`.
- MVP atributos: solo tipo `TEXT`. `NUMBER` y `DATE` existen en enum pero no en UI.
- `UsuarioRepository.eliminar/cambiarRol` usan REST directo (sin `SyncWorker`) — operaciones síncronas críticas.
- `SyncWorker` tiene lógica batch para `producto_atributos`: borra todos del `producto_id` y reinserta.
- **Sistema de temas composable:** paleta (`PaletaAcento`: Forja/Planta/Búnker) × oscuridad (`NivelOscuridad`: Penumbra/Nocturno/Abismo), combinados en runtime vía `crearColorScheme()`. Dark-only. `StoreFlowColoresExtendidos` vía `CompositionLocal`, no prop-drilling. Todos los íconos de acento en toda la app usan `paleta.primario`. Excepción: semáforo de stock (`Rojo600`/`Ambar500`/`Verde400`) — indicador semántico fijo.
- **Lotes/FEFO:** `lote_id` en `MovimientoEntity` es nullable (`SET_NULL`). Salida perecedera usa `registrarSalidaFefo` (privado) que genera un `MovimientoEntity` por lote afectado. Remanente sin cobertura de lotes registra con `lote_id = null` sin bloquear la salida.

---

## 🗄️ MODELO DE DATOS

**AppDatabase versión: 8** (`storeflow.db`, `TypeConverters(DateConverters::class)`)

**11 entidades, 6 niveles de dependencia FK:**
```
Nivel 0: EmpresaEntity
Nivel 1: UsuarioEntity, BodegaEntity, ProveedorEntity, AtributoTemplateEntity (FK → empresa_id CASCADE)
         AuthSessionEntity (fila única, sin FK)
Nivel 2: ProductoEntity (FK → empresa_id + bodega_id CASCADE)
Nivel 3: ProductoAtributoEntity (FK → producto_id + template_id CASCADE, PK compuesta)
         LoteEntity (FK → producto_id + empresa_id CASCADE)
Nivel 4: MovimientoEntity (FK → producto_id CASCADE, lote_id SET_NULL) — INMUTABLE
Nivel 5: SyncEntity (cola de sync, sin FK)
```

**Campos sync en todas las entidades sincronizables:**
```kotlin
val synced: Boolean = false
val synced_at: Date? = null
val created_at: Date = Date()
val updated_at: Date = Date()
```
(`AuthSessionEntity` y `SyncEntity` no llevan `synced`/`synced_at`.)

**Migraciones Room:**
| Migración | Cambio |
|---|---|
| 1→2 | Crea `auth_sessions` |
| 2→3 | `ADD COLUMN bodega_id TEXT NOT NULL DEFAULT ''` en `auth_sessions` |
| 3→4 | Recrea `productos`: `precio` de `REAL` → `INTEGER` |
| 4→5 | `ADD COLUMN rol TEXT NOT NULL DEFAULT 'ADMIN'` en `auth_sessions` |
| 5→6 | Crea `atributo_templates` + `producto_atributos` + índices |
| 6→7 | `ADD COLUMN correo TEXT NOT NULL DEFAULT ''` en `auth_sessions` |
| 7→8 | `ADD COLUMN es_perecedero` en `productos` · crea `lotes` · `ADD COLUMN lote_id TEXT REFERENCES lotes(id) ON DELETE SET NULL` en `movimientos` ⚠️ el `REFERENCES` inline es obligatorio en el `ALTER TABLE` |

**Entidades clave:**
- **ProductoEntity** — `es_perecedero: Boolean = false`. `stock` NO es campo — siempre se calcula.
- **MovimientoEntity** — `lote_id: String? = null` (SET_NULL). INMUTABLE (insert ABORT, nunca UPDATE).
- **LoteEntity** — `fecha_caducidad: Date` + `numero_lote: String?`. Stock por lote calculado via `LoteDao.obtenerConStockFefo` (orden `fecha_caducidad ASC`).
- **AuthSessionEntity** — `id: Int = 1` PK fija. Fila única.

**Regla crítica de stock — nunca almacenar, siempre calcular:**
```sql
SELECT COALESCE(SUM(cantidad), 0) FROM movimientos WHERE producto_id = :id
```

**DAOs destacados:**
- `ProductoDao` — el más grande: `observarConStock`, `observarConStockPorEmpresa` (todas las bodegas — búsqueda global Dashboard), `observarBajoMinimo`, `contarConSku` (unicidad case-insensitive), paginación, transferencia entre bodegas.
- `MovimientoDao` — solo `insertar` (ABORT), nunca UPDATE.
- `LoteDao` — `obtenerConStockFefo` (JOIN movimientos, HAVING stock > 0, orden FEFO) + `observarProximosAVencer`.
- `SyncDao` — `observarPendientes(): Flow<Int>` (badge de pendientes).

---

## 🧩 REPOSITORIES (8, todos `@Singleton`)

- **AuthRepository** — login, registro, `checkSession()` (auto-refresh), logout (limpia todas las tablas Room).
- **ProductoRepository** — CRUD + `observarProductosDeEmpresa(empresaId)` (todas las bodegas, solo lectura — búsqueda global). `crear()` valida SKU único, crea movimiento inicial si aplica, recibe `es_perecedero`.
- **MovimientoRepository** — `registrarEntrada(fechaCaducidad?, numeroLote?)` crea `LoteEntity` si `fechaCaducidad != null`. `registrarSalida` detecta `es_perecedero` y aplica FEFO multi-lote (`registrarSalidaFefo`, privado). Todas validan `nota` no vacía.
- **BodegaRepository** — `observarBodegas()` recalcula `esActiva` reactivamente via `flatMapLatest`.
- **UsuarioRepository** — `eliminar()`/`cambiarRol()` via REST directo, sin cola sync.
- **TemaRepository** — `paletaFlow`/`oscuridadFlow` persistidos en DataStore (dos keys separadas).
- **LoteRepository** — `crear()` retorna el `LoteEntity` (su `id` se usa como `lote_id` del movimiento). `observarProximosAVencer(bodegaId, dias=7)` incluye ya vencidos.

**No existe capa UseCase** — confirmado por auditoría del árbol `app/src/main/java`.

---

## 🔄 SYNC (`data/sync/`)

- **SyncWorker** — push de `sync_queue` a Supabase REST; auto-refresca token expirado; hasta 3 reintentos; batch especial para `producto_atributos`.
- **PullWorker** — GET de 9 tablas → DTOs (`ignoreUnknownKeys=true`) → `upsertAll`. Falla individual no bloquea otras tablas. Se dispara tras login exitoso.
- **Supabase tabla `lotes`** — RLS `ALL` con `USING/WITH CHECK (empresa_id = get_empresa_id())`, FKs CASCADE, índices `idx_lotes_producto`/`idx_lotes_empresa`. Proyecto: `eygbgykglovbivthyqfb` / "StockFlow".

---

## 🧠 VIEWMODELS (10, todos `@HiltViewModel`)

| ViewModel | StateFlows principales |
|---|---|
| **AuthViewModel** | `uiState: StateFlow<AuthUiState>` (`Idle/Cargando/Autenticado/SesionCerrada/Error`) |
| **DashboardViewModel** | `rolActual`, `sinMovimientoReciente`, `countProximosAVencer`, `busquedaGlobal`/`resultadosBusquedaGlobal` (máx. 20, todas las bodegas) |
| **TemaViewModel** | `paletaSeleccionada`, `oscuridadSeleccionada` |
| **AlertasViewModel** | `proximosAVencer: StateFlow<List<LoteProximoAVencer>>` |
| **ProductoViewModel** | `productosFiltrados`, `productosVisibles` (paginado en memoria), `hayMas`, `tamanioPagina`, `templates`, `seleccionados`, `modoSeleccion` (derivado). `busqueda` prellenada via `Rutas.productosConBusqueda()` |
| **MovimientoViewModel** | `registrarEntrada(cantidad, nota, fechaCaducidad?, numeroLote?)` |
| **BodegaViewModel** | `navegarADashboard: SharedFlow<Unit>` |
| **UsuariosViewModel** | `operando: StateFlow<Boolean>` |
| **ReportarErrorViewModel** | `imagenes: StateFlow<List<Uri>>` |
| **AtributoViewModel** / **ConfiguracionScreen** | sin ViewModel propio |

---

## 📱 NAVEGACIÓN (11 pantallas, `MainActivity.kt`)

**Rutas:** `login`, `registro`, `dashboard`, `productos`, `alertas`, `bodegas`, `atributos`, `usuarios`, `configuracion`, `reportar_error`, `movimientos/{productoId}`.
**Start destination:** `login`. Login/Dashboard usan fade; el resto usa slide.
`Rutas.productosConBusqueda()` — argumento opcional, prellenado desde Dashboard.

---

## 🔌 MÓDULOS HILT (`di/`)

- **AppModule** — `provideDataStore()` ("storeflow_datastore").
- **DatabaseModule** — `provideAppDatabase()` (Room + migraciones 1→8 + `fallbackToDestructiveMigrationOnDowngrade()` + FK off) + `@Provides` por cada uno de los 11 DAOs.

---

## 🧪 TESTS (51 totales)

50 unitarios + 1 instrumentado. Stack: JUnit4 + mockk + kotlinx-coroutines-test. Sin Jacoco configurado.

| Archivo | Tests |
|---|---|
| `ProductoTest.kt` | 14 — stock, valorInventario, ratioStock, mapeo |
| `UsuarioTest.kt` | 5 — permisos por rol |
| `BodegaTest.kt` | 3 — descripcion/esActiva |
| `AtributoTemplateTest.kt` | 5 — tipo/obligatorio/orden |
| `ProductoConAtributosTest.kt` | 5 — mapa de atributos |
| `UsuarioRepositoryTest.kt` | 7 — sesión vs entity, precedencia de rol |
| `BodegaRepositoryTest.kt` | 6 — esActiva reactivo |
| `ProductoAtributosFormTest.kt` | 4 — guardar/reemplazar atributos |
| Placeholders | 2 |

⚠️ `ProductoAtributosFormTest.kt` referencia `contarConNombre` (no existe en `ProductoDao`) — falla también en `develop`, es bug preexistente no relacionado a features activas.

---

## 🔐 PATRÓN DE ERRORES

```kotlin
// Repository → Result<T> | ViewModel → StateFlow<UiState> | UI → observa, nunca llama suspend directo
// Cada feature define su propio sealed class: Idle / Cargando / Listo(data) / Guardado / Error(msg)
```

---

## 📐 CONVENCIONES

```
Variables:   español (nombre_producto, no productName)
Comentarios: español
Commits:     inglés semántico (feat:, fix:, refactor:)
Logs:        Timber (no Log.d)
Tablas SQL:  plural minúsculas
PKs:         String UUID (Supabase), excepto auth_sessions (Int fijo = 1)
Textos UI:   español neutro LatAm (público objetivo: Chile) — tuteo tú/usted,
             sin voseo ("tenés", "podés", "vos") ni modismos rioplatenses
             ("acá" → usar "aquí", "dale", "che", "boludo", etc.)
```

---

## 📁 DOCUMENTACIÓN DE REFERENCIA

`C:\Users\Windows 11\Documents\dev\manegenet_inventory_MSaas_v0.0.1\documentacion\`

| Archivo | Contenido |
|---|---|
| `03_DEFINITION_OF_DONE.md` | Checklist por feature — **LEER antes de cada fase** |
| `04_GIT_WORKFLOW.md` | Ramas, commits semánticos, releases |
| `05_SYNC_ALGORITHM_DETAILED.md` | Algoritmo offline-first |
| `IMPLEMENTATION_PLAN.md` | Plan de sesiones atómicas |
| `DEVELOPMENT_LOG.md` | Estado actual del proyecto |
| `HUECOS_Y_SOLUCIONES.md` | Decisiones y problemas resueltos |

---

## ✅ INSTRUCCIONES PARA CLAUDE

1. **No asumir** nada que no esté en este archivo, en el código, o en los contratos pegados.
2. **Un archivo por sesión** — si el scope crece, parar y preguntar.
3. **Respetar nombres** — español para variables, inglés para commits.
4. **No filtrar por empresa_id** en código Kotlin — RLS lo hace.
5. **Stock siempre por query** — nunca campo mutable en `ProductoEntity`.
6. **No crear capa UseCase** — no introducir abstracción nueva sin pedirlo el usuario.
7. **Si hay duda sobre un contrato** — preguntar antes de asumir.
8. **Validación física obligatoria entre fases** — sugerir pruebas en dispositivo, esperar confirmación antes de proponer la siguiente fase; BUILD SUCCESSFUL no es suficiente.
9. **Cada feature va en su propia rama** — `git checkout -b feat/<nombre>` antes de tocar código.
10. **Antes de asumir el estado del working tree** — revisar `git status`.
11. **Antes de escribir un plan, clasificar la feature** (liviana vs compleja — ver criterio abajo). No generar un `docs/plans/*.md` con código completo por paso si la feature es liviana; ejecutar directo.

---

## 🚦 LIVIANA vs COMPLEJA — qué proceso usar

Antes de armar cualquier plan de implementación, evaluar la feature contra estos criterios.

**LIVIANA → implementación directa, sin `docs/plans/*.md`:**
- Sigue un patrón que YA existe en el codebase y se puede señalar su "gemelo" (ej. un método de repository parecido a otro, un diálogo parecido a otro, una Edge Function parecida a otra).
- No requiere migración de Room (no toca la versión de `AppDatabase`).
- No agrega entidades/tablas nuevas ni cambia relaciones N:N existentes.
- No toca contratos de sync (`SyncPayloads`/`PullDtos`) ni políticas RLS.
- Toca como mucho 2-3 capas (ej. Repository + ViewModel + UI) sin abrir preguntas de diseño de datos sin responder.

Si cumple todo lo anterior: anunciar en 2-4 líneas qué se va a hacer (qué cambia, en qué archivos, qué patrón existente se sigue) y pasar directo a implementar — TDD donde ya sea convención (ViewModels), rama propia, validación física, commit semántico. No hace falta pre-escribir el código completo en un doc antes de tocar los archivos reales: eso duplica el trabajo de generar la solución sin aportar seguridad extra cuando el patrón ya está probado en el repo.

**COMPLEJA → sí amerita plan de trabajo completo (`docs/plans/*.md`, con o sin `docs/designs/*.md` previo):**
- Requiere migración de Room / entidad nueva / cambia una relación de datos.
- Hay una decisión de diseño abierta que necesita resolverse con el usuario antes de escribir código (ver ejemplo: Proveedores UI, bloqueada en `TASKS.md` por definir N:N vs FK).
- Toca sync (push/pull) o políticas RLS.
- Afecta múltiples pantallas/flujos, o introduce un patrón nuevo en el codebase que otras features van a replicar después.

Ante la duda, o si la feature mezcla partes livianas con una parte compleja (ej. una migración de schema chica + una UI que sigue un patrón conocido), tratarla como compleja para la parte que lo amerita y ejecutar el resto directo — no es todo o nada.
