# Diagnóstico de estado — StoreFlow (Android)

**Fecha:** 26/10/2026
**Tipo de sesión:** solo lectura y diagnóstico. No se tocó código, no se hicieron commits, builds ni tests.
**Alcance:** repo `StockFlowv00` (git, Gradle, Room, `.harness/`, `docs/`, `supabase/`).
**No consultado:** servidor Supabase, Vercel, Play Console.

**Leyenda:**
- **[V]** verificado en el repo (archivo o git)
- **[E]** estimado o inferido, no comprobado
- **[?]** no lo sé
- **[D]** según `ESTADO.md`, no verificado por mí

> Hallazgo principal: **el DoD que se tomaba como vigente (`.harness/DEFINITION_OF_DONE.md`) no existe** [V]. Tampoco hay en el repo ningún ADR, specs 11–14 ni documentación de monetización [V].

---

## 1. Estado real

- `main` = `origin/main` en `3899553`, versionCode **4** / versionName **"1.0.3"** [V]. El único cambio funcional desde 1.0.2 es `DASHBOARD_URL`, que ahora apunta a la nueva cuenta Vercel `robin-builds` [V].
- El AAB 1.0.3 firmado está en `app/release/` sin trackear [V], y está **decidido no subirlo** hasta pasar a producción. En Play sigue la 1.0.2 [D].
- AppDatabase **v9**, migraciones 1→9 registradas en `DatabaseModule`, **11 entities**, `isMinifyEnabled = false` [V].
- **61 `@Test`** en total, contando 4 del kit de load test, que está sin trackear [V]. Hay tests de ViewModel (`ConfiguracionViewModelTest`, `UsuariosViewModelTest`) que CLAUDE.md no lista [V]. No se corrieron.
- Supabase: `SupabaseClient.kt` y `.mcp.json` apuntan a `quvkxpjstzssivsaqimu` [V].
- Ramas locales: solo `main` y `develop`. `develop` va 23 commits atrás de `main` y no tiene nada propio [V]. En remoto solo queda `fix/mensaje-error-sin-conexion`, ya mergeada [V].
- `ESTADO.md` se actualizó el 26/10 y está commiteado. Solo `.claude/settings.local.json` sigue modificado [V].
- Web: migrada el 26/10 a `storeflow-dashboard-bice.vercel.app`, con repo privado en GitHub y deploy automático [D, en ambos `ESTADO.md`; no verificado en Vercel].

---

## 2. Discrepancias con el snapshot (14/10)

| Ítem | Snapshot | Repo |
|---|---|---|
| Versión en `main` | 3 / 1.0.2 | **4 / 1.0.3** (commit `153f0a0`, 26/10) [V] |
| Último commit | `d70c4c9` (03/10) | `3899553`. Desde entonces hubo 2 merges: README (24/10) y URL del dashboard (26/10) [V] |
| `ESTADO.md` sin commitear | Sí | Commiteado en `34df7da` [V] |
| Archivos sin trackear | loadtest, `play-store/`, `.mcp.json` | Además: `.agents/`, `.claude/skills/`, `.vscode/`, `app/release/` (2 AAB), `skills-lock.json`, `supabase/.temp/`, `ui-dump.xml` [V] |
| Rama `test/load-test-kit` | Existe | **No existe** ni local ni en remoto. El reporte S1–S3 quedó en `main` (escrito el 22/08, commiteado el 26/10); el código del kit sigue sin trackear [V] |
| Ramas viejas sin borrar | Sí | Ya no están. Solo queda `develop`, que es redundante [V] |
| Tests | 50+ | 61 `@Test` (incluye 4 del kit sin trackear) [V] |
| Vercel | — | Cuenta nueva `robin-builds`. La URL vieja `stockflow-web-eight.vercel.app` quedó congelada en una cuenta perdida, y las versiones ≤1.0.2 siguen apuntando a ella [D] |
| `.harness/DEFINITION_OF_DONE.md` | Vigente | **No existe** [V] |
| ADR-014 y ADRs de monetización | Existen | **0 ADRs en el repo.** El código del kit cita "ADR-015-D", que tampoco existe [V]. No sé dónde viven [?] |
| Specs 11–14, tiers, reverse trial | En definición | Ninguna mención en el repo [V] |
| Policy de `sync_queue` | Rota | En la migración del repo es `auth.role() = 'authenticated'`, sin filtrar por empresa: **cualquier usuario autenticado ve las filas de otras empresas** [V en archivo; no verificado en servidor] |
| `ordenes_compra*` | Ausentes en el servidor | Tampoco están en la migración ni en Kotlin, así que es consistente [V] |
| `movimientos.empresa_id` | Usa subquery | Confirmado en la migración: la policy filtra con `producto_id in (select … where empresa_id = get_empresa_id())` [V] |
| Testers de la prueba cerrada | 1 aceptado al 06/10 | `ESTADO.md` no registra nada posterior [?] |

