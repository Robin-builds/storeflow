# Trazar usuario_id al Crear Movimientos Implementation Plan

**Goal:** Que Android escriba `movimientos.usuario_id` al crear cualquier movimiento, para cerrar el gap documentado en `.harness/PUENTE.md` — la web ya lee esa columna (columna "Usuario" en Movimientos, mergeada a producción el 23/08) pero siempre la ve `null` porque Android nunca la seteaba.

**Architecture:** Migración Room 8→9 que agrega `movimientos.usuario_id` (nullable, FK a `usuarios(id)`, `ON DELETE SET NULL`) — mismo patrón exacto que la columna `lote_id` agregada en la migración 7→8. `MovimientoRepository` toma el `user_id` de la sesión activa (`AuthSessionDao`, ya inyectado en otros repositories) y lo setea en cada `MovimientoEntity` que crea. Se propaga a Supabase vía `SyncPayloads` y se lee de vuelta vía `PullDtos` (consistencia con `lote_id`).

**Tech Stack:** Kotlin, Room 2.6.1, Hilt, JUnit4 + mockk + kotlinx-coroutines-test.

**Platform:** Android

**Spec:** Sin diseño previo — el approach ya estaba especificado en `.harness/PUENTE.md` (gap conocido) y replica un patrón existente (`lote_id`), sin decisiones de diseño abiertas.

**Alcance:** Solo escritura Android → Supabase. No se toca la UI de Android (Historial de movimientos no muestra "quién" — el consumidor de este dato es la web). No se toca la web (ya lee `usuario_id`).

---

### Task 1: Test — `MovimientoRepositoryTest.kt` (TDD, escribir antes de la Task 2)

**Files:**
- Create: `app/src/test/java/cl/storeflow/warehouse/data/repository/MovimientoRepositoryTest.kt`

Seguir el patrón de `BodegaRepositoryTest.kt` (mockk + `runTest`, mismo estilo de `testSession: AuthSessionEntity`). Mockear `MovimientoDao`, `ProductoDao`, `SyncDao`, `SyncTrigger`, `LoteRepository`, `AuthSessionDao`.

- [ ] **Step 1:** `registrarEntrada` con sesión activa (`coEvery { authSessionDao.obtenerSesion() } returns testSession`) → capturar el `MovimientoEntity` insertado con `slot<MovimientoEntity>()` en `coEvery { movimientoDao.insertar(capture(slot)) }` y `assertEquals(testSession.user_id, slot.captured.usuario_id)`.
- [ ] **Step 2:** Mismo chequeo para `registrarSalida` (producto no perecedero, mockear `productoDao.calcularStock` y `productoDao.obtenerPorId` con `es_perecedero = false`).
- [ ] **Step 3:** Mismo chequeo para `registrarAjuste`.
- [ ] **Step 4:** Sin sesión (`coEvery { authSessionDao.obtenerSesion() } returns null`) → `registrarEntrada` igual crea el movimiento (`Result.success`), con `slot.captured.usuario_id == null`.

Estos tests van a fallar contra `MovimientoEntity` actual (no tiene el campo) — esperado, es el punto de partida TDD.

---

### Task 2: `MovimientoEntity.kt` — agregar campo `usuario_id`

**Files:**
- Modify: `app/src/main/java/cl/storeflow/warehouse/data/local/entity/MovimientoEntity.kt`

- [ ] **Step 1:** Agregar `val usuario_id: String? = null` al `data class MovimientoEntity`.
- [ ] **Step 2:** Agregar tercera entrada a `foreignKeys` (mismo patrón que la de `lote_id`):
  ```kotlin
  ForeignKey(
      entity = UsuarioEntity::class,
      parentColumns = ["id"],
      childColumns = ["usuario_id"],
      onDelete = ForeignKey.SET_NULL
  )
  ```
- [ ] **Step 3:** Agregar `Index("usuario_id")` a `indices`. Import `UsuarioEntity` si hace falta.

