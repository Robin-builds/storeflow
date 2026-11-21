# Research: Planes & Entitlements v1

Fecha: 2026-11-21. Base: código de la app y de `stockflow-web`, catálogo de Sandbox y Producción consultado solo con `SELECT`, y documentación de Supabase. No quedan `NEEDS CLARIFICATION`.

## R1. Plan efectivo calculado al evaluar

- **Decision**: una función pura recibe la suscripción y un instante. Orden: tester → Premium; Fundador vigente → Premium; período pagado vigente → su plan; trial vigente → Standard; si no → Free.
- **Rationale**: cumple FR-002 y FR-003 aunque no corra ningún proceso. Recibir el instante permite probar T − 1 s y T sin un reloj falsificable.
- **Alternatives considered**: `pg_cron` que actualice un estado (si falla, el plan queda mal); GUC para simular la hora (un mecanismo así no debe existir en Producción).

## R2. Bloqueo de usuarios excedentes (FR-029)

- **Decision**: función registrada en `pgrst.db_pre_request`. Si el usuario autenticado es excedente, responde 403 con `code = SFP05`. Exceptúa `rpc/obtener_entitlement`, `rpc/reportar_retenidos` y los `POST` a `movimientos` y `lotes` con `Prefer: return=minimal` y sin `resolution=`.
- **Por qué esas condiciones**: `return=minimal` impide que un `select` embebido devuelva datos de la empresa, y sin `resolution=` un `POST` no puede convertirse en edición. Los triggers deciden si la fecha entra en la ventana (FR-028).
- **Rationale**: con RLS, un `PATCH` o `DELETE` de un usuario bloqueado responde 2xx con 0 filas, y `SyncWorker` lo eliminaría de la cola como exitoso. El pre-request devuelve un error explícito en todos los verbos, sin reescribir políticas.
- **Verificado**: la documentación lo describe para "rechazar requests tras exceder una cuota". Solo cubre la Data API. En ambos ambientes la publicación de Realtime tiene 0 tablas, `pg_graphql` no está instalado y no hay otro pre-request configurado.
- **Restricción**: no agregar tablas a Realtime sin extender este bloqueo.
- **Permisos**: `SECURITY DEFINER`, `search_path = ''`, y `EXECUTE` explícito para `anon`, `authenticated` y `service_role`, porque corre con el rol de cada request.
- **Ante un error interno**: deja pasar el request y registra el error. El aislamiento entre empresas lo sigue garantizando RLS, y un fallo del límite comercial no debe tumbar la API. La decisión se calcula dentro del bloque protegido y el rechazo se lanza fuera, para no capturarlo.
- **Reversión**: `alter role authenticator reset pgrst.db_pre_request` y `notify pgrst, 'reload config'`.
- **Riesgo**: un error en esta función afecta a toda la API. Debe ser mínima y tener test propio, y solo se promueve tras validarla en Sandbox. El formato real de `request.path` también se confirma allí.
- **Nota**: Supabase Auth seguirá autenticando la contraseña. "No puede iniciar sesión" significa que la app muestra el bloqueo y el servidor no entrega datos.
- **Alternatives considered**: `get_empresa_id()` devolviendo `NULL` (problema de 0 filas); hook de Auth (no corta sesiones ya abiertas).

## R3. Límite de historial en la presentación

- **Decision**: filtrar por fecha en `HistorialMovimientosViewModel` y en las páginas del panel web. **No** usar RLS.
- **Rationale**: `productos_con_stock` y `bodegas_resumen` suman movimientos con `security_invoker`, y el pull alimenta el stock local. Una política por fecha haría que el stock mostrado fuera incorrecto (FR-021).
- **Efecto aceptado**: un usuario técnico puede leer su propio historial completo por API. Son datos de su empresa, así que no rompe el Principio III.

## R4. Export

- **Decision**: la capacidad se exige en `/api/export/movimientos` y `/api/export/productos`, en el servidor de Next.js, antes de consultar datos. Responde 403.
- **Definición del dueño**: el export es una capacidad **solo del panel web**. Las acciones de compartir texto de la app no son export y no se limitan por plan.
- **Efecto aceptado**: es un límite comercial. Los datos siguen legibles por la API para su propia empresa.

## R5. Concurrencia de límites y cupo

- **Decision**: el trigger toma `FOR NO KEY UPDATE` sobre la suscripción y luego cuenta, en función `VOLATILE`. El cupo Fundador bloquea la fila de su ola y cuenta otorgamientos.
- **Rationale**: serializa solo lo necesario. En `READ COMMITTED`, el conteo posterior al lock ve lo confirmado por la transacción anterior (FR-014, SC-007).
- **Alternatives considered**: `SERIALIZABLE` (exige reintentos); contador guardado (puede divergir).