---

## 3. Drift documental

### Referencias al proyecto Supabase viejo (`eygbgykglovbivthyqfb`)
- `.harness/CLAUDE.md:208` todavía dice que la tabla `lotes` vive en el proyecto viejo. Es la **única referencia operativa** al proyecto viejo [V].
- Las demás menciones (`MIGRACION_SUPABASE.md`, `PUENTE.md`, el SQL de migración) son históricas y están bien [V].
- Código (`SupabaseClient.kt`) y `.mcp.json` están limpios [V]. Las env vars de Vercel apuntan al proyecto vigente según `ESTADO.md` del 26/10 [D].

### `.harness/CLAUDE.md`
- **Líneas 298–309:** manda a leer `03_DEFINITION_OF_DONE.md` de febrero (carpeta externa `manegenet_inventory_MSaas_v0.0.1/documentacion/`) "antes de cada fase", que es justo el DoD desactualizado. **CLAUDE.md apunta al DoD equivocado** [V].
- **Línea 261:** el bug de `contarConNombre` ya no existe (no aparece en `app/src`) [V].
- La tabla de tests dice 55, pero hoy hay 61 [V].
- Las listas de ViewModels, repositories y rutas omiten `HistorialMovimientos*` y `Ayuda*`, que `ESTADO.md` sí menciona [E, no revisado archivo por archivo].
- La convención dice "commits en inglés semántico", pero los commits del 26/10 están en español [V]. O se cambia la regla o se cumple.

### `.harness/ESTADO.md`
- **Línea 53** dice que la prueba cerrada está "en revisión de Google", pero la **línea 24** dice que se aprobó el 06/10 [V].
- **Línea 51** habla de ramas locales que ya no existen [V].
- **Línea 131**: "Paginación real Supabase (web): pospuesta". Se resolvió el 23/08 con las vistas `productos_con_stock` y `bodegas_resumen` [V, según `PUENTE.md`].
- **Sección 🌐 Web (líneas 141–150)** sigue listando `productos/importar` y ramas sin mergear. Eso contradice a `PUENTE.md`, que dice que el import CSV se eliminó el 23/08 [V].
- **Línea 139** dice que `DASHBOARD_URL` se cambió "en la rama `fix/url-dashboard-nueva-cuenta`", pero ya está en `main` [V].

### `.harness/TASKS.md`
- Dice que Proveedores "requiere migración Room (versión 9)". Sería la **v10** [V].
- Señala la rama `feat/historial-movimientos-android`, que ya no existe [V].

### `.harness/PUENTE.md`
- **Línea 25:** "verificar Vercel si no se hizo ya". Según `ESTADO.md` del 26/10 ya se hizo, pero el pendiente nunca se cerró [D].

### Esquema de Supabase sin versionar
- `supabase/migrations/` tiene un solo archivo, del 08/08 [V].
- Las vistas del 23/08 y cualquier cambio posterior **no están versionados en este repo** [V]. No revisé si están en `stockflow-web` [?].

### ADRs que en la práctica ya están resueltos
- **`StockSnapshot`**: el reporte `docs/reportes/2026-08-22-load-test-kit-s1-s3.md` concluye que ningún escenario supera el umbral de <1s, ni siquiera con 750k movimientos (~550 ms en el Moto G60) [V]. Los datos dicen "no por ahora". Falta escribirlo como ADR cerrado, condicionado a correr L5 o a acercarse a 1M de movimientos por bodega.
- **realtime-kt**: el plugin está declarado en Gradle e importado en `SupabaseClient.kt` (línea 6) [V]. No busqué suscripciones, pero estimo que no se usa [E]. En los hechos es un "no" que paga peso de dependencia.

### TODOs y pendientes huérfanos
- TODOs huérfanos en el código: **ninguno** [V].
- Pendientes huérfanos en docs:
  - S3 del kit de carga (capa Repository con `@HiltAndroidTest`, ADR-015-D): no iniciado [V].
  - "Limpieza de 12 ramas" en el reporte de carga: ya hecha [V].
  - Validar la galería en "Reportar problema" con 1.0.2: sigue sin hacerse [D].
  - El `user.email` de git en este repo sigue siendo el de relleno `tu@email.cl` [D].

