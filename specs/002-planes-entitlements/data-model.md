# Data Model: Planes & Entitlements v1

## Servidor (Postgres)

Todas las tablas tienen `id uuid` como PK (Principio V) y RLS activo.

### `planes_catalogo`

Tres filas exactas. Sin acceso para `anon` ni `authenticated`.

| Campo | Tipo | Regla |
|---|---|---|
| `plan` | `text` unique | `FREE`, `STANDARD`, `PREMIUM` |
| `max_bodegas`, `max_usuarios` | `integer` null | `NULL` = sin límite; `>= 1` |
| `export`, `lotes`, `multiempresa` | `boolean` not null | Capacidades |
| `historial_dias` | `integer` null | `NULL` = completo; `>= 1` |

### `suscripciones`

| Campo | Tipo | Regla |
|---|---|---|
| `empresa_id` | `uuid` not null unique | FK a `empresas`, `on delete cascade` |
| `trial_inicio` | `timestamptz` not null | Instante del servidor |
| `trial_fin` | `timestamptz` not null | Check: `= trial_inicio + interval '30 days'` |
| `plan_pagado` | `text` null | `STANDARD` o `PREMIUM` |
| `pagado_hasta` | `timestamptz` null | Check: nulo si y solo si `plan_pagado` es nulo |
| `primer_pago_standard_at` | `timestamptz` null | Determina "por primera vez a Standard pagado" (FR-012) |
| `cobertura_fin` | `timestamptz` null | Fin anticipado por baja o paso a Free; `registrar_pago` lo limpia (R7) |
| `es_tester` | `boolean` not null | Default `false` (FR-018) |
| `activado_en` | `timestamptz` null | Solo empresas existentes (FR-026) |
| `usaba_lotes` | `boolean` not null | Default `false` |
| `created_at`, `updated_at` | `timestamptz` not null | |

**RLS**: `SELECT` de la propia empresa. Sin políticas de escritura y con `REVOKE` de `INSERT/UPDATE/DELETE`.

**Creación**: trigger `AFTER INSERT` en `empresas`, en la misma transacción del registro (FR-001). El backfill de M1 crea un trial para cada empresa existente (FR-026), con `es_tester = false`.

### `fundador_olas`

| Campo | Tipo | Regla |
|---|---|---|
| `ola` | `integer` unique | v1 tiene solo la ola 1 |
| `cupo` | `integer` not null | Ola 1 = 100 |

Sin acceso para clientes. Permite la Ola 2 sin cambiar el modelo (NO-2).

### `fundador_otorgamientos`

| Campo | Tipo | Regla |
|---|---|---|
| `empresa_id` | `uuid` not null unique | **Sin FK**: sobrevive a la eliminación de la empresa (FR-013) |
| `ola` | `integer` not null | FK a `fundador_olas(ola)` |
| `otorgado_at` | `timestamptz` not null | |
| `perdido_at` | `timestamptz` null | Una vez fijado no vuelve a nulo (FR-016) |
| `motivo_perdida` | `text` null | `BAJA`, `PASO_A_FREE`, `IMPAGO` |

Sin acceso para clientes.

### `plan_cambios`

Registro de solo inserción (FR-019). Sin acceso para clientes.

| Campo | Tipo | Regla |
|---|---|---|
| `empresa_id` | `uuid` not null | Sin FK, para conservar la auditoría |
| `accion` | `text` not null | `PAGO`, `BAJA`, `PASO_A_FREE`, `TESTER` |
| `detalle` | `jsonb` not null | Valores anterior y nuevo |
| `operador` | `text` not null | No vacío |
| `created_at` | `timestamptz` not null | |

### `movimientos_fuera_plan`

| Campo | Tipo | Regla |
|---|---|---|
| `movimiento_id` | `uuid` unique | FK a `movimientos`, `on delete cascade` |
| `empresa_id` | `uuid` not null | FK a `empresas` |
| `marcado_at` | `timestamptz` not null | Default `now()` |

**RLS**: `SELECT` de la propia empresa. Solo la escribe un trigger (FR-028, Principio II).

### `avisos_plan`

| Campo | Tipo | Regla |
|---|---|---|
| `empresa_id` | `uuid` not null | FK a `empresas` |
| `momento` | `text` not null | `DIA_0`, `MENOS_7`, `MENOS_1`, `TERMINO`, `MAS_2` |
| `emitido_at` | `timestamptz` not null | |
| `cerrado_por` | `uuid[]` not null | ADMIN que lo cerraron |

Unique (`empresa_id`, `momento`), que garantiza la emisión única (FR-017). Se lee y se cierra solo mediante las RPC.

### `retenidos_resumen`

Resumen por usuario y dispositivo (FR-023). Sin acceso directo: se escribe con `reportar_retenidos` y el ADMIN lo lee en `obtener_entitlement`.

| Campo | Tipo | Regla |
|---|---|---|
| `empresa_id` | `uuid` not null | FK a `empresas`, `on delete cascade` |
| `usuario_id` | `uuid` not null | FK a `usuarios`, `on delete cascade` |
| `dispositivo_id` | `uuid` not null | Generado en el dispositivo |
| `cantidad` | `integer` not null | `> 0`; reportar 0 elimina la fila |
| `actualizado_at` | `timestamptz` not null | Hora del servidor |

