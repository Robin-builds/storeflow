# 🤖 CLAUDE.md — Contexto Persistente del Proyecto
**Pegar al inicio de CADA sesión de implementación.**
**Última actualización:** Abril 2026

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
**Estado:** Fase 1 completa y verificada end-to-end. Login y registro funcionando en dispositivo físico.

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

**Dependencias pendientes (Fase 1+):**
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
ui/          → Composables, ViewModels
domain/      → UseCases, modelos de negocio
data/
  local/
    entity/  → 7 entidades Room
    dao/     → 7 DAOs
    AppDatabase.kt
    DateConverters.kt
  remote/    → Supabase (Fase 1+)
di/          → DatabaseModule (listo), AuthModule (Fase 1)
utils/       → Extensiones, helpers
```

**Multi-tenancy:** JWT custom claims (`empresa_id` en `app_metadata`)
→ RLS en Supabase filtra por empresa automáticamente
→ El código Kotlin NO filtra manualmente por empresa_id

**Auth:** Supabase Auth (`auth-kt`) — email/password
→ Token se guarda en Room (tabla: `auth_sessions`) — NO en memoria
→ Al abrir app: si hay token válido → Dashboard, si no → Login
→ JWT expirado (401) → limpiar token en Room → redirigir a Login
→ Registro crea en secuencia: empresa → usuario (rol ADMIN) → bodega "Bodega Principal"

**Pendiente agregar en Fase 1:** `AuthSessionEntity` + `AuthSessionDao` a AppDatabase

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
FASE 2 (Productos CRUD):  ☐ Pendiente
FASE 3 (Movimientos):     ☐ Pendiente
FASE 4 (Alertas):         ☐ Pendiente
FASE 5 (Sync):            ☐ Pendiente
```

**Último commit:**  `bff7748` — debug: Add Timber logging to AuthRepository
**Rama activa:**    `develop`
**Próxima sesión:** Fase 2 — Productos CRUD

**Lo construido en Fase 1:**
- `AuthSessionEntity` + `AuthSessionDao` → `AppDatabase` v2 con migration
- `SupabaseClient` con anon key JWT correcta
- `AuthRepository`: login, logout, checkSession, registrar (con recuperación de huérfanos)
- `AuthViewModel` expone `UiState` vía `StateFlow`
- `LoginScreen`, `RegistroScreen`, `DashboardScreen` en Compose
- `SesionUsuario` domain model
- Función SQL `registrar_empresa` (SECURITY DEFINER) en Supabase — crea empresa + usuario + bodega atómicamente bypaseando RLS
- RLS corregida: `get_empresa_id()` lee de tabla `usuarios` (no JWT claims)
- Verificado end-to-end en dispositivo físico: login ✅ registro multi-empresa ✅

---

## ✅ INSTRUCCIONES PARA CLAUDE

1. **No asumir** nada que no esté en este archivo o en los contratos pegados
2. **Un archivo por sesión** — si el scope crece, parar y preguntar
3. **Respetar nombres** — español para variables, inglés para commits
4. **No filtrar por empresa_id** en código Kotlin — RLS lo hace
5. **Stock siempre por query** — nunca campo mutable en ProductoEntity
6. **Si hay duda sobre un contrato** — preguntar antes de asumir