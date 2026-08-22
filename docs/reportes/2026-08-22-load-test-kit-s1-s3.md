# Kit de load testing — S1-S3, resultados consolidados

**Fecha:** 2026-08-22
**Rama:** `test/load-test-kit` (mismo commit que `develop`/`main` tras la consolidación de esta sesión: `80fa4b1`)
**Dispositivos:** Samsung S25 FE (`SM-S731B`, Android 16) · Motorola Moto G60 (Android 12)
**Kit:** `app/src/androidTest/java/cl/storeflow/warehouse/loadtest/` (`LoadTestSchema.kt`, `LoadTestSeeder.kt`, `Medidor.kt`, `RoomScaleProbeTest.kt`)

---

## 0. Consolidación de ramas (previo a S1-S3)

Antes de retomar el kit se encontraron 10 ramas locales sin mergear a `develop`:
`feat/lotes-esquema`, `feat/lotes-ui-producto`, `feat/lotes-ui-movimientos`, `feat/lotes-supabase-sync`,
`feat/lotes-alertas`, `feat/reset-password`, `feat/cambio-applicationId-play-store`,
`feat/dashboard-buscar-producto`, `acept-guia-despacho`, `fix/mensaje-error-sin-conexion`.

Se confirmó que **todas ya estaban integradas sin conflictos en `main`** (main = develop + 51 commits, incluye también `feat/scanner-busqueda-dashboard` y `feat/guia-usuario-interactiva`, mergeadas previamente y sin rama local remanente). No hizo falta resolver nada — se hizo:

```
git checkout develop && git merge --ff-only main   # b099432 → 80fa4b1
git checkout test/load-test-kit && git merge --ff-only main
```

Esto trajo el schema real a `version = 8` (11 entidades, agrega `LoteEntity`, `es_perecedero` en `ProductoEntity`, `lote_id` en `MovimientoEntity`) — resolviendo el mismatch que tenía bloqueado el kit (`LoadTestSchema.kt` estaba verificado contra ese schema v8, no contra el v7 de `develop`).

También reemplazó el `CLAUDE.md` de la raíz por `.harness/CLAUDE.md` + `ESTADO.md`/`TASKS.md`/`MIGRACION_SUPABASE.md`/`PUENTE.md` (fuente de contexto a usar de ahora en más).

**Pendiente / no ejecutado:** borrar las 10 ramas ya redundantes (más `feat/paginacion-productos` y `feat/ui-polish`, también contenidas en `develop`) — se dejó a criterio del usuario.

---

## 1. S1 — Mapeo de schema (`LoadTestSchema.kt`)

Decisiones verificadas contra el repo (no asumidas de la spec original):

- **AppDatabase v8, 11 entidades** — confirmado tras la consolidación de ramas.
- **Realtime instalado pero no usado** — `SupabaseClient.kt` hace `install(Realtime)` pero no hay ningún `.channel()`/`.subscribe()` en la app. El techo de 200 conexiones no aplica hoy.
- **Un device = una sola empresa cargada** (falso el supuesto multi-tenant) — `AuthSessionEntity` es fila única y `AuthRepository.logout()`/refresh fallido llaman `db.clearAllTables()`. Se descartó `MultiTenantProbeTest.kt` del kit.
- **E4 "historial paginado" y E5 "offset profundo" no existen** — `MovimientoDao.observarPorProducto()` no pagina. E4 se redefinió como "historial completo sin paginar"; E5 se descartó.
- **E9 "búsqueda por nombre" no existe** — `ProductoDao` no tiene LIKE/FTS. Descartado.
- **E3 "stock bajo" ya optimizado de fábrica** — `observarBajoMinimo()` es `LEFT JOIN + GROUP BY + HAVING`, no subquery correlacionada (la hipótesis original de la spec ya no aplica, pero sigue siendo el escenario más caro por otro motivo — ver §4).

---

## 2. S2 — Seeder (`LoadTestSeeder.kt`)

Genera datasets deterministas (semilla fija 42L) por escalón, insert directo por SQL en una transacción + `ANALYZE`. Escalones (`Escalon.BARRIDO` = L0-L4; L5 excluido del barrido por defecto, ~20min de siembra):

| Escalón | Usuarios | Bodegas | Productos | Movimientos | Sync pendiente |
|---|---|---|---|---|---|
| L0 | 1 | 1 | 50 | 200 | 0 |
| L1 | 2 | 1 | 500 | 5.000 | 20 |
| L2 | 4 | 2 | 2.000 | 35.000 | 150 |
| L3 | 6 | 3 | 8.000 | 150.000 | 800 |
| L4 | 15 | 5 | 15.000 | 750.000 | 3.000 |
| L5 (no corrido) | 30 | 10 | 50.000 | 3.000.000 | 10.000 |

