package cl.storeflow.warehouse.loadtest

/**
 * Único punto de acoplamiento entre el kit y el schema real de Room (SPEC 15 v2).
 *
 * Todo es String crudo a propósito: el seeder (S2) inserta por SQL directo
 * (ADR-015-B) y no puede usar las data classes.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * DECISIONES DE S1 — verificadas contra el repo en esta sesión, no asumidas.
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * S1 (schema) — CONFIRMADO tal como lo declara la spec:
 *   AppDatabase v8, 11 entidades, package cl.storeflow.warehouse.
 *
 * S6 (¿usa realtime-kt?) — INSTALADO PERO NO USADO:
 *   SupabaseClient.kt hace install(Realtime), pero no hay un solo .channel(),
 *   .subscribe() ni postgresChangeFlow en toda la app. El techo de 200
 *   conexiones realtime de la sección 8.2/C8 NO aplica hoy: no hay canales
 *   abiertos. Si se activa realtime en el futuro, revisar C8 de nuevo.
 *
 * S7 (¿un device = un tenant?) — FALSO, con evidencia directa:
 *   - AuthSessionEntity es una tabla de una sola fila (@PrimaryKey id: Int = 1).
 *   - AuthRepository.logout() y el path de refresh-token fallido llaman
 *     db.clearAllTables().
 *   Un device NUNCA tiene más de una empresa cargada localmente: un contador
 *   que atiende 10 clientes hace logout/login y borra todo entre uno y otro.
 *   DECISIÓN: se descarta EJE-6, E10, MultiTenantProbeTest y EB-16 del kit.
 *   No se construye MultiTenantProbeTest.kt en S4.
 *
 * ADR-015-D (DAO vs Repository) — HiltAndroidTest, no construcción a mano:
 *   ProductoRepository tiene 8 dependencias @Inject (incluye SyncTrigger, que
 *   a su vez depende de WorkManager). Construirlo a mano no es viable con un
 *   constructor tan grande. Los tests de capa Repository en S3 usan
 *   @HiltAndroidTest.
 *
 * Escenarios de la spec sin función real detrás — redefinidos o descartados:
 *   - E4 "historial paginado" y E5 "offset profundo": NO EXISTEN. Room no
 *     tiene ningún método con LIMIT/OFFSET sobre movimientos.
 *     MovimientoDao.observarPorProducto() trae el historial COMPLETO sin
 *     paginar. Se redefine E4 como "historial completo, sin paginar" — el
 *     riesgo real medido no es el costo de un OFFSET grande, sino que cada
 *     apertura de esa pantalla carga TODAS las filas del producto sin límite.
 *     E5 se descarta: no hay OFFSET que medir.
 *   - E9 "búsqueda por nombre": NO EXISTE. ProductoDao no tiene ningún método
 *     de búsqueda (ni LIKE ni FTS). Se descarta; EB-13 no aplica todavía.
 *   - E3 "stock bajo": OJO, la hipótesis de EB-01/EB-02 y la Opción C de
 *     ADR-015-C (GROUP BY en vez de subquery correlacionada) YA ESTÁN
 *     aplicadas de fábrica. ProductoDao.observarBajoMinimo() es un
 *     LEFT JOIN + GROUP BY + HAVING, no una subquery correlacionada por fila.
 *     El prior de "🔴 candidato seguro" baja: sigue siendo el escenario más
 *     caro (agrega TODOS los movimientos de la bodega por cada apertura del
 *     Dashboard), pero no por el motivo que asumía la spec.
 *
 * ANTES DE CORRER (S2 en adelante): re-verificar cada constante contra
 * data/local/entity/ si el schema cambia. Un nombre mal escrito falla en
 * runtime con "no such column", no en compilación.
 */
object LoadTestSchema {

    const val TABLA_EMPRESAS    = "empresas"
    const val TABLA_USUARIOS    = "usuarios"
    const val TABLA_BODEGAS     = "bodegas"
    const val TABLA_PRODUCTOS   = "productos"
    const val TABLA_MOVIMIENTOS = "movimientos"
    const val TABLA_SYNC        = "sync_queue"

    // ── INSERTs. El orden de los '?' define el orden de bind en el seeder (S2).
    //    Columnas verificadas contra data/local/entity/*.kt línea por línea.
    //    Boolean -> INTEGER (0/1) y Date -> INTEGER (millis epoch) via
    //    DateConverters; los enums (TipoMovimiento, OperacionSync) se guardan
    //    como el String de su .name (ej. "ENTRADA", "INSERT").

    // Todas las columnas parametrizadas (nada de literales inline como '1' o
    // 'INSERT' mezclados con '?'): el seeder hace bind en el mismo orden en
    // que aparecen las columnas, sin tener que llevar la cuenta de qué
    // posición es literal y cuál es placeholder entre dos archivos.

