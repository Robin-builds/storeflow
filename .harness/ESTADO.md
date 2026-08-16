# 📊 ESTADO.md — Estado Dinámico del Proyecto
> **Uso:** Pegar junto con `CLAUDE.md` al inicio de CADA sesión.
> **Actualizar este archivo al cerrar cada sesión** (rama activa, último trabajo, blockers, próximo paso).
> **Última actualización:** 16/08/2026 — Mensaje de "sin conexión" + integración de Firebase Crashlytics (rama `fix/mensaje-error-sin-conexion`, pusheada a origin, sin mergear):
> 9. **Mensaje claro de sin conexión** — se detectó en dispositivo físico (Moto G60) que un registro fallido por falta de internet mostraba la excepción técnica cruda (`Unable to resolve host...`) directo al usuario. `AuthRepository` (`login`, `registrar`, `cambiarPassword`) ahora detecta `UnknownHostException`/mensaje "Unable to resolve host" y muestra "Sin conexión a internet. Verifica tu WiFi o datos móviles e intenta nuevamente." — commit `eb76947`.
> 10. **Integración Firebase Crashlytics** — primer SDK externo del proyecto (aparte de Supabase). Proyecto Firebase `storeflow-8bf78` ya creado por el usuario, app registrada con `applicationId = cl.storeflow.app`. Se agregaron plugins `google-services` (4.5.0) y `firebase-crashlytics` (3.0.7) + `firebase-bom` (34.17.0) — versiones verificadas contra el Maven de Google, no de memoria (conocimiento del asistente llega a enero 2026). `google-services.json` versionado (no es secreto, está restringido por `applicationId`+SHA — a diferencia de `keystore.properties`/`*.jks` que sí están gitignorados). Nueva clase `CrashlyticsTree` (`Timber.Tree`) que reenvía `Timber.w`/`Timber.e` existentes a `Crashlytics.recordException()` sin tocar los call sites de repositories/workers. Colección deshabilitada en debug (`setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)`), habilitada por defecto en release. **Validado en dispositivo físico:** crash forzado temporalmente (revertido después) llegó a la consola de Firebase — con un matiz: el dispositivo se quedó sin internet a mitad de la prueba (mismo problema del punto 9) y Crashlytics lo reintentó solo al recuperar conexión, sin perder el reporte (queda cacheado en disco hasta poder subirlo). Primer crash de una app nueva en Firebase tardó ~2 min en aparecer en consola (demora esperada, documentada por Google).
> 8. **Cambio de `applicationId`** (`cl.storeflow.warehouse` → `cl.storeflow.app`) — el listing anterior en Play Store quedó con el keystore perdido; se abandona y se crea uno nuevo desde cero. `namespace` de Kotlin **se mantiene** en `cl.storeflow.warehouse` (solo cambia el identificador de publicación, no el código). También `versionCode` → 1 y `versionName` → "1.0.0" (listing nuevo, arranca de cero) en `app/build.gradle.kts`. Sin impacto en Supabase (no hay OAuth ni deep links atados al `applicationId`) ni en `AndroidManifest.xml` (usa `${applicationId}` dinámico). Quedan como referencias cosméticas sin actualizar (no bloquean build/publicación): `.harness/CLAUDE.md` (línea de nombre del proyecto), `docs/plans/2026-08-08-reset-password.md`, `.claude/settings.local.json` (rutas de test hardcodeadas). **Pendiente:** el usuario crea el keystore nuevo manualmente desde Android Studio antes de generar el release firmado; `proguard-rules.pro` revisado y está vacío pero `isMinifyEnabled = false` en release, así que no aplica para este build.
>
> **Sesión 09/08/2026** — recuperación/limpieza (sin feature nueva de fondo) + ajustes chicos de ayuda contextual y de idioma, todo directo a `main` (sin rama propia, cambios pequeños de texto/UI):
> 1. Se encontraron y mergearon a `main` dos ramas de julio que habían quedado huérfanas tras cambios de rama: `feat/guia-usuario-interactiva` (ayuda contextual) y `feat/scanner-busqueda-dashboard` (escaneo QR en buscador global) — commit `bc001d3`.
> 2. Se corrigió este documento: `feat/reset-password` y `feat/historial-movimientos-android` ya estaban mergeadas a `main` desde antes, pero seguían listadas aquí como pendientes — todas las ramas locales quedaron al día con `main` (`git branch --no-merged main` da vacío) — commit `b4e674e`.
> 3. Se agregó `BotonAyuda` ("?") junto al switch "Es perecedero" (form de producto) y al campo "Fecha de caducidad" (diálogo de Entrada en Movimientos) — el usuario (dueño del proyecto) no tenía claro dónde se ingresaba la fecha de vencimiento y pidió explicarlo ahí mismo. De paso se corrigieron 2 usos de "acá" (rioplatense) a "aquí" — commit `6c58e42`.
> 4. Se revisó todo `app/src/main/java/` buscando voseo/argentinismos (público: Chile) — no había más casos. Se agregó la convención "Textos UI: español neutro LatAm" a `CLAUDE.md` para que no se repita.
> 5. **Branding/ícono** (rama `feat/icono-app`, mergeada a `main`): se reemplazó el ícono placeholder de Android Studio (fondo verde con grilla, nunca personalizado) por un adaptive icon real — fondo `#0D1519`, primer plano con cajas cian/naranja, versión monocroma para íconos tematizados (Android 13+). El usuario proveyó el export de Android Studio (`Image Asset`) en `Desktop\res`; se fusionó con `app/src/main/res` en vez de reemplazar la carpeta entera (esa carpeta no traía `colors.xml`/`strings.xml`/`themes.xml`/`file_paths.xml`/el PDF de la guía — reemplazarla íntegra habría roto el build). De paso se migró `mipmap-anydpi` → `mipmap-anydpi-v26` (redundante desde que minSdk es 27) — commit `681d096`.
> 6. Se agregó el logo (`ic_launcher_foreground`) junto al nombre de la app en el header del Dashboard y en el TopAppBar de Configuración. El wordmark "STOREFLOW" — que se perdía en gris sobre el tema más oscuro — pasó a bicolor corporativo (STORE cian, FLOW naranja) con contorno blanco delgado (dos `Text` superpuestos: uno con `drawStyle = Stroke` en blanco detrás, el relleno de color encima) — mismo commit `681d096`. Se probó (y se descartó, a pedido del usuario) recolorear el ícono de engranaje de Configuración con los mismos colores de marca — quedó en su gris neutro original.
> 7. **Linterna en el escáner** — `BarcodeScannerDialog` (compartido por form de producto, Productos y buscador del Dashboard) ahora tiene un botón de linterna vía `CameraX CameraControl.enableTorch()`, útil para escanear en bodegas oscuras. Solo se muestra si `cameraInfo.hasFlashUnit()`. Primera versión (ícono chico, blanco sobre transparente) resultó poco visible — se rehízo como botón circular de 56dp con fondo sólido (naranja apagada `#F0921E` / amarillo encendida `#FFD500`, ícono negro). Se probó y se descartó una card "Linterna" standalone en el Dashboard (`CameraManager.setTorchMode`, sin preview) — el usuario prefirió mantener solo el botón dentro del escáner; "Historial de movimientos" volvió a ocupar el ancho completo — commit `dd983c3`.