## R6. Antigüedad de bodegas y usuarios

- **Decision**: en `INSERT` el servidor asigna `created_at = clock_timestamp()`, porque `now()` es igual para todas las altas de una transacción. En `UPDATE` conserva el valor anterior, sin lanzar error.
- **Rationale**: FR-007 ordena por antigüedad y hoy el cliente envía la fecha, así que se podría antedatar una bodega para elegir cuál queda activa. No se lanza error porque Room guarda milisegundos y Postgres microsegundos.
- **Efecto aceptado**: una bodega creada offline toma como antigüedad su llegada al servidor.

## R7. Ventana de FR-028 y lotes

- **Decision**: `vencimiento = coalesce(cobertura_fin, greatest(trial_fin, pagado_hasta))`, con `pagado_hasta + 7 días` para Fundador vigente. `cobertura_fin` se fija en baja o paso a Free, porque `pasar_a_free` borra `pagado_hasta`. Un lote cuyo movimiento entra en la ventana también se acepta si la empresa tenía la capacidad de lotes.
- **Rationale**: un movimiento de entrada depende de su lote. Sin esto, FR-028 no se podría cumplir para perecederos.
- **Riesgo aceptado** (SUP-8): la fecha viene del dispositivo; el movimiento queda marcado.

## R8. Trial de activación (FR-026)

- **Decision**: `activado_en` y `usaba_lotes` en la suscripción. Durante ese trial son activos los recursos creados hasta `activado_en`. Las altas nuevas se validan contra Standard.
- **Rationale**: evita guardar marcas por fila, y al terminar el trial aplica la regla normal.

## R9. Pérdida de Fundador sin jobs

- **Decision**: la vigencia se deriva de `perdido_at` nulo y `ahora < pagado_hasta + 7 días`. La función de pago persiste la pérdida antes de renovar si la ventana ya pasó.
- **Rationale**: la pérdida es efectiva desde V + 7 días aunque nadie ejecute nada (FR-015, FR-016).
- **Registro histórico**: `fundador_otorgamientos` no tiene FK a `empresas`, para sobrevivir a su eliminación (FR-013).

## R10. Códigos de rechazo

- **Decision**: `SQLSTATE` propios `SFP01`–`SFP06`. El cliente clasifica por `code`, no por status.
- **Verificado**: PostgREST devuelve el código en JSON y mapea los no reconocidos a 400. El pre-request usa `raise sqlstate 'PGRST'` para responder 403.

## R11. Reloj y gracia offline

- **Decision**: guardar `validado_en_servidor`, `reloj_en_validacion`, `mayor_reloj_observado`, `elapsed_en_validacion` y `boot_count`. Tiempo transcurrido = el mayor entre `max(reloj, mayor_observado) − reloj_en_validacion` y, sin reinicio, el delta de `elapsedRealtime`.
- **Rationale**: un desfase fijo no altera la cuenta, y atrasar la hora no la reduce (FR-024).
- **Riesgo residual**: reiniciar y atrasar el reloj sin abrir la app puede alargar la gracia local. El servidor sigue aplicando el plan real.

## R12. Retención en la cola

- **Decision**: columnas `rechazo_codigo` y `rechazado_at` en `sync_queue`. Un ítem `SFP*` no se reintenta ni se borra. Al cambiar el plan, se limpian las marcas y se reintenta. Los errores transitorios dejan de descartarse.
- **Guardia P3**: un `DELETE` de bodega queda retenido si existe un ítem anterior de `productos` retenido o pendiente.
- **Alternatives considered**: tabla separada (duplica el estado de la cola).

## R13. Pull y FK locales

- **Hallazgo**: `DatabaseModule` desactiva las FK, por lo que el `REPLACE` del pull no borra hijos. Con FK activas sí lo haría (comprobado en SQLite).
- **Decision**: no cambiar el pull ni el PRAGMA. Como el pull no borra filas locales, lo retenido permanece.
- **Advertencia**: activar las FK sin reemplazar `REPLACE` causaría pérdida de datos locales.

## R14. Avisos

- **Decision**: la RPC emite de forma idempotente los momentos alcanzados, y la app muestra solo el más reciente sin cerrar. Cerrarlo cierra también los anteriores (FR-017). Sin señal, lo calcula desde el plan guardado.
- **Rationale**: como el único canal es la app, no se necesita un job. Una empresa que no abre la app no tiene a quién mostrarle el aviso.

