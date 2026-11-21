# Implementation Plan: Planes & Entitlements v1

**Branch**: sin rama propia todavía (feature `002-planes-entitlements`; el plan se generó estando en `fix/deuda-constitucion`, sin commit) | **Date**: 2026-11-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-planes-entitlements/spec.md`

**Clasificación** (`.harness/CLAUDE.md`): COMPLEJA. Requiere migración de Room, entidades nuevas, un patrón nuevo de retención en sync y cambios en dos repos (app y `stockflow-web`).

## Summary

Agregar planes Free / Standard / Premium por empresa, con la fuente de verdad en Postgres. El servidor calcula el plan efectivo al evaluar, aplica límites con triggers y bloquea a los usuarios excedentes en cada request. La app y el panel web solo reflejan ese plan. Lo que el servidor rechaza queda retenido en el dispositivo, sin perderse.

Decisiones que ordenan el diseño (detalle en [research.md](./research.md)):

- **Sin jobs**: plan efectivo, pérdida de Fundador y avisos se resuelven al evaluar.
- **Bloqueo de excedentes con pre-request de PostgREST**, no con RLS. Con RLS, un `PATCH` bloqueado responde 2xx con 0 filas y la app lo borraría de la cola como si hubiera funcionado.
- **El límite de historial se aplica en la presentación, nunca en RLS.** Las vistas de stock y el pull suman movimientos: filtrarlos por RLS falsearía el stock (Principio I).
- **La app se publica antes de activar el enforcement**, porque la versión actual descarta lo que el servidor rechaza 3 veces.
- **Ningún movimiento hecho con el plan vigente queda fuera del stock**, aunque su autor ya sea excedente: se acepta y se marca dentro de la ventana de FR-028.
- **Una sola vía de refresh de sesión**, y ninguna sesión vencida o cierre de sesión borra trabajo sin sincronizar (FR-030).
- **El servidor rechaza eliminar una bodega con productos** (FR-031).
- **La app solo conoce el tipo de plan.** Los valores se manejan del lado web y servidor, y la app recibe permisos ya resueltos (FR-011).

## Technical Context

**Language/Version**: Kotlin 2.0.21 (app), SQL/PLpgSQL en Postgres 17 (servidor), TypeScript/Deno (Edge Function), TypeScript/Next.js (`stockflow-web`)

**Primary Dependencies**: Jetpack Compose (BOM 2024.09.00), Room 2.6.1, Hilt 2.51.1, WorkManager 2.9.1, supabase-kt 1.4.6, Ktor. **Sin dependencias nuevas** (FR-027, NO-7).

**Storage**: Supabase Postgres (fuente de verdad), Room v9 → siguiente versión (caché offline, con FK locales desactivadas)

**Testing**: JUnit + MockK + coroutines-test (`testDebugUnitTest`, en `pre-push`), tests SQL en `supabase/tests/` contra Sandbox, validación en dispositivo físico

**Target Platform**: Android minSdk 27, targetSdk 36; panel web en Vercel. Supabase Sandbox `ihmseolpajzqnddldffc` → Producción `quvkxpjstzssivsaqimu`

**Project Type**: App móvil offline-first + backend Supabase + panel web de lectura

**Performance Goals**: el pre-request agrega una consulta indexada por request. El lock de límites serializa solo dentro de una empresa.

**Constraints**: offline-first, gracia de 7 días, cero pérdida de datos, sin contenido de compra en el APK, sin tablas en la publicación de Realtime

**Scale/Scope**: 11 empresas de prueba, 31 FR, 8 tablas de servidor nuevas, 2 tablas Room nuevas

## Constitution Check

*GATE: evaluado antes de Phase 0 y re-evaluado después de Phase 1. Constitución v1.2.0.*

| Regla | Cómo la respeta el diseño | Enforcement | Pre | Post |
|---|---|---|---|---|
| **I. Stock derivado** | Sin columna de stock. El límite de historial no toca RLS, vistas ni pull | `EsquemaInvariantesTest` + test SQL de stock tras downgrade | ✅ | ✅ |
| **II. Movimientos inmutables** | La marca de FR-028 vive en tabla aparte. Ningún flujo edita movimientos. El servidor rechaza eliminar una bodega con productos (FR-031). Eliminar un producto sigue siendo la deuda D3, fuera de esta feature | Trigger existente + trigger de M2 + `MovimientoDaoInmutableTest` | ⚠️ cascada de bodega | ✅ al aplicar M2 |
| **III. Aislamiento** | RPC sin `empresa_id`, tablas nuevas con lectura propia o sin acceso, helpers en esquema `private`, se cierra `usuario_insert_propio` | `aislamiento_empresas.sql` ampliado | ⚠️ agujero vivo | ✅ al aplicar M2 |
| **IV. CLP enteros** | La feature no guarda montos (NO-1, NO-3) | No aplica | ✅ | ✅ |
| **V. Identidad global** | Toda tabla nueva con PK `uuid`. En Room, `plan_cache` usa `empresa_id` y `avisos_plan` un UUID con índice único por empresa y momento | Tipo + `EsquemaInvariantesTest` | ✅ | ✅ |
| **Fallo capital** | Movimientos válidos de excedentes entran al stock (FR-028, FR-029); sesión y logout no borran trabajo (FR-030) | Tests de SC-014, SC-015 y SC-016 | ⚠️ defectos P1–P3 | ✅ |
| **Sandbox → Producción** | Migraciones sin editar, promovidas con el script | `promover-migracion.sh` | ✅ | ✅ |
| **Solo lo solicitado** | Este comando solo genera documentos. Código, migraciones y `stockflow-web` se tocan solo si el dueño lo pide | Revisión | ✅ | ✅ |
| **Puerta 4 (Room)** | Migración explícita + esquema exportado + upgrade real en dispositivo | Manual | ✅ | ✅ |

**Resultado: PASA.** Los ⚠️ son violaciones existentes que este plan cierra.

## Prerrequisitos (defectos actuales, sin corregir)

| ID | Defecto | Evidencia | Bloquea |
|---|---|---|---|
| **P1** | Abrir la app sin red con el token vencido borra toda la base local, incluida la cola. Además, tres actores rotan el mismo refresh token (R17) | `AuthRepository.kt:334-349`, `SyncWorker.kt:97-118` (revisado en código, no reproducido) | FR-023, FR-024, FR-029, FR-030 |
| **P2** | `SyncWorker` descarta cualquier ítem tras 3 fallos, incluso por red | `SyncWorker.kt:77-79` | FR-023, SC-011 |
| **P3** | Si se descarta el traslado de productos y luego pasa el `DELETE` de la bodega, Postgres borra en cascada productos y movimientos | `BodegaRepository.kt:76-86` | FR-005, FR-022, FR-031 |
| **P4** | `usuario_insert_propio` permite insertarse en cualquier empresa. Activa en Producción y Sandbox | `pg_policies`, 2026-11-21 | FR-009, FR-019, Principio III |

Si se corrigen antes por separado, sus tareas salen de esta feature.

## Pendientes con fecha de revisión

PEND-1 (cupo de bodegas sin señal) y PEND-2 (producto nuevo offline en bodega en solo lectura) quedan postergados por decisión del dueño, con **revisión en marzo de 2027**. Detalle y riesgo aceptado en [spec.md](./spec.md). Este plan no los implementa y no son una enmienda de la constitución.

## Project Structure

### Documentation (this feature)

```text
specs/002-planes-entitlements/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── entitlement-rpc.md
│   ├── codigos-rechazo.md
│   ├── operador.md
│   └── sync-cola.md
├── checklists/requirements.md
└── tasks.md             # lo genera /speckit-tasks
```

### Source Code

```text
supabase/
├── migrations/
│   ├── <ts>_planes_modelo.sql          # M1: catálogo, suscripciones, backfill, RPC
│   ├── <ts>_cerrar_insert_usuarios.sql # M2: elimina usuario_insert_propio (P4) y protege bodegas con productos (P3)
│   ├── <ts>_planes_solo_lectura.sql    # M3: US-2, conjuntos activos y solo lectura
│   ├── <ts>_planes_limites_bloqueo.sql # M4: US-3, límites y pre-request
│   ├── <ts>_planes_fundador.sql        # M5: US-4, operaciones del operador
│   └── <ts>_planes_avisos.sql          # M6: US-5
├── functions/registrar-usuario-empresa/index.ts  # devuelve el código de rechazo
└── tests/{bodega_sin_cascada,planes_modelo,planes_limites,planes_solo_lectura,planes_bloqueo,planes_fundador,planes_avisos}.sql

