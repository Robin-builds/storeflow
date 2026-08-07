# 📊 ESTADO.md — Estado Dinámico del Proyecto
> **Uso:** Pegar junto con `CLAUDE.md` al inicio de CADA sesión.
> **Actualizar este archivo al cerrar cada sesión** (rama activa, último trabajo, blockers, próximo paso).
> **Última actualización:** 06/08/2026 — Rama `feat/historial-movimientos-android` en curso (sin mergear a `main`). Historial global de movimientos completo y validado en dispositivo. Intento de Proveedores UI revertido — falta definir modelo de datos antes de reintentar.

---

## 🌿 RAMA ACTIVA

```
feat/historial-movimientos-android — EN CURSO, sin mergear:
  ✅ Historial global de movimientos (Android) — completo, validado en Moto G60
       - MovimientoDao.observarPorEmpresa (JOIN productos), MovimientoConProducto
       - HistorialMovimientosViewModel/Screen (búsqueda + paginación "Cargar más")
       - Card de entrada en Dashboard (todos los roles)
  ✅ Fix edge-to-edge en Dashboard — header fijo (WindowInsets.statusBars),
       cards inferiores ya no tapadas por nav bar (WindowInsets.navigationBars)
  ✅ Fix bug preexistente: ProductoAtributosFormTest referenciaba contarConNombre
       (método inexistente) — bloqueaba gradlew.bat test completo
  ⏸️ Proveedores UI — implementado y luego REVERTIDO a pedido del usuario.
       Motivo: la UI armada era solo un directorio de contactos (nombre+contacto),
       sin conexión real con productos. Preguntas abiertas antes de reintentar:
       - Relación producto↔proveedor es N:N (no 1:1) — un producto puede
         comprarse a varios proveedores y viceversa
       - Precio de COSTO varía por proveedor — hoy `ProductoEntity.precio` es
         precio de VENTA, no existe concepto de costo en el modelo actual
       - Propuesta sobre la mesa: tabla intermedia `producto_proveedores`
         (producto_id, proveedor_id, precio_compra, sku_proveedor?), mismo
         patrón que `producto_atributos` (N:N con datos propios de la relación)
       - Alternativa más simple (sin costo ni multi-sourcing): proveedor_id
         nullable directo en Producto — descartada si se quiere trazar costos
       - PENDIENTE: preguntar al usuario si Proveedores es solo agenda de
         contacto o si necesita trazar costos/comparar proveedores — de eso
         depende cuál modelo construir

main — todo mergeado (antes de esta rama):
  ✅ sistema de temas composable
  ✅ paginación "Cargar más"
  ✅ fix fallback downgrade Room
  ✅ card búsqueda productos en Dashboard (feat/dashboard-buscar-producto)
  ✅ trazabilidad de caducidad/lotes — 5 sesiones completas
       feat/lotes-esquema → feat/lotes-supabase-sync → feat/lotes-ui-producto
       → feat/lotes-ui-movimientos → feat/lotes-alertas
```

---

## ✅ ESTADO ACTUAL — Fases completadas

