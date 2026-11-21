# Contrato: códigos de rechazo por plan

PostgREST entrega el código en el campo `code`. **El cliente clasifica por `code`, no por el status HTTP.**

| Código | Significado | Status | Requisitos |
|---|---|---|---|
| `SFP01` | Límite de bodegas | 400 | FR-009, FR-014 |
| `SFP02` | Límite de usuarios | 400 (409 en la Edge Function) | FR-009 |
| `SFP03` | Capacidad no incluida en el plan | 400 | FR-011, FR-025 |
| `SFP04` | Bodega en solo lectura | 400 | FR-006, FR-028 |
| `SFP05` | Usuario excedente | 403 en el pre-request, 400 en triggers | FR-029 |
| `SFP06` | Bodega con productos | 400 | FR-031 |

## Rutas permitidas a un usuario excedente

| Ruta | Condición |
|---|---|
| `rpc/obtener_entitlement` | Siempre |
| `rpc/reportar_retenidos` | Siempre |
| `POST movimientos`, `POST lotes` | `Prefer: return=minimal` y sin `resolution=`. El trigger valida la ventana de FR-028 |

Toda otra ruta responde `SFP05`.

```json
{ "code": "SFP05", "message": "Usuario sin acceso en el plan actual", "details": null, "hint": null }
```

Los mensajes no mencionan precios ni invitan a comprar (FR-027).

## Edge Function `registrar-usuario-empresa`

Si el insert falla con `SFP02`, elimina el usuario de Auth recién creado y responde:

```json
HTTP 409
{ "error": "Límite de usuarios del plan alcanzado", "code": "SFP02" }
```

## Panel web

| Caso | Respuesta |
|---|---|
| `/api/export/*` sin la capacidad | 403 `{ "error": "...", "code": "SFP03" }` |
| Sesión de usuario excedente | Pantalla de acceso bloqueado |
| RPC inexistente (`PGRST202`) | "Sin plan": no restringe nada |
| Otro error al leer el plan | Export 503; las páginas siguen sin aplicar el límite de historial |

## Clasificación en la app

| Respuesta | Clase | Acción |
|---|---|---|
| 2xx | Éxito | Marca sincronizado y elimina de la cola |
| `code` = `SFP*` | Rechazo por plan | Retiene |
| Red, 408, 429, 5xx, 401 | Transitorio | Reintenta sin descartar |
| `23503` (FK) | Dependencia pendiente | Reintenta después de su padre, sin descartar |
| `23505` de PK en un `INSERT` | Ya existe en el servidor | Cuenta como éxito |
| Otro 4xx en `movimientos` o `lotes` | Permanente | Retiene con su código; **nunca se descarta** |
| Otro 4xx en otras entidades | Permanente | Comportamiento actual |
