# Contrato: procesamiento de la cola de sync

Orden de cada ejecución de `SyncWorker`:

1. Obtener un token válido desde `GestorSesion`, la única vía de refresh (R17).
2. **Revalidar el plan antes de revisar si la cola está vacía**, para que un cambio llegue aunque no haya nada que enviar (FR-010). Si falla por red, continúa: el servidor sigue aplicando el límite.
3. Si `bloqueado = true`, enviar **solo** los `INSERT` de `lotes` y `movimientos` con `Prefer: return=minimal`, y conservar el resto de la cola. No enviar los que dependan de un `INSERT` de producto o lote aún no aceptado. El servidor decide con la ventana de FR-028 (FR-029).
4. Si el plan cambió, limpiar las marcas de rechazo para reintentar (FR-008).
5. Recorrer la cola por `created_at`, omitiendo retenidos y sus dependientes.
6. Clasificar cada respuesta según [codigos-rechazo.md](./codigos-rechazo.md).
7. Informar la cantidad de retenidos con `reportar_retenidos` si cambió (FR-023).

## Reglas de retención

| Ítem | Queda retenido si |
|---|---|
| Producto | Su bodega tiene un `INSERT` retenido |
| Lote, atributo, movimiento | Su producto, su lote o la bodega del producto tiene un **`INSERT`** retenido. Un `UPDATE` retenido del padre no retiene a sus dependientes, porque el padre ya existe en el servidor (FR-028) |
| `DELETE` de bodega | Existe un ítem **anterior** de `productos` retenido o pendiente |

## Invariantes

- Un ítem retenido no se borra, sin importar los intentos (FR-023).
- Un ítem retenido no cuenta como error para `Result.retry()`, para no reintentar sin fin.
- Los contadores de pendientes y de rechazados son distintos.
- La app no ofrece cerrar sesión a ningún usuario con trabajo pendiente o retenido (FR-030).
- Una sesión vencida o inválida **no** borra trabajo sin sincronizar, y solo el mismo usuario puede retomarla (FR-030).