---

## 🌿 RAMAS

- `feat/cambio-applicationId-play-store` — cambio de `applicationId` para republicar en Play Store con listing limpio (keystore anterior perdido). Falta: keystore nuevo (lo crea el usuario manualmente) y build de release firmado antes de mergear.
- `fix/mensaje-error-sin-conexion` (activa, pusheada a origin) — mensaje de "sin conexión" en AuthRepository + integración Firebase Crashlytics. Falta: decidir merge (no depende de `feat/cambio-applicationId-play-store`, se puede mergear independiente).

Única feature con diseño abierto (no una rama activa): **Proveedores UI** — ver `🗺️ FEATURES FUTURAS / PENDIENTES`.

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
  Historial global de movimientos      ✅ Validada en dispositivo físico — mergeada a main
  Cambio/reseteo de password           ✅ Validada en dispositivo físico — mergeada a main
  Ayuda contextual ("?" + PDF + toggle)✅ Validada en dispositivo físico — mergeada a main (09/08)
  Escaneo QR en buscador de Dashboard  ✅ Validada en dispositivo físico — mergeada a main (09/08)
  Ícono de app + wordmark de marca     ✅ Validada en dispositivo físico — mergeada a main (09/08)
  Linterna en escáner QR/barcode       ✅ Validada en dispositivo físico — mergeada a main (09/08)