## R15. Catálogo inicial

| Plan | Bodegas | Usuarios | Export | Historial | Lotes | Multiempresa |
|---|---|---|---|---|---|---|
| FREE | 1 | 2 | No | 90 días | No | No |
| STANDARD | 5 | 10 | Sí | 365 días | No | No |
| PREMIUM | Sin límite | Sin límite | Sí | Completo | Sí | Sí |

Estos valores viven en el servidor y se manejan del lado web; la app no los recibe. Standard usa el extremo alto de los rangos de SUP-2. Los 365 días son un valor provisional. Multiempresa queda como dato de catálogo, sin comportamiento en v1 (NO-4).

## R16. Versión de Room

- **Decision**: usar la siguiente versión libre. La deuda D3 también reservó la v10; la feature que se fusione segunda toma la v11.

## R17. Una sola vía de refresh de sesión (C3, FR-030)

- **Hallazgo**: hoy pueden rotar el mismo refresh token tres actores: el auto-refresh del cliente Auth (`alwaysAutoRefresh = true` por defecto en supabase-kt 1.4.6), `SyncWorker` por HTTP directo y `checkSession`. `SyncWorker` guarda el token nuevo solo en Room, así que el cliente Auth queda con uno antiguo.
- **Decision**: un `GestorSesion` singleton con `Mutex` es el único que refresca, usando Room como fuente. Tras cada refresh llama a `importSession(session, autoRefresh = false)`. El cliente se configura con `alwaysAutoRefresh = false` y `autoLoadFromStorage = false`. `SyncWorker`, `PullWorker` y `checkSession` piden el token a `GestorSesion`.
- **Sesión inválida**: `clearAllTables()` solo corre si no hay trabajo pendiente ni retenido. Si lo hay, se conservan los datos y se pide ingresar de nuevo. Solo se acepta el mismo `user_id`, para no enviar la cola con otra cuenta (Principio III).
- **Sin verificar**: la configuración de detección de reutilización de tokens del proyecto. El diseño no depende de ella.

## R18. Bodega con productos (C4, FR-031)

- **Decision**: trigger `BEFORE DELETE` en `bodegas`, en M2, que rechaza con `SFP06` si tiene productos. Exime acciones FK (`pg_trigger_depth() > 1`), para que eliminar una empresa siga funcionando.
- **Rationale**: la guardia del cliente no protege ante REST directo ni versiones anteriores. El flujo normal no cambia, porque la app traslada los productos antes de eliminar.
- **Fuera de alcance**: eliminar un **producto** sigue borrando sus movimientos en cascada. Es la deuda D3, con borrado lógico ya decidido.

## R19. Resumen de retenidos (I2, FR-023)

- **Decision**: tabla `retenidos_resumen` y RPC `reportar_retenidos(p_dispositivo, p_cantidad)`. El ADMIN recibe el resumen por usuario en `obtener_entitlement`.
- **Rationale**: lo retenido vive en el dispositivo de quien lo registró. Un conteo cumple FR-023 sin subir datos rechazados al servidor.
- **Alternatives considered**: subir el detalle (duplica el trabajo retenido); limitarlo al propio dispositivo (el ADMIN no sabría que el stock tiene trabajo pendiente).

## R20. RPC evolutiva y login de excedentes (U1, U2)

- **Decision**: M1 crea la RPC con `bloqueado = false` y sin bodegas en solo lectura. M3 la reemplaza con los conjuntos activos, M4 agrega el resumen de retenidos y M6 los avisos. Para un excedente devuelve `empresa_id`, `rol` y `bloqueado`.
- **Login**: tras autenticar, la app consulta la RPC **antes** de leer `usuarios`. Si está bloqueado, guarda la sesión con esos datos y muestra la pantalla de bloqueo.

## R21. Panel web sin plan (I3)

- **Decision**: `getPlan` devuelve "sin plan" ante `PGRST202` y el panel no restringe nada, igual que la app. Ante otro error, el export responde 503 y las páginas siguen funcionando sin aplicar el límite de historial.
- **Rationale**: el panel se despliega antes que las migraciones.

## R22. Degradación sin red (G2)

- **Decision**: la RPC entrega `degradacion_free`, ya resuelta por el servidor, y la caché la guarda. Al vencer la gracia, `EvaluadorPlan` la aplica sin conocer ningún valor de límite. Una bodega fuera de `bodegas_activas`, incluida una creada offline, queda en solo lectura.
- **Rationale**: la app solo conoce el tipo de plan (FR-011), y tampoco podría reconstruir el orden, porque el servidor asigna la antigüedad (R6).
