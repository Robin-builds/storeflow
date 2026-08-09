# 📋 TASKS.md — Feature Activa
> **Uso:** Pegar junto con `CLAUDE.md` + `ESTADO.md` cuando hay implementación activa.
> **Este archivo se reemplaza completamente al iniciar cada nueva feature.**
> **Archivar al mergear:** mover el contenido a `ESTADO.md` → sección historial si aplica.

---

## 🎯 FEATURE EN PROGRESO

**Nombre:** Cambio y reseteo de contraseña
**Rama:** `feat/reset-password`
**Sesión actual:** 08/08 — completada y validada en dispositivo físico
**Estado:** ✅ Completa — pendiente de decidir merge a `main`

**Nota:** la feature anterior (Proveedores UI, en `feat/historial-movimientos-android`)
sigue pausada — ver `ESTADO.md` sección RAMA ACTIVA para su contexto, no se tocó en
esta sesión.

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

- [x] Funciona en dispositivo físico (no solo BUILD SUCCESSFUL) — validado 08/08
- [x] Tests unitarios relevantes pasan (`gradlew.bat test`) — suite completa verde
- [x] Sin regresiones en features anteriores
- [x] Commiteado en rama propia con mensaje semántico (`feat/reset-password`)
- [x] Edge function `resetear-password-usuario` desplegada a producción (`quvkxpjstzssivsaqimu`)

---

## 📝 SUBTAREAS (ver `docs/plans/2026-08-08-reset-password.md`)

```
[x] Task 1 — AuthRepository.cambiarPassword (auto-servicio)
[x] Task 2 — ConfiguracionViewModel (TDD)
[x] Task 3 — UI de auto-servicio en ConfiguracionScreen
[x] Task 4 — Build de verificación (checkpoint)
[x] Task 5 — Edge Function resetear-password-usuario
[x] Task 6 — AuthRepository.resetearPasswordUsuario + UsuariosViewModel.resetearPassword (TDD)
[x] Task 7 — UI de reseteo en UsuariosScreen
[x] Task 8 — Build final, deploy de la edge function y validación en dispositivo
```

---

## 🔖 NOTAS DE SESIÓN

```
Sesión 08/08 — feat/reset-password:
  - Hecho: Tasks 6-8 del plan completadas (Tasks 1-5 venían de sesión anterior).
           Reseteo de password por ADMIN: AuthRepository.resetearPasswordUsuario,
           UsuariosViewModel.resetearPassword (TDD, UsuariosViewModelTest nuevo),
           DialogResetearPassword + opción en menú de UsuariosScreen (oculta para
           la propia fila del ADMIN logueado).
           Edge function resetear-password-usuario desplegada a producción
           (quvkxpjstzssivsaqimu) vía `npx supabase functions deploy` con
           access token temporal (revocado post-deploy).
           Validado en dispositivo físico: cambio de password auto-servicio +
           reseteo por ADMIN + re-login con password nueva — todo OK.
  - Gotcha: CLI de Supabase no soporta npm install global; se usó `npx supabase`.
            La cuenta conectada al MCP de Supabase de esta sesión (proyecto
            "StockFlow", eygbgykglovbivthyqfb) NO es el proyecto real de la app
            (quvkxpjstzssivsaqimu, otra cuenta/org) — verificar SUPABASE_URL en
            SupabaseClient.kt antes de asumir cuál proyecto tocar.
  - Pendiente: decidir si mergear feat/reset-password a main.

Sesión 06/08 (feature previa, sin tocar en esta sesión — ver ESTADO.md):
  - Proveedores UI sigue pausada en feat/historial-movimientos-android, bloqueada
    en definición de modelo de datos (pregunta S0 sin responder).
```

---

## ⚠️ DECISIONES TOMADAS EN ESTA FEATURE

| Decisión | Por qué |
|---|---|
| Se descarta el modelo "proveedor sin relación a productos" (v1) | El usuario lo consideró inmaduro: un proveedor sin conexión a qué vende no aporta al flujo de inventario |
| Se prefiere (tentativo, sin confirmar) tabla intermedia `producto_proveedores` sobre FK simple si se necesita trazar costos | Mismo patrón ya validado en el codebase (`producto_atributos`) para relaciones N:N con datos propios de la relación |