```

---

## ✨ FEATURES IMPLEMENTADAS (resumen para contexto)

- 📷 **Escaneo QR/Barcode** — `BarcodeScannerDialog` (ML Kit + CameraX). Botón en campo SKU (form de producto), en el buscador de `ProductosListScreen`, y en el buscador global del Dashboard (`BusquedaProductoCard`). Incluye linterna (`CameraControl.enableTorch()`) — botón circular 56dp, naranja/amarillo según estado, visible solo si `cameraInfo.hasFlashUnit()`.
- 🗂️ **Selección masiva** — `seleccionados: StateFlow<Set<String>>`, `modoSeleccion` derivado, `eliminarSeleccionados()`, `transferirSeleccionados()`. "Seleccionar todos" opera sobre `productosVisibles`.
- 🎨 **Atributos dinámicos** — solo tipo TEXT en UI (MVP). `NUMBER`/`DATE` en enum, sin UI.
- 🌗 **Temas composables** — `PaletaId` (Forja/Planta/Búnker) × `OscuridadId` (Penumbra/Nocturno/Abismo). Reemplaza `TemaApp` (eliminado). Persiste en DataStore (dos keys). Selector visual en ConfiguracionScreen.
- 💬 **Compartir stock** — `ACTION_SEND` desde AlertasScreen, ProductosListScreen y ConfiguracionScreen.
- 👥 **Gestión de usuarios ADMIN** — Edge Function `registrar-usuario-empresa` con `SUPABASE_SERVICE_ROLE_KEY` server-side (Supabase Admin API no disponible en móvil).
- 📄 **Paginación "Cargar más"** — en memoria. `tamanioPagina` (25/50/100). Búsqueda resetea ventana visible. "Compartir inventario" sigue operando sobre el total filtrado.
- 🔍 **Búsqueda global en Dashboard** — por nombre/SKU en **todas las bodegas** (`ProductoDao.observarConStockPorEmpresa`). Solo lectura, con escaneo QR. "Ver en Productos" navega con búsqueda precargada vía `Rutas.productosConBusqueda()`.
- 🥫 **Lotes / FEFO** — `es_perecedero` toggle en form de producto. Entrada pide `DatePicker` + número de lote. Salida aplica FEFO automático multi-lote. "Próximos a vencer" en AlertasScreen + card dividida en Dashboard (`AlertaMiniCard`). Sync completo contra Supabase.
- 📋 **Historial global de movimientos** — `MovimientoDao.observarPorEmpresa` (JOIN productos), `HistorialMovimientosViewModel/Screen` con búsqueda + paginación "Cargar más". Card de entrada en Dashboard (todos los roles).
- 🔑 **Cambio/reseteo de contraseña** — auto-servicio (`AuthRepository.cambiarPassword`, diálogo en Configuración) + reseteo por ADMIN (`AuthRepository.resetearPasswordUsuario`, Edge Function `resetear-password-usuario`, diálogo en UsuariosScreen, oculto para la propia fila del ADMIN logueado).
- ❓ **Ayuda contextual** — `BotonAyuda` ("?") en cards de Dashboard (Productos, Bodegas, Historial, alertas stock bajo/próximos a vencer, Configurar productos, Usuarios, Menor stock, Sin actividad), en Configuración (Dashboard Web, Apariencia), en form de producto (Stock mínimo, Es perecedero) y en el diálogo de Entrada de Movimientos (Fecha de caducidad). Toggle "Mostrar ayuda contextual" en Configuración → sección Ayuda (`AyudaRepository`/`AyudaViewModel`, persistido en DataStore, expuesto a todo el árbol vía `LocalMostrarAyuda`). Guía PDF descargable (`GuiaPdf.abrirGuiaPdf`, `FileProvider`). `OnboardingDialog` en primer ingreso al Dashboard.
- 🎨 **Ícono de app + wordmark** — adaptive icon real (fondo `#0D1519`, cajas cian `#2EC6DA`/naranja `#F0921E`, monocromo para Android 13+), reemplaza el placeholder de Android Studio. Logo (`ic_launcher_foreground`) junto al nombre en el header del Dashboard (28dp) y en el TopAppBar de Configuración (30dp, lado derecho). Wordmark "STOREFLOW" bicolor (STORE cian, FLOW naranja) con contorno blanco delgado vía `Text` duplicado (`drawStyle = Stroke` blanco detrás + relleno de color encima).

---

## 🗺️ FEATURES FUTURAS / PENDIENTES

| Feature | Estado | Notas |
|---|---|---|
| 💬 WhatsApp notif. push | ☐ Pendiente | Edge Function Supabase; requiere aprobación Meta; cero impacto Android |
| Atributos NUMBER/DATE | ☐ Pendiente | Enum listo, sin UI |
| Proveedores UI | ☐ Pendiente — intento revertido | Rolodex simple insuficiente. Falta definir relación N:N producto↔proveedor y dónde vive el precio de costo (por proveedor, no en `Producto`) antes de reintentar. Es COMPLEJA (migración Room + decisión de diseño abierta) → amerita `docs/plans/*.md`. Ver detalle en `TASKS.md` |
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

*(Nota: esto es del repo web, separado del Android. No se tocó en la sesión 09/08.)*

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
| Cambio/reseteo de password (auto-servicio + ADMIN) | Dispositivo físico | ✅ | ninguno |
| Escaneo QR en buscador global del Dashboard | Moto G60 | ✅ | ninguno |
| Ayuda contextual ("?" en cards, toggle, guía PDF) | Moto G60 | ✅ | ninguno — recuperada de rama huérfana de julio, revalidada tras merge |
| Ayuda contextual — "Es perecedero" + "Fecha de caducidad" | Moto G60 | ✅ | ninguno |
| Linterna en escáner QR/barcode | Moto G60 | ✅ | 1ra versión (ícono chico) poco visible — rehecha como botón circular de color; card standalone en Dashboard probada y descartada |
| Ícono de app (adaptive icon nuevo) | Moto G60 | ✅ | ninguno |
| Logo + wordmark bicolor en Dashboard/Configuración | Moto G60 | ✅ | Colores del engranaje de Configuración probados en 2 combinaciones, ninguna convenció — se revirtió a gris neutro original |
