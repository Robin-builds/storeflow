# 📋 TASKS.md — Feature Activa
> **Uso:** Pegar junto con `CLAUDE.md` + `ESTADO.md` cuando hay implementación activa.
> **Este archivo se reemplaza completamente al iniciar cada nueva feature.**
> **Archivar al mergear:** mover el contenido a `ESTADO.md` → sección historial si aplica.

---

## 🚧 TRABAJO ACTUAL — Planes & Entitlements v1 (al 2026-11-21)

> Esta sección prevalece sobre "Feature en progreso", que quedó desactualizada.
> Nada de esta lista está autorizado por estar escrito aquí: cada punto se trabaja cuando el dueño lo pida.

**Rama:** `feat/planes-entitlements` (app y `stockflow-web`), sin push. Último commit de la app: `3cee1f7`.
**Spec:** `specs/002-planes-entitlements/`. Avance: 58 de 118 tareas; Fases 3 y 4 completas.
**Bases:** M2, M1 y M3 aplicadas solo en Sandbox. Producción sigue en `20261121161643`.
**Siguiente fase de la spec:** Fase 5, límites reales y bloqueo de usuarios excedentes (T061–T092).

### Defectos encontrados en dispositivo (corregidos y validados en moto g(60), sin commit)

- [x] **DEF-1 — Tras registrarse no se descargaban los datos.** `registrar()` ahora dispara el pull.
- [x] **DEF-2 — Los errores mostraban URL, headers y clave.** `mensajeAuthSeguro` entrega solo un texto limpio.
- [x] **DEF-3 — Crear o eliminar una bodega no disparaba la sincronización.** `BodegaRepository` ahora usa `SyncTrigger`.
- [x] **DEF-4 — Cerrar sesión con trabajo pendiente no mostraba mensaje.** Se oculta el botón y aparece un aviso (T083).
- [x] **DEF-5 — Tras una sesión revocada, el trabajo pendiente no se enviaba al reingresar.** `login` ahora dispara el sync (SC-016).

### Validación en dispositivo (completa, moto g(60), 2026-11-21)

- [x] Registro, Mi plan, downgrade a Free, bodegas en solo lectura y stock intacto.
- [x] Entrada, Salida y Ajuste no hacen nada en una bodega en solo lectura.
- [x] Movimiento en la bodega editable después del downgrade.
- [x] Upgrade real de Room v9 a v10 con datos y un movimiento en cola (puerta 4): sin pérdida y sincronizado.
- [x] Token vencido sin red: datos intactos y sincronización al volver la red.
- [x] Sesión revocada con trabajo pendiente: pide ingresar, rechaza otra cuenta, acepta la misma y envía la cola.
- [x] Eliminar una bodega con productos: se trasladan con stock e historial intactos.
- [x] Trial de empresas existentes: lógica del backfill ensayada en Sandbox con 7 bodegas, 12 usuarios y lotes, en transacción revertida. La migración real aún no corrió sobre empresas existentes.

### Pendientes antes de Producción

- [ ] Integrar `main` en la rama: falta el hotfix `e393ad8` (migración `20261121161643` y su test).
- [ ] T003: instalar `psql` y definir `STOREFLOW_SANDBOX_DB_URL` y `STOREFLOW_PROD_DB_URL`. `scripts/test-planes-concurrencia.sh` aún no se ha ejecutado.
- [ ] T029: invocar de verdad la Edge Function `registrar-usuario-empresa`; solo se simuló su alta con `service_role`.
- [ ] T030: promover M2 a Producción, solo con confirmación explícita.

### Datos de prueba en Sandbox

- Empresa **Prueba Planes** (`prueba.planes@example.com`): trial vencido, 3 bodegas, 2 productos.
- Empresa **Prueba Bodegas** (`prueba.bodegas@example.com`): trial vigente, 1 producto con stock 10. Su sesión quedó abierta en el moto g(60).
- Empresa **Mi Almacen**: creada por el dueño, trial vigente.
- `.mcp.json` apunta a **Producción**: para escribir en Sandbox usar el conector con `project_id = ihmseolpajzqnddldffc`.

---

## 🎯 FEATURE EN PROGRESO

**Estado:** Sin feature activa. Sesión 09/08 fue de recuperación/merge de trabajo ya
hecho (ver `ESTADO.md`), no de desarrollo nuevo. Este archivo queda con el contexto
de la próxima feature candidata (Proveedores UI) para cuando se retome.