Unique (`usuario_id`, `dispositivo_id`).

### Vencimiento (derivado)

`coalesce(cobertura_fin, greatest(trial_fin, pagado_hasta))`, con `pagado_hasta + 7 días` si la empresa es Fundador vigente. Es la base de la ventana de FR-028.

### Conjuntos activos (derivados, sin columnas)

- **Bodegas activas**: las primeras `max_bodegas` por (`created_at`, `id`).
- **Usuarios activos**: primero el ADMIN más antiguo; luego el resto por (`created_at`, `id`), hasta `max_usuarios`.
- **Trial de activación**: además, todos los recursos creados hasta `activado_en`.

No se guarda ninguna marca, así que un downgrade o reactivación no modifica filas de negocio (FR-005, FR-008).

### Triggers

| Tabla | Momento | Regla | Código |
|---|---|---|---|
| `bodegas` | BEFORE INSERT | Límite + `created_at` del servidor | `SFP01` |
| `bodegas` | BEFORE DELETE (M2) | Rechaza si todavía tiene productos (FR-031) | `SFP06` |
| `bodegas` | BEFORE UPDATE/DELETE | Conserva `created_at`; rechaza si está en solo lectura o es la única activa con excedentes | `SFP04` |
| `usuarios` | BEFORE INSERT | Límite + `created_at` del servidor | `SFP02` |
| `usuarios` | BEFORE UPDATE | Conserva `created_at` | — |
| `productos` | BEFORE I/U/D | Bodega de origen y destino activas | `SFP04` |
| `lotes` | BEFORE I/U/D | Bodega activa + capacidad de lotes, o `INSERT` dentro de la ventana (R7). Un excedente solo puede insertar dentro de la ventana | `SFP03`, `SFP04`, `SFP05` |
| `producto_atributos` | BEFORE I/U/D | Bodega del producto activa | `SFP04` |
| `movimientos` | BEFORE INSERT | Bodega y usuario activos, o fecha anterior a vencimiento + 7 días | `SFP04`, `SFP05` |
| `movimientos` | AFTER INSERT | Registra la marca si entró por FR-028, incluidos los de excedentes | — |

Las acciones referenciales de FK (`pg_trigger_depth() > 1`) quedan exentas. Los límites aplican también al service role (Edge Function). El bloqueo general de excedentes es el pre-request (R2); los triggers de `lotes` y `movimientos` deciden solo sus inserciones permitidas.

## Local (Room)

### `plan_cache` (nueva)

| Campo | Tipo | Regla |
|---|---|---|
| `empresa_id` | `TEXT` PK | Principio V |
| `plan_efectivo`, `estado` | `TEXT` | |
| `bloqueado` | `INTEGER` | Usuario excedente |
| `puede_crear_bodega`, `puede_crear_usuario` | `INTEGER` | Permisos resueltos por el servidor |
| `historial_desde` | `INTEGER` null | `NULL` = historial completo |
| `lotes` | `INTEGER` | |
| `degradacion_free` | `TEXT` | JSON con bodegas activas, bloqueo, capacidades, permisos e historial para degradar sin red |
| `bodegas_solo_lectura` | `TEXT` | JSON |
| `dispositivo_id` | `TEXT` | UUID generado en el dispositivo |
| `trial_fin`, `vencimiento` | `INTEGER` null | |
| `validado_en_servidor`, `reloj_en_validacion`, `mayor_reloj_observado` | `INTEGER` | R11 |
| `elapsed_en_validacion`, `boot_count` | `INTEGER` | R11 |

### `avisos_plan` (nueva)

| Campo | Tipo | Regla |
|---|---|---|
| `id` | `TEXT` PK | UUID (Principio V) |
| `empresa_id`, `momento` | `TEXT` | Índice único (`empresa_id`, `momento`) |
| `cerrado` | `INTEGER` | Cierre local |
| `cierre_pendiente` | `INTEGER` | Falta confirmarlo en el servidor |

### `sync_queue` (columnas nuevas)

| Campo | Tipo | Regla |
|---|---|---|
| `rechazo_codigo` | `TEXT` null | `SFP01`–`SFP06` u otro código de error permanente |
| `rechazado_at` | `INTEGER` null | |

Las tablas se vacían en logout con `clearAllTables()`.

## Transiciones

```text
Registro o activación ──► TRIAL (Standard, 30 días)
TRIAL ──vence sin pago──► FREE (excedentes en solo lectura, usuarios bloqueados)
TRIAL / FREE ──pago Standard, primera vez, con cupo──► FUNDADOR (Premium)
TRIAL / FREE ──pago, sin cupo o no primera vez──► PAGADO
PAGADO ──vence──► FREE
FUNDADOR ──vence──► gracia de 7 días (sigue Premium)
gracia ──regulariza──► FUNDADOR
gracia ──V + 7 días──► pierde estatus, FREE
FUNDADOR ──baja o paso a Free──► pierde estatus
```

```text
Ítem de cola: PENDIENTE ──OK──► eliminado
              PENDIENTE ──SFP*──► RETENIDO ──cambia el plan──► PENDIENTE
              PENDIENTE ──transitorio──► PENDIENTE
```