---

## 4. Huecos del DoD vigente

**No hay DoD vigente.** Lo único que existe es el de febrero, fuera del repo, y CLAUDE.md lo sigue imponiendo. Los criterios que sí se usan en la práctica están repartidos en CLAUDE.md, ESTADO.md y el reporte de carga.

Lo que falta para tener un DoD que sirva de "biblia":

1. **El archivo mismo**: crear `.harness/DEFINITION_OF_DONE.md` y que CLAUDE.md apunte a ese, no al de febrero.
2. **Validación física con alcance definido**: qué dispositivo (Moto G60 como piso) y qué flujos mínimos (camino feliz, sin conexión, sync de ida y vuelta).
3. **Migración Room**: exigir upgrade real desde la versión publicada en Play, no instalación limpia. Se hizo en 7→8 y 8→9, pero no está escrito como criterio.
4. **Cambios en Supabase**:
   - migración versionada en `supabase/migrations/`;
   - RLS probado con 2 empresas distintas (aislamiento negativo, que hoy no se prueba);
   - `security_invoker = true` en vistas;
   - `PUENTE.md` actualizado en los dos repos.
5. **Rendimiento**: el "<1s" aparece en el reporte, pero no dice qué consultas, con qué volumen ni en qué dispositivo. Propuesta: E2/E3 bajo 1 s en L4 sobre el Moto G60.
6. **Tests**: hoy solo dice "TDD donde ya sea convención". Faltan mínimos por capa: un Repository con lógica de stock o sync debe tener test; las consultas DAO críticas, test instrumentado.
7. **Release**: hay checklist de Play repartido en `ESTADO.md`, pero no en el DoD. Falta:
   - bump de versión;
   - AAB generado desde un commit etiquetado (hoy no hay tags);
   - validación en el dispositivo;
   - decisión explícita sobre ProGuard y archivo de desofuscación.
8. **Invariantes como ítems verificables**:
   - stock derivado de movimientos, nunca guardado;
   - sin filtro por `empresa_id` en Kotlin (con un grep como chequeo);
   - movimientos solo con INSERT.
9. **Cierre documental**: `ESTADO.md` y `TASKS.md` actualizados, y rama borrada después del merge. Hoy pasa a medias.
10. **Specs sin DoD propio**: Proveedores, 11, 13 y la de monetización. Ninguna tiene criterios de aceptación en el repo.

---

## 5. Decisiones que bloquean el próximo spec y siguiente paso propuesto

### Proveedores UI
El único spec con contexto en el repo (`TASKS.md`). Lo bloquea una sola pregunta: **¿agenda o traza de costos?**
- **Agenda**: alcanza con `proveedor_id` nullable en `Producto` (1:N). Sin tabla nueva.
- **Traza de costos**: tabla N:N `producto_proveedores` (`producto_id`, `proveedor_id`, `precio_compra`, `sku_proveedor?`), con Room v10, sync push/pull y RLS. Es feature compleja y amerita `docs/plans/*.md`.

### Monetización
- Está bloqueada por una dependencia circular: cobrar B2B con factura exige DTE, pero el spec de DTE está congelado por ADR-014.
- Los ADRs relevantes (DTE, downgrade, sesiones múltiples por dispositivo) no existen en el repo [V].
- Además, no aplica hasta salir a producción, y eso depende de juntar 12 testers durante 14 días seguidos en la prueba cerrada.
- **Supuesto declarado:** no es el siguiente spec.

### Prerrequisito de seguridad, antes de cualquier spec
- Confirmar en el servidor si la policy de `sync_queue` es la de la migración. Si es así, hay una fuga de datos entre empresas.
- Estimo que Android usa la cola local de Room y nunca escribe en esa tabla del servidor [E]. Si es así, la salida más simple es eliminar la tabla o cerrarla del todo con `using (false)`.

### Siguiente paso propuesto (en orden)
1. Consulta de solo lectura vía MCP a `pg_policies` para confirmar la policy de `sync_queue` en el servidor.
2. Escribir `.harness/DEFINITION_OF_DONE.md` con los 10 puntos de la sección 4, y corregir el drift de CLAUDE.md, ESTADO.md y TASKS.md.
3. Decidir "agenda o costos" para Proveedores. Con eso el spec queda clasificado como liviano (agenda) o complejo (costos, con plan en `docs/plans/`).