---

### Task 3: Migración Room 8 → 9

**Files:**
- Modify: `app/src/main/java/cl/storeflow/warehouse/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/cl/storeflow/warehouse/di/DatabaseModule.kt`

- [ ] **Step 1:** En `AppDatabase.kt`, bump `@Database(version = 9, ...)`.
- [ ] **Step 2:** Agregar `MIGRATION_8_9`, mismo patrón que `MIGRATION_7_8` para `lote_id`:
  ```kotlin
  val MIGRATION_8_9 = object : Migration(8, 9) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE movimientos ADD COLUMN usuario_id TEXT REFERENCES usuarios(id) ON DELETE SET NULL")
          db.execSQL("CREATE INDEX IF NOT EXISTS index_movimientos_usuario_id ON movimientos (usuario_id)")
      }
  }
  ```
- [ ] **Step 3:** En `DatabaseModule.kt`, agregar `AppDatabase.MIGRATION_8_9` a la lista de migraciones.

---

### Task 4: `MovimientoRepository.kt` — setear `usuario_id`

**Files:**
- Modify: `app/src/main/java/cl/storeflow/warehouse/data/repository/MovimientoRepository.kt`

- [ ] **Step 1:** Inyectar `AuthSessionDao` en el constructor (mismo patrón que `ProductoRepository`/`BodegaRepository`/`UsuarioRepository` — Hilt lo resuelve solo, ya está provisto).
- [ ] **Step 2:** Agregar helper privado:
  ```kotlin
  private suspend fun usuarioIdActual(): String? = authSessionDao.obtenerSesion()?.user_id
  ```
- [ ] **Step 3:** Setear `usuario_id = usuarioIdActual()` en el `MovimientoEntity(...)` de `registrarEntrada`.
- [ ] **Step 4:** En `registrarSalida`: obtener `usuarioId` una vez al inicio del método, setearlo en la rama no perecedera, y pasarlo como parámetro a `registrarSalidaFefo(productoId, cantidad, nota, usuarioId)`.
- [ ] **Step 5:** En `registrarSalidaFefo`, usar el `usuarioId` recibido en las dos construcciones de `MovimientoEntity` (por lote y remanente).
- [ ] **Step 6:** Setear `usuario_id = usuarioIdActual()` en el `MovimientoEntity(...)` de `registrarAjuste`.

---

### Task 5: `SyncPayloads.kt` y `PullDtos.kt`

**Files:**
- Modify: `app/src/main/java/cl/storeflow/warehouse/data/sync/SyncPayloads.kt`
- Modify: `app/src/main/java/cl/storeflow/warehouse/data/sync/PullDtos.kt`

- [ ] **Step 1:** En `SyncPayloads.kt`, dentro de `MovimientoEntity.toSupabaseJson()`, agregar (mismo patrón que `lote_id`): `usuario_id?.let { put("usuario_id", it) }`.
- [ ] **Step 2:** En `PullDtos.kt`, en `MovimientoDto` agregar `@SerialName("usuario_id") val usuarioId: String? = null`, y en `toEntity()` agregar `usuario_id = usuarioId`.

---

### Task 6: Verificación

- [ ] **Step 1:** `gradlew.bat test` — `MovimientoRepositoryTest` nuevo y toda la suite existente en verde.
- [ ] **Step 2:** `gradlew.bat assembleDebug` — confirma que la migración compila y el schema exportado (`schemas/`) se genera sin error.
- [ ] **Step 3 (validación física, obligatoria antes de mergear):** instalar en dispositivo con la DB ya en versión 8 (upgrade real, no reinstalación limpia), confirmar que abre sin crash, registrar un movimiento nuevo y confirmar que sincroniza con `usuario_id` seteado — verificar en la web (`stockflow-web-eight.vercel.app/dashboard/movimientos`, columna Usuario ya no debería mostrar "—" para ese movimiento) o directo en Supabase.