scripts/test-planes-concurrencia.sh     # conexiones simultáneas; pre-push corre cada .sql en una sola

app/src/main/java/cl/storeflow/warehouse/
├── data/local/entity/{PlanCacheEntity,AvisoPlanEntity,SyncEntity}.kt
├── data/local/dao/{PlanCacheDao,AvisoPlanDao,SyncDao}.kt
├── data/local/AppDatabase.kt                 # migración Room
├── data/repository/PlanRepository.kt         # nuevo
├── data/repository/{AuthRepository,BodegaRepository,MovimientoRepository}.kt
├── data/remote/SupabaseClient.kt             # sin auto-refresh propio
├── data/sync/{SyncWorker,PullWorker,GestorSesion,ProcesadorCola,ClasificadorRechazo,ResolverRetencion}.kt
├── domain/model/EvaluadorPlan.kt             # nuevo, puro
├── ui/plan/{PlanScreen,PlanViewModel,AvisoPlanBanner,BloqueoPlanScreen}.kt
└── ui/movimientos/HistorialMovimientosViewModel.kt

stockflow-web/ (repo hermano)
├── app/api/export/{movimientos,productos}/route.ts   # exige la capacidad export
├── app/dashboard/movimientos/page.tsx                # límite de historial
├── app/dashboard/productos/[id]/page.tsx             # límite de historial
└── utils/                                            # lectura del plan y estado bloqueado
```

**Structure Decision**: se mantienen `app`, `supabase/` y el repo web. La lógica va en Repositories (sin UseCase). Evaluador, clasificador y resolvedor son clases puras para probar reloj y rechazos sin Android.

**Corte por historia**: M1–M4 cubren el núcleo (US-1 a US-3). M5 (US-4) y M6 (US-5) son independientes y se pueden recortar.

## Rollout

1. Resolver P1–P3 e implementar la app. Sin la RPC desplegada, la app queda en estado "sin plan" y no bloquea nada.
2. Aplicar M1–M6 en **Sandbox**, correr tests y validar en dispositivo con el build `sandbox`.
3. Publicar la app y actualizar los dispositivos de prueba.
4. Desplegar el panel web compatible.
5. Promover las migraciones a Producción con confirmación explícita. M2 se puede promover antes y por separado, porque cierra P3 y P4 sin depender de los planes. **M1 inicia el trial de 30 días** de las 11 empresas (FR-026).

## Complexity Tracking

Sin violaciones de la constitución que justificar.
