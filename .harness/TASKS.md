# 📋 TASKS.md — Feature Activa
> **Uso:** Pegar junto con `CLAUDE.md` + `ESTADO.md` cuando hay implementación activa.
> **Este archivo se reemplaza completamente al iniciar cada nueva feature.**
> **Archivar al mergear:** mover el contenido a `ESTADO.md` → sección historial si aplica.

---

## 🎯 FEATURE EN PROGRESO

**Nombre:** Proveedores UI (segundo intento — modelo a definir)
**Rama:** `feat/historial-movimientos-android`
**Sesión actual:** retomar 07/08 o siguiente sesión
**Estado:** ⏸️ Pausada — bloqueada en definición de modelo de datos

---

## 📌 CONTEXTO DE LA FEATURE

```
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
```

---

## ✅ DEFINITION OF DONE (esta feature)

- [ ] Modelo de datos acordado con el usuario (pregunta pendiente arriba)
- [ ] Funciona en dispositivo físico (no solo BUILD SUCCESSFUL)
- [ ] Tests unitarios relevantes pasan (`gradlew.bat test`)
- [ ] Sin regresiones en features anteriores
- [ ] Commiteado en rama propia con mensaje semántico

---

## 📝 SUBTAREAS

```
[ ] S0 — Preguntar al usuario: ¿agenda de contacto o trazar costos/multi-proveedor?
[ ] S1 — Si aplica: migración Room v9 + ProductoProveedorEntity + DAO
[ ] S2 — Repository (crear/asociar/desasociar producto-proveedor)
[ ] S3 — UI: ProveedoresScreen (CRUD proveedor) + selector de proveedores en form de producto
[ ] S4 — Sync push/pull para la nueva tabla intermedia
```

---

## 🔖 NOTAS DE SESIÓN

```
Última sesión (06/08):
  - Hecho: Historial global de movimientos completo y validado (ver ESTADO.md).
           Fix edge-to-edge en Dashboard (header fijo + insets).
           Fix bug preexistente en ProductoAtributosFormTest.
           Proveedores UI v1 implementada y luego revertida a pedido del usuario.
  - Pendiente: definir modelo de datos de Proveedores antes de tocar código de nuevo.
  - Blocker: falta la respuesta del usuario a la pregunta S0.

Próximo paso inmediato: preguntar si Proveedores es agenda simple o necesita
costos/multi-sourcing, y recién ahí diseñar el modelo (junction table vs FK simple).
```

---

## ⚠️ DECISIONES TOMADAS EN ESTA FEATURE

| Decisión | Por qué |
|---|---|
| Se descarta el modelo "proveedor sin relación a productos" (v1) | El usuario lo consideró inmaduro: un proveedor sin conexión a qué vende no aporta al flujo de inventario |
| Se prefiere (tentativo, sin confirmar) tabla intermedia `producto_proveedores` sobre FK simple si se necesita trazar costos | Mismo patrón ya validado en el codebase (`producto_atributos`) para relaciones N:N con datos propios de la relación |
