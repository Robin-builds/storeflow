# Contrato: RPC de plan

## `obtener_entitlement()`

`POST /rest/v1/rpc/obtener_entitlement` con JWT de usuario y cuerpo `{}`.

- **No recibe parámetros.** La empresa sale de la sesión (FR-020).
- `EXECUTE` solo para `authenticated`.
- **Un usuario excedente** recibe solo `bloqueado`, `empresa_id`, `rol` y `ahora_servidor`. Sus otras rutas permitidas están en [codigos-rechazo.md](./codigos-rechazo.md).
- La app la consulta **antes** de leer `usuarios` al iniciar sesión.
- Emite de forma idempotente los avisos alcanzados (FR-017).

### Respuesta 200

```json
{
  "bloqueado": false,
  "empresa_id": "uuid",
  "rol": "ADMIN",
  "plan_efectivo": "STANDARD",
  "estado": "TRIAL",
  "capacidades": { "export": true, "lotes": false },
  "permisos": { "crear_bodega": true, "crear_usuario": true },
  "historial_desde": "2025-11-21T12:00:00Z",
  "trial_fin": "2026-12-21T12:00:00Z",
  "vencimiento": "2026-12-21T12:00:00Z",
  "bodegas_solo_lectura": ["uuid"],
  "degradacion_free": {
    "bodegas_activas": ["uuid"],
    "bloqueado": false,
    "capacidades": { "export": false, "lotes": false },
    "permisos": { "crear_bodega": false, "crear_usuario": false },
    "historial_desde": "2026-08-23T12:00:00Z"
  },
  "retenidos": [{ "usuario_id": "uuid", "cantidad": 3, "actualizado_at": "2026-11-21T12:00:00Z" }],
  "avisos": [{ "momento": "MENOS_7", "cerrado": false }],
  "ahora_servidor": "2026-11-21T12:00:00Z"
}
```

- La app recibe el tipo de plan y permisos ya resueltos. **Nunca recibe valores de límites** (FR-011).
- `historial_desde = null` significa historial completo.
- `degradacion_free` es lo que la app aplica sin red al vencer la gracia. Una bodega que no esté en `bodegas_activas` queda en solo lectura (R22).
- `avisos` y `retenidos` solo llegan para ADMIN.
- **Evolución**: M1 devuelve `bloqueado = false` y listas vacías; M3 agrega conjuntos activos, M4 retenidos y M6 avisos.
- Los avisos de vencimiento (`MENOS_7`, `MENOS_1`, `TERMINO`, `MAS_2`) se emiten solo a empresas que nunca pagaron y sin marca de tester (M6).
- **Nunca incluye** precios ni el cupo Fundador (FR-027).

### Errores

| Caso | Comportamiento de la app |
|---|---|
| Sin red o 5xx | Conserva la caché y aplica la gracia |
| 401 | Refresca el token y reintenta |
| Función inexistente (`PGRST202`) | Estado "sin plan": no bloquea nada (Rollout, paso 1) |
| Usuario sin empresa | Error; no escribe caché |

## `cerrar_aviso_plan(p_momento text)`

`POST /rest/v1/rpc/cerrar_aviso_plan`. Solo ADMIN de la empresa de la sesión. Cierra ese momento **y los anteriores** para ese ADMIN. Es idempotente y no acepta `empresa_id`.

## `reportar_retenidos(p_dispositivo uuid, p_cantidad integer)`

`POST /rest/v1/rpc/reportar_retenidos`. Disponible para todo usuario autenticado, incluido un excedente. Usuario y empresa salen de la sesión. Con `p_cantidad = 0` elimina la fila; con un valor negativo responde error. No devuelve datos.