---

## 📌 CONTEXTO DE LA PRÓXIMA FEATURE CANDIDATA — Proveedores UI

```
Rama: feat/historial-movimientos-android (donde quedó el intento previo, pausado)

Problema: la app necesita gestión de proveedores conectada de verdad al
          inventario, no un directorio de contactos aislado.

Primer intento (revertido 06/08): CRUD simple sobre ProveedorEntity
  existente (nombre + contacto), sin ninguna relación con Producto.
  El usuario lo frenó: "no está madura, debe estar más incrustada en
  la app" — correcto, no resolvía nada del negocio real.

Preguntas sin responder (bloquean el diseño):
  1. ¿Un producto puede tener varios proveedores? → Sí, casi siempre
     (respaldo, comparar precio, distribuidores distintos). Relación
     real es N:N, no 1:1.
  2. ¿El mismo producto tiene el mismo precio en cada proveedor? → No.
     El precio de COSTO varía por proveedor. Hoy `ProductoEntity.precio`
     es precio de VENTA — no existe precio de costo en el modelo.
  3. PENDIENTE DE PREGUNTAR AL USUARIO: ¿Proveedores es solo agenda de
     contacto (a quién llamo para reponer X), o necesita trazar costos
     de compra y comparar proveedores? La respuesta decide el modelo:
       - Solo agenda → proveedor_id nullable en Producto (1:N, simple)
       - Trazar costos → tabla intermedia producto_proveedores
         (producto_id, proveedor_id, precio_compra, sku_proveedor?),
         mismo patrón que producto_atributos (N:N con datos propios)

Restricciones específicas:
  - No introducir UseCase ni abstracciones nuevas fuera de lo pedido
  - Seguir convención existente: Repository con Result<T>, ViewModel
    con StateFlow<UiState>, patrón ya usado en Bodegas/Atributos
  - Si se opta por producto_proveedores: requiere migración Room
    (versión 9), entidad ProductoProveedorEntity, DAO, y wiring de
    sync (push en SyncWorker/SyncPayloads + pull en PullWorker/DTOs)
  - Es COMPLEJA según el criterio de CLAUDE.md (migración Room + decisión
    de diseño abierta) → amerita docs/plans/*.md antes de codear
```

---

## 🔖 NOTAS DE SESIÓN

```
Sesión 09/08 — recuperación de ramas huérfanas (sin código nuevo):
  - Confirmado que feat/reset-password ya estaba mergeada a main (el usuario
    lo señaló; ESTADO.md tenía la nota vieja "pendiente de decidir merge").
  - Encontradas y mergeadas a main dos ramas de julio que se habían perdido de
    vista tras cambios de rama:
      - feat/guia-usuario-interactiva (ayuda contextual, ver ESTADO.md)
      - feat/scanner-busqueda-dashboard (escaneo QR en buscador global del
        Dashboard, feature nueva de esta misma sesión que casi queda fuera
        del merge — el usuario detectó la omisión antes del push)
  - Gotcha para recordar: al mergear una rama vieja con `git merge main` y
    resolver conflictos reescribiendo un archivo entero (DashboardScreen.kt),
    hay que verificar contra CADA rama sin mergear que toque ese mismo
    archivo — no alcanza con mirar el diff contra el commit de main en el
    momento del merge. Antes de mergear a main, correr
    `git branch --no-merged main` para confirmar que no queda ninguna rama
    con trabajo validado colgando.
  - Push a origin/main hecho (bc001d3).

Sesión 06/08 (Proveedores UI, sin tocar desde entonces):
  - Proveedores UI sigue pausada en feat/historial-movimientos-android, bloqueada
    en definición de modelo de datos (pregunta 3 sin responder, ver arriba).
```

---

## ⚠️ DECISIONES TOMADAS (Proveedores UI, aún pendiente)

| Decisión | Por qué |
|---|---|
| Se descarta el modelo "proveedor sin relación a productos" (v1) | El usuario lo consideró inmaduro: un proveedor sin conexión a qué vende no aporta al flujo de inventario |
| Se prefiere (tentativo, sin confirmar) tabla intermedia `producto_proveedores` sobre FK simple si se necesita trazar costos | Mismo patrón ya validado en el codebase (`producto_atributos`) para relaciones N:N con datos propios de la relación |