Smoke test (`SmokeSeederTest.kt`, sesión temporal, **ya borrado** — reemplazado por `RoomScaleProbeTest.kt`) — siembra de L2, validado en 2 dispositivos:

| Device | Tiempo de siembra L2 | Productos | Movimientos | Sync | Resultado |
|---|---|---|---|---|---|
| Moto G60 | 1.583 ms | 2.000 ✅ | 35.000 ✅ | 150 ✅ | PASS |
| Samsung S25 FE | 960 ms | 2.000 ✅ | 35.000 ✅ | 150 ✅ | PASS |

Ambos muy por debajo del presupuesto de 30s. (Nota: corrida de una sola vez, sin warmup — no es una comparativa de rendimiento rigurosa, solo confirma que el seeder funciona en ambos dispositivos.)

---

## 3. S3 — Barrido de escala (`RoomScaleProbeTest.kt`)

Mide E1-E4 (capa DAO) en modo **cold** (conexión Room recién abierta, una sola muestra) y **warm** (3 warmups + 10 muestras, percentiles) para cada escalón L0-L4. `PRODUCTO_ID = "prod-0"` (más "caliente" por el sesgo Pareto del seeder), `BODEGA_ID = "bod-0"`.

Escenarios:
- **E1** `ProductoDao.calcularStock(id)` — `SELECT COALESCE(SUM(cantidad),0) FROM movimientos WHERE producto_id=?`
- **E2** `ProductoDao.observarConStock(bodegaId)` — lista de Productos, `LEFT JOIN movimientos GROUP BY p.id`
- **E3** `ProductoDao.observarBajoMinimo(bodegaId)` — mismo JOIN + `HAVING stock < minimo` (badge de alertas / Dashboard)
- **E4** `MovimientoDao.observarPorProducto(id)` — historial completo de un producto, sin paginar

### 3.1 Samsung S25 FE — CSV completo

```
escalon,escenario,capa,modo,mediana_ms,p95_ms,max_ms,filas,heap_delta_kb
L0,E1_stock_producto,dao,cold,11.72,11.72,11.72,1,-934
L0,E1_stock_producto,dao,warm,2.90,4.69,4.69,1,215
L0,E2_lista_productos_stock,dao,cold,27.09,27.09,27.09,50,140
L0,E2_lista_productos_stock,dao,warm,12.74,13.64,13.64,50,28
L0,E3_stock_bajo,dao,cold,27.74,27.74,27.74,22,168
L0,E3_stock_bajo,dao,warm,12.82,17.60,17.60,22,28
L0,E4_historial_completo_sin_paginar,dao,cold,40.53,40.53,40.53,25,164
L0,E4_historial_completo_sin_paginar,dao,warm,32.87,41.82,41.82,25,24
L1,E1_stock_producto,dao,cold,36.92,36.92,36.92,1,415
L1,E1_stock_producto,dao,warm,3.54,6.93,6.93,1,4
L1,E2_lista_productos_stock,dao,cold,106.00,106.00,106.00,500,292
L1,E2_lista_productos_stock,dao,warm,46.05,53.26,53.26,500,28
L1,E3_stock_bajo,dao,cold,38.83,38.83,38.83,156,148
L1,E3_stock_bajo,dao,warm,37.30,44.18,44.18,156,32
L1,E4_historial_completo_sin_paginar,dao,cold,34.03,34.03,34.03,36,168
L1,E4_historial_completo_sin_paginar,dao,warm,24.51,36.76,36.76,36,24
L2,E1_stock_producto,dao,cold,20.13,20.13,20.13,1,665
L2,E1_stock_producto,dao,warm,2.37,6.21,6.21,1,8
L2,E2_lista_productos_stock,dao,cold,139.86,139.86,139.86,1000,418
L2,E2_lista_productos_stock,dao,warm,57.36,64.94,64.94,1000,16
L2,E3_stock_bajo,dao,cold,62.98,62.98,62.98,174,190
L2,E3_stock_bajo,dao,warm,43.64,48.51,48.51,174,32
L2,E4_historial_completo_sin_paginar,dao,cold,22.83,22.83,22.83,88,176
L2,E4_historial_completo_sin_paginar,dao,warm,16.97,23.11,23.11,88,24
L3,E1_stock_producto,dao,cold,21.56,21.56,21.56,1,664
L3,E1_stock_producto,dao,warm,6.32,8.53,8.53,1,-4
L3,E2_lista_productos_stock,dao,cold,193.55,193.55,193.55,2667,878
L3,E2_lista_productos_stock,dao,warm,131.73,136.04,136.04,2667,28
L3,E3_stock_bajo,dao,cold,124.62,124.62,124.62,475,279
L3,E3_stock_bajo,dao,warm,119.45,125.61,125.61,475,28
L3,E4_historial_completo_sin_paginar,dao,cold,27.16,27.16,27.16,77,173
L3,E4_historial_completo_sin_paginar,dao,warm,15.35,18.30,18.30,77,28
L4,E1_stock_producto,dao,cold,14.98,14.98,14.98,1,1354
L4,E1_stock_producto,dao,warm,2.24,2.48,2.48,1,4
L4,E2_lista_productos_stock,dao,cold,401.48,401.48,401.48,3000,974
L4,E2_lista_productos_stock,dao,warm,367.98,384.46,384.46,3000,24
L4,E3_stock_bajo,dao,cold,362.09,362.09,362.09,68,166
L4,E3_stock_bajo,dao,warm,353.20,369.82,369.82,68,28
L4,E4_historial_completo_sin_paginar,dao,cold,27.43,27.43,27.43,212,200
L4,E4_historial_completo_sin_paginar,dao,warm,24.02,30.25,30.25,212,24
```