```
FASE 0 (Setup)                         ✅ Completa
FASE 1 (Auth)                          ✅ Completa
FASE 2 (Productos CRUD)                ✅ Completa
FASE 3 (Movimientos)                   ✅ Completa
FASE 4 (Alertas)                       ✅ Completa
FASE 5A (Sync push)                    ✅ Completa — validada en dispositivo físico
FASE 5B (Sync pull)                    ✅ Completa — validada en dispositivo físico (2 cuentas)
FASE 6 (Multi-bodega + Roles)          ✅ Completa — validada en dispositivo físico
FASE 7 (Pulido UI)                     ✅ Completa — validada en dispositivo físico
FASE 8 S1-S5 (Dom. rico + Atributos)  ✅ Completa — tests de dominio verdes
FASE 9 S1 (Config. atributos UI)       ✅ Completa — validada en dispositivo físico
FASE 9 S2 (Form. producto atributos)   ✅ Completa
FASE 9 S3 (Sync push atributos)        ✅ Completa — validada en dispositivo físico
FASE 9 S4 (Pull atributos)             ✅ Completa — validada en 2 dispositivos
FASE 10 S1 (Reg. usuario en empresa)   ✅ Completa — Edge Function deployada
FASE 10 S2 (UsuariosScreen ADMIN)      ✅ Completa — validada en 2 dispositivos físicos

POST-MVP:
  Escaneo QR/Barcode (SKU)             ✅ Validada en dispositivo físico
  Selección masiva                     ✅ Validada en dispositivo físico
  Compartir stock / dashboard web      ✅ Validada en dispositivo físico
  Búsqueda SKU/barcode                 ✅ Validada en dispositivo físico
  Login UX teclado                     ✅ Completo
  Renombrado StockFlow→StoreFlow       ✅ Completo (package cl.storeflow.warehouse)
  Sistema de temas composable          ✅ Validado en dispositivo físico (incl. Búnker)
  Reportar problema                    ✅ Wireado en MainActivity/ConfiguracionScreen
  Card alertas en Dashboard            ✅ Presente en código actual
  Nombres duplicados + descripción     ✅ SKU único por empresa (case-insensitive)
  ConfiguracionScreen (cards)          ✅ Completo
  Dashboard web                        ✅ En producción
  Paginación "Cargar más" Productos    ✅ Validada en dispositivo físico
  Fallback downgrade Room              ✅ main (86d69fa)
  Búsqueda productos en Dashboard      ✅ Validada en dispositivo físico, mergeada a main
  Lotes / Trazabilidad caducidad       ✅ Completo (5/5 sesiones) — mergeada a main
```

---

## ✨ FEATURES IMPLEMENTADAS (resumen para contexto)

- 📷 **Escaneo QR/Barcode** — `BarcodeScannerDialog` (ML Kit + CameraX), botón en campo SKU.
- 🗂️ **Selección masiva** — `seleccionados: StateFlow<Set<String>>`, `modoSeleccion` derivado, `eliminarSeleccionados()`, `transferirSeleccionados()`. "Seleccionar todos" opera sobre `productosVisibles`.
- 🎨 **Atributos dinámicos** — solo tipo TEXT en UI (MVP). `NUMBER`/`DATE` en enum, sin UI.
- 🌗 **Temas composables** — `PaletaId` (Forja/Planta/Búnker) × `OscuridadId` (Penumbra/Nocturno/Abismo). Reemplaza `TemaApp` (eliminado). Persiste en DataStore (dos keys). Selector visual en ConfiguracionScreen.
- 💬 **Compartir stock** — `ACTION_SEND` desde AlertasScreen, ProductosListScreen y ConfiguracionScreen.
- 👥 **Gestión de usuarios ADMIN** — Edge Function `registrar-usuario-empresa` con `SUPABASE_SERVICE_ROLE_KEY` server-side (Supabase Admin API no disponible en móvil).
- 📄 **Paginación "Cargar más"** — en memoria. `tamanioPagina` (25/50/100). Búsqueda resetea ventana visible. "Compartir inventario" sigue operando sobre el total filtrado.
- 🔍 **Búsqueda global en Dashboard** — por nombre/SKU en **todas las bodegas** (`ProductoDao.observarConStockPorEmpresa`). Solo lectura. "Ver en Productos" navega con búsqueda precargada vía `Rutas.productosConBusqueda()`.
- 🥫 **Lotes / FEFO** — `es_perecedero` toggle en form de producto. Entrada pide `DatePicker` + número de lote. Salida aplica FEFO automático multi-lote. "Próximos a vencer" en AlertasScreen + card dividida en Dashboard (`AlertaMiniCard`). Sync completo contra Supabase.

---

## 🗺️ FEATURES FUTURAS / PENDIENTES

