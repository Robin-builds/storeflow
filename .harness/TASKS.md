# 📋 TASKS.md — Feature Activa
> **Uso:** Pegar junto con `CLAUDE.md` + `ESTADO.md` cuando hay implementación activa.
> **Este archivo se reemplaza completamente al iniciar cada nueva feature.**
> **Archivar al mergear:** mover el contenido a `ESTADO.md` → sección historial si aplica.

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