### 3.2 Motorola Moto G60 — CSV completo

```
escalon,escenario,capa,modo,mediana_ms,p95_ms,max_ms,filas,heap_delta_kb
L0,E1_stock_producto,dao,cold,21.92,21.92,21.92,1,-286
L0,E1_stock_producto,dao,warm,4.59,5.40,5.40,1,-284
L0,E2_lista_productos_stock,dao,cold,71.22,71.22,71.22,50,172
L0,E2_lista_productos_stock,dao,warm,25.06,42.86,42.86,50,24
L0,E3_stock_bajo,dao,cold,54.00,54.00,54.00,22,163
L0,E3_stock_bajo,dao,warm,27.89,39.20,39.20,22,25
L0,E4_historial_completo_sin_paginar,dao,cold,58.44,58.44,58.44,25,161
L0,E4_historial_completo_sin_paginar,dao,warm,34.76,46.00,46.00,25,5
L1,E1_stock_producto,dao,cold,39.74,39.74,39.74,1,420
L1,E1_stock_producto,dao,warm,5.14,8.90,8.90,1,1
L1,E2_lista_productos_stock,dao,cold,102.34,102.34,102.34,500,334
L1,E2_lista_productos_stock,dao,warm,60.25,93.26,93.26,500,23
L1,E3_stock_bajo,dao,cold,94.15,94.15,94.15,156,199
L1,E3_stock_bajo,dao,warm,62.10,73.61,73.61,156,25
L1,E4_historial_completo_sin_paginar,dao,cold,59.18,59.18,59.18,36,162
L1,E4_historial_completo_sin_paginar,dao,warm,35.48,45.11,45.11,36,22
L2,E1_stock_producto,dao,cold,43.10,43.10,43.10,1,672
L2,E1_stock_producto,dao,warm,6.12,24.41,24.41,1,2
L2,E2_lista_productos_stock,dao,cold,143.53,143.53,143.53,1000,410
L2,E2_lista_productos_stock,dao,warm,88.58,94.67,94.67,1000,24
L2,E3_stock_bajo,dao,cold,79.32,79.32,79.32,174,172
L2,E3_stock_bajo,dao,warm,79.96,102.14,102.14,174,25
L2,E4_historial_completo_sin_paginar,dao,cold,45.58,45.58,45.58,88,171
L2,E4_historial_completo_sin_paginar,dao,warm,28.20,56.33,56.33,88,22
L3,E1_stock_producto,dao,cold,34.71,34.71,34.71,1,666
L3,E1_stock_producto,dao,warm,4.31,7.70,7.70,1,-13
L3,E2_lista_productos_stock,dao,cold,209.20,209.20,209.20,2667,875
L3,E2_lista_productos_stock,dao,warm,197.31,230.19,230.19,2667,0
L3,E3_stock_bajo,dao,cold,196.84,196.84,196.84,475,272
L3,E3_stock_bajo,dao,warm,189.09,199.45,199.45,475,25
L3,E4_historial_completo_sin_paginar,dao,cold,50.38,50.38,50.38,77,169
L3,E4_historial_completo_sin_paginar,dao,warm,31.38,44.97,44.97,77,22
L4,E1_stock_producto,dao,cold,43.36,43.36,43.36,1,1359
L4,E1_stock_producto,dao,warm,7.27,9.68,9.68,1,1
L4,E2_lista_productos_stock,dao,cold,604.54,604.54,604.54,3000,967
L4,E2_lista_productos_stock,dao,warm,551.52,560.28,560.28,3000,2
L4,E3_stock_bajo,dao,cold,533.99,533.99,533.99,68,158
L4,E3_stock_bajo,dao,warm,527.30,546.64,546.64,68,26
L4,E4_historial_completo_sin_paginar,dao,cold,44.31,44.31,44.31,212,195
L4,E4_historial_completo_sin_paginar,dao,warm,40.49,54.94,54.94,212,22
```

