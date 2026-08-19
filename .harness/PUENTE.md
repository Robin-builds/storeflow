# 🌉 PUENTE.md — Contexto Compartido Mobile ↔ Web
> **Qué es esto:** StoreFlow tiene dos codebases separados (repos git distintos, sin
> monorepo) que leen y escriben la MISMA base Supabase. Este documento es el lugar
> donde se anota todo lo que un cambio en un lado puede romper o afectar del otro.
>
> **Mirror:** este archivo existe idéntico en los dos repos. Si lo editás en uno,
> editalo también en el otro — no hay symlink, es sincronización manual:
> - `C:\Users\Windows 11\AndroidStudioProjects\StockFlowv00\.harness\PUENTE.md` (este archivo)
> - `C:\Users\Windows 11\Documents\dev\stockflow-web\.harness\PUENTE.md`
>
> **Cuándo actualizarlo:** cualquier cambio que toque una tabla, columna, RLS policy,
> rol, o convención que ambos lados asumen. Si el cambio es puramente interno de un
> lado (una pantalla nueva, un componente, un refactor que no toca Supabase), no hace
> falta tocar este archivo.

---

## 🗄️ PROYECTO SUPABASE COMPARTIDO

**Proyecto:** `eygbgykglovbivthyqfb` / "StockFlow" (mismo para ambos repos, mismas credenciales de usuario final).

**Repos:**
- **Mobile (Android):** `C:\Users\Windows 11\AndroidStudioProjects\StockFlowv00` — Kotlin, Room offline-first + sync (push/pull) contra Supabase. Lee/escribe todo.
- **Web (dashboard):** `C:\Users\Windows 11\Documents\dev\stockflow-web` — Next.js, sin capa offline, habla directo contra Supabase vía `@supabase/ssr`/`supabase-js`. Hoy es **mayormente de solo lectura** (excepción: import CSV masivo de productos, que sí inserta).

---

## 🔐 AUTH Y MULTI-TENANCY (idéntico en ambos lados)

- Supabase Auth compartido — mismas cuentas de usuario sirven para loguearse en la app Android y en el dashboard web.
- `empresa_id` vive en `app_metadata` del JWT (custom claim) → RLS filtra por empresa automáticamente en TODAS las tablas.
- **Ninguno de los dos códigos filtra manualmente por `empresa_id`** — es responsabilidad exclusiva de RLS. Si algún día alguno de los dos empieza a filtrar manualmente, es señal de que algo está mal configurado en RLS, no un patrón a copiar.
- **Roles:** `usuarios.rol` ∈ `ADMIN | OPERADOR`.
  - Android: `rol` se persiste en `AuthSessionEntity` al login (fuente de verdad local), controla acceso a bodegas/atributos/usuarios.
  - Web: `getRol()` (`utils/getRol.ts`) consulta `usuarios.rol` por `auth.uid()` en cada request. Controla visibilidad del botón exportar CSV y de la columna Usuario en Movimientos.
  - ⚠️ **Gotcha ya vivido:** la PK `usuarios.id` **es** el uid de auth — no existe columna `usuarios.user_id`. La web tuvo un bug (`getRol.ts` consultando `user_id`) que hacía caer el rol siempre a OPERADOR. Si se toca este join de cualquier lado, recordar que `usuarios.id = auth.uid()`, sin columna intermedia.

---

## 📐 CONVENCIONES DE DATOS COMPARTIDAS

- **Stock nunca es un campo almacenado, en ningún lado.** Se calcula siempre como `SUM(movimientos.cantidad) WHERE producto_id = :id`.
  - Android: `ProductoDao` (queries `observarConStock*`).
  - Web: `calcularStock()` en `types/index.ts`, sobre `movimientos(cantidad)` embebido vía PostgREST.
  - Si alguno de los dos lados empieza a cachear/guardar stock en una columna, el otro lado queda desincronizado — no hacerlo sin actualizar este documento y avisar al otro lado.
- **`precio: Int`** en toda la cadena — CLP sin decimales. Mismo tipo en Room (`Producto.precio: Int`) y en el formateo web (`Intl.NumberFormat('es-CL', { currency: 'CLP' })`).
- **`movimientos` es inmutable — solo INSERT, nunca UPDATE.**
  - Android lo refuerza a nivel DAO (`OnConflictStrategy.ABORT`).
  - Web hoy **no tiene UI de escritura de movimientos** — es de solo lectura por diseño, precisamente para no romper esta invariante desde otro cliente. Si algún día se agrega una pantalla de movimientos en la web, tiene que respetar "solo insert, nunca update/delete" igual que Android.
- **Español en todo lo user-facing y en nombres de variable/columna** — `nombre_producto`, no `productName`, en ambos lados.

---

## ⚠️ GAP CONOCIDO — `movimientos.usuario_id`

- Columna `usuario_id uuid references usuarios(id) on delete set null` — existe en Supabase desde la migración `20260808000000_migracion_completa_organizacion_nueva.sql`. Es **nullable, sin `DEFAULT`** (no se autocompleta con `auth.uid()` en el servidor).
- **Android nunca la setea** — `MovimientoEntity`/`MovimientoRepository`/`SyncPayloads` no tienen ningún campo `usuario_id`. Todo movimiento creado desde el celular llega a Supabase con `usuario_id = null`.
- **La web (rama `feat/ui-refresh-visual`, en progreso al 18/08)** empezó a leer y mostrar esta columna: join `usuarios(nombre, email)` en la tabla de Movimientos y en el export CSV, visible solo para ADMIN. Ver `TASKS.md` del repo web.
- **Consecuencia práctica hoy:** la columna "Usuario" en la web va a mostrar "—" para prácticamente todos los movimientos, porque la inmensa mayoría se originan en el celular.
- **Pendiente de decidir con el usuario:** si vale la pena que Android empiece a mandar `usuario_id` (tomarlo de `AuthSessionEntity`, incluirlo en el insert de `MovimientoRepository` y en el payload de `SyncWorker`) antes de que esta feature web se dé por terminada, o si se documenta como limitación conocida y se sigue.

---

## 🔄 ASIMETRÍA DE ESCRITURA (por diseño, no un bug)

- **Android** es offline-first: escribe en Room primero, encola en `sync_queue`, `SyncWorker` empuja a Supabase con reintentos. Puede operar sin conexión.
- **Web** no tiene cola offline — cada operación (incluida la carga masiva de CSV, en lotes de 300) pega directo contra Supabase vía `supabase-js`/`@supabase/ssr`. Si falla la conexión, falla la operación, sin reintento automático.
- Esto significa que **cualquier feature nueva en la web que escriba datos** (hoy solo el import CSV) debe asumir que puede coexistir con escrituras concurrentes de la app Android vía sync — no asumir que "la web es la única fuente de verdad en el momento de escribir".

---

## 🧭 CUÁNDO CONSULTAR EL `.harness` DEL OTRO REPO

Antes de una feature web que dependa de cómo Android modela algo (o viceversa), conviene leer directamente el `.harness/CLAUDE.md` del otro lado en vez de asumir desde este resumen:

- Modelo de datos completo, migraciones Room, entidades: `StockFlowv00\.harness\CLAUDE.md` (sección 🗄️ MODELO DE DATOS).
- Estructura de páginas/componentes web: `stockflow-web\.harness\CLAUDE.md` (sección 🏗️ ARQUITECTURA).
- Estado de features/ramas de cada lado: el `ESTADO.md` respectivo — no asumir que uno refleja el estado del otro.
