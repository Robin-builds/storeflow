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

**Proyecto:** `quvkxpjstzssivsaqimu` (organización nueva; migrado desde `eygbgykglovbivthyqfb`
el 2026-08-08, ver `.harness/MIGRACION_SUPABASE.md`). Mismo proyecto para ambos repos,
mismas credenciales de usuario final. ⚠️ **`eygbgykglovbivthyqfb` es el proyecto viejo,
abandonado desde la migración — no usarlo.** El 22/08 se detectó que `stockflow-web`
(`.env.local` local y posiblemente las env vars de producción en Vercel) seguía
apuntando al proyecto viejo, dos semanas después de la migración — verificar Vercel
si no se hizo ya.

**Repos:**
- **Mobile (Android):** `C:\Users\Windows 11\AndroidStudioProjects\StockFlowv00` — Kotlin, Room offline-first + sync (push/pull) contra Supabase. Lee/escribe todo.
- **Web (dashboard):** `C:\Users\Windows 11\Documents\dev\stockflow-web` — Next.js, sin capa offline, habla directo contra Supabase vía `@supabase/ssr`/`supabase-js`. Es **de solo lectura + descarga por decisión de producto** (23/08: se sacó el import CSV masivo de productos que insertaba — ver `ESTADO.md`; solo queda exportar CSV, que no escribe). Acceso pensado solo para usuarios ADMIN (sin bloqueo técnico todavía, es una convención de uso).

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
  - Web: dos vistas SQL en Supabase (creadas 23/08, `with (security_invoker = true)` — **obligatorio**, sin eso la vista bypassa RLS y filtra mal por empresa):
    - `productos_con_stock` — `productos` + `stock_actual`/`bajo_stock` calculados vía `LEFT JOIN movimientos GROUP BY`. Usada por Productos, Dashboard y Alertas para paginar/ordenar/filtrar por stock en SQL en vez de traer todo a Node.
    - `bodegas_resumen` — agregado por bodega (`total_productos`, `stock_total`, `productos_stock_bajo`) sobre `productos_con_stock`. Usada por Bodegas.
    - `calcularStock()` en `types/index.ts` sigue existiendo para el detalle de producto y lotes (pocas filas, no necesita la vista).
  - **Por qué se agregaron:** con >1000 productos, `select('*, movimientos(cantidad)')` sin `.range()` se corta silenciosamente en 1000 filas (límite default de PostgREST) — Productos/Dashboard/Bodegas/Alertas mostraban conteos truncados sin error visible. Se detectó al cargar datos de prueba a gran escala (10k productos) en la cuenta de sara.
  - Si alguno de los dos lados empieza a cachear/guardar stock en una columna real (no vista), el otro lado queda desincronizado — no hacerlo sin actualizar este documento y avisar al otro lado.
- **`precio: Int`** en toda la cadena — CLP sin decimales. Mismo tipo en Room (`Producto.precio: Int`) y en el formateo web (`Intl.NumberFormat('es-CL', { currency: 'CLP' })`).
- **`movimientos` es inmutable — solo INSERT, nunca UPDATE.**
  - Android lo refuerza a nivel DAO (`OnConflictStrategy.ABORT`).
  - Web hoy **no tiene UI de escritura de movimientos** — es de solo lectura por diseño, precisamente para no romper esta invariante desde otro cliente. Si algún día se agrega una pantalla de movimientos en la web, tiene que respetar "solo insert, nunca update/delete" igual que Android.
- **Español en todo lo user-facing y en nombres de variable/columna** — `nombre_producto`, no `productName`, en ambos lados.

---

## ✅ RESUELTO — `movimientos.usuario_id` (23/08)

- Columna `usuario_id uuid references usuarios(id) on delete set null` — existe en Supabase desde la migración `20260808000000_migracion_completa_organizacion_nueva.sql`. Es **nullable, sin `DEFAULT`** (no se autocompleta con `auth.uid()` en el servidor).
- **Android ahora la setea** (migración Room 8→9, `feat/usuario-id-movimientos` mergeada a `main` el 23/08): `MovimientoRepository` toma el `user_id` de la sesión activa y lo incluye en las 4 rutas de creación de movimientos + el stock inicial de `ProductoRepository.crear()`. Se propaga vía `SyncPayloads`/`PullDtos`. Validado en dispositivo físico (Moto G60): upgrade real v8→v9 sin crash, movimiento nuevo sincronizado, columna Usuario en la web mostrando el nombre real.
- **La web** lee y muestra esta columna desde el 23/08 (mergeada a `master`): join `usuarios(nombre, email)` en la tabla de Movimientos y en el export CSV, visible solo para ADMIN.
- **Limitación que queda (esperada, no es bug):** todos los movimientos creados **antes** del 23/08 (histórico) van a seguir mostrando "—" para siempre — no se completan retroactivamente. Solo los movimientos nuevos, creados después de que los usuarios actualicen la app, van a tener `usuario_id`.

---

## 🔄 ASIMETRÍA DE ESCRITURA (por diseño, no un bug)

- **Android** es offline-first: escribe en Room primero, encola en `sync_queue`, `SyncWorker` empuja a Supabase con reintentos. Puede operar sin conexión. Es la única escritura real del sistema.
- **Web no escribe nada hoy** (23/08: se sacó el import CSV masivo, que era la única excepción). Es de solo lectura + descarga por decisión de producto, no solo por limitación técnica.
- **Si en el futuro se agrega alguna feature de escritura en la web**, tiene que asumir que puede coexistir con escrituras concurrentes de la app Android vía sync — no asumir que "la web es la única fuente de verdad en el momento de escribir". La web no tiene cola offline: cada operación pegaría directo contra Supabase vía `supabase-js`/`@supabase/ssr`, sin reintento automático si falla la conexión.

---

## 🧭 CUÁNDO CONSULTAR EL `.harness` DEL OTRO REPO

Antes de una feature web que dependa de cómo Android modela algo (o viceversa), conviene leer directamente el `.harness/CLAUDE.md` del otro lado en vez de asumir desde este resumen:

- Modelo de datos completo, migraciones Room, entidades: `StockFlowv00\.harness\CLAUDE.md` (sección 🗄️ MODELO DE DATOS).
- Estructura de páginas/componentes web: `stockflow-web\.harness\CLAUDE.md` (sección 🏗️ ARQUITECTURA).
- Estado de features/ramas de cada lado: el `ESTADO.md` respectivo — no asumir que uno refleja el estado del otro.