### 3.3 Comparativa resumida (modo warm, medianas en ms)

| Escenario | Device | L0 | L1 | L2 | L3 | L4 | Crecimiento L4/L0 |
|---|---|---|---|---|---|---|---|
| E1 stock producto | S25 FE | 2,9 | 3,5 | 2,4 | 6,3 | 2,2 | plano |
| E1 stock producto | Moto G60 | 4,6 | 5,1 | 6,1 | 4,3 | 7,3 | plano |
| **E2 lista productos+stock** | S25 FE | 12,7 | 46,1 | 57,4 | 131,7 | **368,0** | ~29× |
| **E2 lista productos+stock** | Moto G60 | 25,1 | 60,3 | 88,6 | 197,3 | **551,5** | ~22× |
| **E3 stock bajo** | S25 FE | 12,8 | 37,3 | 43,6 | 119,5 | **353,2** | ~27,5× |
| **E3 stock bajo** | Moto G60 | 27,9 | 62,1 | 80,0 | 189,1 | **527,3** | ~18,9× |
| E4 historial producto | S25 FE | 32,9 | 24,5 | 17,0 | 15,4 | 24,0 | plano |
| E4 historial producto | Moto G60 | 34,8 | 35,5 | 28,2 | 31,4 | 40,5 | plano |

---

## 4. Hallazgos clave

1. **E1 y E4 no escalan con el volumen de la base** — E1 porque es un `SUM` acotado a un producto, E4 porque filtra por `producto_id` indexado. Confirmado en ambos dispositivos, en los 5 escalones.
2. **E2 y E3 sí escalan casi linealmente con el total de movimientos de la bodega** — ambos hacen `LEFT JOIN productos-movimientos GROUP BY` sin acotar por producto. Es el candidato real de optimización futura (paginar `observarConStock`, o mantener `stock_actual` desnormalizado con triggers en vez de recalcular por SUM en cada carga).
3. **Ningún escenario cruza el umbral de "<1s" del DoD**, ni siquiera en L4 (750k movimientos, escalón ya extremo para una pyme) — pero en el Moto G60 el margen se reduce a la mitad (~550ms vs. ~370ms en el S25 FE). L5 (3M movimientos) no se corrió; con la tendencia observada, ahí sí podría acercarse o cruzar el segundo en hardware de gama media.
4. **Comparativa de hardware:** Moto G60 es consistentemente 1,5×-2× más lento que el S25 FE en E2/E3, con la brecha achicándose relativamente a mayor escala (la query domina sobre la diferencia de CPU/IO).
5. **Limitación conocida del seeder confirmada en los datos:** `filas` de E3 no crece monótonamente (22→156→174→475→**68** en ambos dispositivos) — con 60%/40% ENTRADA/SALIDA el stock deriva hacia arriba con el volumen, así que en L4 casi ningún producto queda bajo el mínimo. El tiempo medido en L4 sigue siendo válido (la query igual agrega todos los movimientos antes del `HAVING`), pero el conteo de filas hay que leerlo con ese contexto.

---

## 5. Pendiente

- **S3 capa Repository** (`@HiltAndroidTest`, ADR-015-D) — no iniciado. Requiere setup de test runner Hilt que hoy no existe en `androidTest/` (`ExampleInstrumentedTest.kt` es el único archivo previo, sin Hilt).
- Limpieza de las 12 ramas ya redundantes en el repo (ver §0).
- Evaluar correr L5 (3M movimientos) si se quiere confirmar el punto de quiebre real de E2/E3.