| Feature | Estado | Notas |
|---|---|---|
| 💬 WhatsApp notif. push | ☐ Pendiente | Edge Function Supabase; requiere aprobación Meta; cero impacto Android |
| 📋 Historial global movimientos | ✅ Web / ✅ Android | Completo en `feat/historial-movimientos-android`, validado en dispositivo, sin mergear a `main` |
| Atributos NUMBER/DATE | ☐ Pendiente | Enum listo, sin UI |
| Proveedores UI | ☐ Pendiente — intento revertido | Rolodex simple insuficiente. Falta definir relación N:N producto↔proveedor y dónde vive el precio de costo (por proveedor, no en `Producto`) antes de reintentar. Ver detalle en RAMA ACTIVA |
| Paginación real Supabase (web) | ☐ Pospuesta | Stock no es columna real — requeriría vista SQL en Supabase para ordenar/paginar por stock |

---

## 🌐 PROYECTO WEB (`stockflow-web`)

**Path local:** `C:\Users\Windows 11\Documents\dev\stockflow-web`
**Stack:** Next.js · TypeScript · Tailwind · @supabase/ssr
**URL producción:** `https://stockflow-web-eight.vercel.app`

**Páginas:** login, dashboard (resumen), productos, movimientos, `productos/importar` (nuevo).
- Productos: filtro por bodega, orden Nombre/Precio/Stock/Alerta, exportar CSV (BOM UTF-8).
- Movimientos: paginación 25/página, orden por columna, búsqueda por nombre de producto (`?q=`), badge tipo ENTRADA/SALIDA/AJUSTE corregido.
- **Fix crítico:** `utils/getRol.ts` consultaba `usuarios.user_id` (no existe — la PK `id` ya es el uid de auth) → rol siempre caía a OPERADOR. Corregido en `feat/historial-movimientos` e `feat/importar-productos-csv`.

**Ramas sin mergear a `master`:**
- `feat/historial-movimientos` — búsqueda + fix badge tipo + fix getRol (commiteada).
- `feat/importar-productos-csv` — import CSV masivo (en progreso): plantilla descargable con atributos dinámicos, preview/validación, commit en lotes de 300 vía `supabase-js` (sin RPC). Solo creación, no actualiza existentes. Toca `getRol.ts` (mismo fix, sin conflicto esperado) y `productos/page.tsx`.

---

## 🧪 ÚLTIMAS PRUEBAS FÍSICAS

| Feature | Dispositivo | Resultado | Bugs encontrados |
|---|---|---|---|
| Lotes S1 esquema v7→v8 | Moto G60 | ✅ | 1er intento sin `REFERENCES` inline crasheaba — corregido |
| Lotes S2 Supabase sync | Moto G60 + Supabase real (~10k registros) | ✅ | ninguno |
| Lotes S3 toggle perecedero | Moto G60 | ✅ | Falsa alarma: atributo obligatorio sin valor (validación preexistente) |
| Lotes S4 entrada+salida FEFO | Moto G60 | ✅ split 6+4=10 confirmado | Falsa alarma: lotes de pruebas previas ya agotados |
| Lotes S5 alertas + Dashboard | Moto G60 | ✅ con fecha en el pasado | Falsa alarma: usuario no vio sección nueva sobre la conocida |
| Búsqueda productos Dashboard | Moto G60 | ✅ | ninguno |
| Sistema de temas composable | Moto G60 | ✅ (incl. Búnker cian) | 2 rondas de ajuste post-validación (contraste cerrar sesión + unificación íconos) |
| Paginación "Cargar más" | Moto G60 | ✅ | `gradlew.bat test` falla por `contarConNombre` (bug preexistente en `ProductoAtributosFormTest`, no relacionado) |
| Fix fallback downgrade Room | Moto G60 (schema v8 vs v7 en código) | ✅ | Crash root-caused vía logcat antes del fix |
| 5B Sync pull final | Samsung S25 FE, 2 cuentas | ✅ | Plugin serialización, precio Double→Int, Room FK migration (resueltos) |
| 10 S2 UsuariosScreen | 2 dispositivos físicos | ✅ | ninguno |
| Historial global de movimientos | Moto G60 | ✅ | ninguno |
| Fix edge-to-edge Dashboard (header fijo + insets) | Moto G60 | ✅ | Llave de cierre faltante al mover el header fuera del scroll — corregida antes de instalar |