    const val INSERT_EMPRESA =
        "INSERT INTO $TABLA_EMPRESAS (id, nombre, rut, synced, synced_at, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?)"

    const val INSERT_USUARIO =
        "INSERT INTO $TABLA_USUARIOS (id, empresa_id, nombre, email, rol, synced, synced_at, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"

    const val INSERT_BODEGA =
        "INSERT INTO $TABLA_BODEGAS (id, empresa_id, nombre, ubicacion, synced, synced_at, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

    const val INSERT_PRODUCTO =
        "INSERT INTO $TABLA_PRODUCTOS (id, empresa_id, bodega_id, nombre, descripcion, sku, precio, stock_minimo, es_perecedero, synced, synced_at, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    // tipo: 'ENTRADA' | 'SALIDA' | 'AJUSTE' (TipoMovimiento.name). nota y
    // lote_id se bindean NULL desde el seeder — LoteEntity no forma parte de
    // los ejes 1-6, fuera de alcance del barrido base.
    const val INSERT_MOVIMIENTO =
        "INSERT INTO $TABLA_MOVIMIENTOS (id, producto_id, tipo, cantidad, nota, lote_id, synced, synced_at, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    // operacion: 'INSERT' | 'UPDATE' | 'DELETE' (OperacionSync.name).
    // payload es NOT NULL en el schema real (a diferencia del placeholder de
    // la spec v2, que no lo contemplaba): el seeder bindea un JSON mínimo.
    const val INSERT_SYNC =
        "INSERT INTO $TABLA_SYNC (id, entidad_tipo, entidad_id, operacion, payload, reintentos, created_at, updated_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"

    /**
     * SQL literal de cada escenario, copiado del DAO correspondiente.
     *
     * Existe SOLO para correr EXPLAIN QUERY PLAN: Room no expone el SQL
     * generado en runtime. Es duplicación deliberada y controlada — si un DAO
     * cambia y esto no, el plan reportado deja de corresponder. Verificar en
     * cada corrida que el texto sigue coincidiendo con el DAO.
     *
     * Qué buscar en la salida:
     *   ❌ SCAN productos            → sin índice utilizable
     *   ❌ CORRELATED SCALAR SUBQUERY → se ejecuta una vez por fila externa
     *   ❌ USE TEMP B-TREE FOR ORDER BY → ordena en memoria, no por índice
     *   ✅ SEARCH ... USING INDEX ...
     *
     * E5, E9 y E10 no aparecen: no tienen función real detrás (ver KDoc de
     * la clase). E11 (pendientes de sync) tampoco: es un COUNT(*) trivial
     * sobre sync_queue, sin JOIN que valga la pena diagnosticar con plan.
     */
    val SQL_PARA_EXPLAIN = mapOf(
        // ProductoDao.calcularStock(productoId)
        "E1_stock_producto" to
            "SELECT COALESCE(SUM(cantidad), 0) FROM movimientos WHERE producto_id = ?",

        // ProductoDao.observarConStock(bodegaId) — pantalla más usada
        "E2_lista_productos_stock" to
            "SELECT p.id, p.empresa_id, p.bodega_id, p.nombre, p.descripcion, p.sku, " +
            "p.precio, p.stock_minimo, p.synced, p.synced_at, p.created_at, p.updated_at, " +
            "p.es_perecedero, COALESCE(SUM(m.cantidad), 0) AS stock_actual " +
            "FROM productos p LEFT JOIN movimientos m ON m.producto_id = p.id " +
            "WHERE p.bodega_id = ? GROUP BY p.id ORDER BY p.nombre ASC",

        // ProductoDao.observarBajoMinimo(bodegaId) — corre en cada apertura
        // del Dashboard. Ya es LEFT JOIN + GROUP BY + HAVING (no subquery
        // correlacionada, a diferencia de la hipótesis original de EB-01/02).
        "E3_stock_bajo" to
            "SELECT p.id, p.empresa_id, p.bodega_id, p.nombre, p.descripcion, p.sku, " +
            "p.precio, p.stock_minimo, p.synced, p.synced_at, p.created_at, p.updated_at, " +
            "p.es_perecedero, COALESCE(SUM(m.cantidad), 0) AS stock_actual " +
            "FROM productos p LEFT JOIN movimientos m ON m.producto_id = p.id " +
            "WHERE p.bodega_id = ? GROUP BY p.id " +
            "HAVING stock_actual < p.stock_minimo AND p.stock_minimo > 0 " +
            "ORDER BY (CAST(stock_actual AS REAL) / p.stock_minimo) ASC",

        // MovimientoDao.observarPorProducto(productoId) — REDEFINIDO respecto
        // de la spec v2: sin LIMIT/OFFSET porque el DAO real no pagina. Mide
        // el costo de traer el historial COMPLETO de un producto.
        "E4_historial_completo_sin_paginar" to
            "SELECT * FROM movimientos WHERE producto_id = ? ORDER BY created_at DESC"
    )
}
