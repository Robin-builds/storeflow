package cl.storeflow.warehouse.loadtest

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlin.random.Random

/**
 * Genera datasets sintéticos deterministas directamente en SQLite.
 *
 * Determinista = misma semilla, mismos datos. Sin esto dos corridas no
 * comparan y los números no sirven para detectar regresiones.
 *
 * Sin parámetro `cantidadEmpresas`: S1 confirmó que un device nunca tiene más
 * de una empresa cargada localmente (AuthRepository.logout() y el path de
 * refresh-token fallido llaman db.clearAllTables()). EJE-6, E10 y
 * MultiTenantProbeTest quedan fuera del kit — ver LoadTestSchema.kt.
 */
class LoadTestSeeder(
    private val db: SupportSQLiteDatabase,
    semilla: Long = 42L
) {
    private val rnd = Random(semilla)

    /** Fecha base fija (2024-01-01). Evita que la fecha de corrida altere los datos. */
    private val baseMillis = 1_704_067_200_000L

    /**
     * Puebla la base para un escalón, con una sola empresa.
     *
     * @return el id de la empresa creada.
     */
    fun sembrar(escalon: Escalon): String {
        val empresaId = "emp-0"

        // Todo en UNA transacción: SQLite hace fsync por transacción, no por
        // insert. Es la diferencia entre 2 minutos y 3 horas.
        db.beginTransaction()
        try {
            sembrarEmpresa(empresaId)
            sembrarUsuarios(empresaId, escalon.usuarios)
            val bodegas = sembrarBodegas(empresaId, escalon.bodegas)
            val productos = sembrarProductos(empresaId, bodegas, escalon.productos)
            sembrarMovimientos(productos, escalon.movimientos)
            sembrarPendientesSync(escalon.pendientesSync)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // ANALYZE actualiza las estadísticas del planner. Sin esto SQLite puede
        // elegir table scan sobre índices y las mediciones salen falsamente malas.
        db.execSQL("ANALYZE")
        return empresaId
    }

    private fun sembrarEmpresa(empresaId: String) {
        db.compileStatement(LoadTestSchema.INSERT_EMPRESA).use { st ->
            st.bindString(1, empresaId)
            st.bindString(2, "Empresa Load Test")
            st.bindString(3, "76000000-7")
            st.bindLong(4, 1L) // synced
            st.bindLong(5, baseMillis) // synced_at
            st.bindLong(6, baseMillis) // created_at
            st.bindLong(7, baseMillis) // updated_at
            st.executeInsert()
        }
    }

    private fun sembrarUsuarios(empresaId: String, cantidad: Int) {
        db.compileStatement(LoadTestSchema.INSERT_USUARIO).use { st ->
            repeat(cantidad) { i ->
                st.clearBindings()
                st.bindString(1, "usr-$i")
                st.bindString(2, empresaId)
                st.bindString(3, "Usuario $i")
                st.bindString(4, "usuario$i@loadtest.cl")
                st.bindString(5, if (i == 0) "ADMIN" else "OPERADOR")
                st.bindLong(6, 1L) // synced
                st.bindLong(7, baseMillis) // synced_at
                st.bindLong(8, baseMillis) // created_at
                st.bindLong(9, baseMillis) // updated_at
                st.executeInsert()
            }
        }
    }

    private fun sembrarBodegas(empresaId: String, cantidad: Int): List<String> {
        val ids = mutableListOf<String>()
        db.compileStatement(LoadTestSchema.INSERT_BODEGA).use { st ->
            repeat(cantidad) { i ->
                val id = "bod-$i"
                st.clearBindings()
                st.bindString(1, id)
                st.bindString(2, empresaId)
                st.bindString(3, "Bodega $i")
                st.bindNull(4) // ubicacion
                st.bindLong(5, 1L) // synced
                st.bindLong(6, baseMillis) // synced_at
                st.bindLong(7, baseMillis) // created_at
                st.bindLong(8, baseMillis) // updated_at
                st.executeInsert()
                ids += id
            }
        }
        return ids
    }

    private fun sembrarProductos(
        empresaId: String,
        bodegas: List<String>,
        cantidad: Int
    ): List<String> {
        val ids = ArrayList<String>(cantidad)
        db.compileStatement(LoadTestSchema.INSERT_PRODUCTO).use { st ->
            repeat(cantidad) { i ->
                val id = "prod-$i"
                st.clearBindings()
                st.bindString(1, id)
                st.bindString(2, empresaId)
                // Round-robin: distribución uniforme, el caso favorable.
                // La distribución sesgada se prueba en EB-03 con un seeder aparte.
                st.bindString(3, bodegas[i % bodegas.size])
                st.bindString(4, "Producto ${i.toString().padStart(6, '0')}")
                st.bindNull(5) // descripcion
                st.bindString(6, "SKU-${i.toString().padStart(6, '0')}")
                st.bindLong(7, 5000L) // precio (CLP, entero desde MIGRATION_3_4)
                st.bindLong(8, 5L) // stock_minimo
                st.bindLong(9, 0L) // es_perecedero
                st.bindLong(10, 1L) // synced
                st.bindLong(11, baseMillis) // synced_at
                st.bindLong(12, baseMillis) // created_at
                st.bindLong(13, baseMillis) // updated_at
                st.executeInsert()
                ids += id
            }
        }
        return ids
    }

    /**
     * LIMITACIÓN CONOCIDA: con 60% ENTRADA (+10) / 40% SALIDA (-3), el valor
     * esperado por movimiento es +4,8. El stock de casi todos los productos
     * deriva hacia arriba con el volumen, así que E3 (stock_bajo,
     * stock_minimo=5) puede devolver muy pocas filas o ninguna en escalones
     * grandes. Si Medidor reporta `filas=0` en E3, NO es que la query sea
     * gratis: es que el dataset no genera productos bajo el mínimo. Antes de
     * interpretar el tiempo de E3 en S3/S6, verificar `filas` en el CSV.
     */
    private fun sembrarMovimientos(productos: List<String>, total: Int) {
        db.compileStatement(LoadTestSchema.INSERT_MOVIMIENTO).use { st ->
            var i = 0
            while (i < total) {
                // Pareto aproximado: 20% de los productos concentra 80% de los
                // movimientos. Es como se comporta un inventario real y castiga
                // más al índice que el reparto uniforme.
                val idx = if (rnd.nextInt(100) < 80) {
                    rnd.nextInt((productos.size / 5).coerceAtLeast(1))
                } else {
                    rnd.nextInt(productos.size)
                }
                val esEntrada = rnd.nextInt(100) < 60
                val fecha = baseMillis - rnd.nextLong(0, 63_072_000_000L) // ~2 años

                st.clearBindings()
                st.bindString(1, "mov-$i")
                st.bindString(2, productos[idx])
                st.bindString(3, if (esEntrada) "ENTRADA" else "SALIDA")
                st.bindLong(4, if (esEntrada) 10L else -3L)
                st.bindNull(5) // nota
                st.bindNull(6) // lote_id — fuera de alcance de los ejes 1-6
                st.bindLong(7, 1L) // synced
                st.bindLong(8, fecha) // synced_at
                st.bindLong(9, fecha) // created_at
                st.bindLong(10, fecha) // updated_at
                st.executeInsert()
                i++
            }
        }
    }

    /**
     * Cola de sincronización pendiente.
     *
     * Simula un device que estuvo sin señal. SyncDao.observarPendientes()
     * expone esto como Flow<Int> (un COUNT, no una lista) — si hay un badge
     * de "N pendientes" en el Dashboard observándolo, esa cola es carga
     * permanente en cada emisión. Ver E11 y EB-15.
     */
    private fun sembrarPendientesSync(cantidad: Int) {
        if (cantidad <= 0) return
        db.compileStatement(LoadTestSchema.INSERT_SYNC).use { st ->
            repeat(cantidad) { i ->
                st.clearBindings()
                st.bindString(1, "sync-$i")
                st.bindString(2, "MOVIMIENTO")
                st.bindString(3, "mov-$i")
                st.bindString(4, "INSERT")
                st.bindString(5, "{}") // payload NOT NULL en el schema real
                st.bindLong(6, 0L) // reintentos
                st.bindLong(7, baseMillis) // created_at
                st.bindLong(8, baseMillis) // updated_at
                st.executeInsert()
            }
        }
    }
}

/** Escalón de carga. Valores de la sección 4.2 de SPEC 15 v2. */
data class Escalon(
    val nombre: String,
    val usuarios: Int,
    val bodegas: Int,
    val productos: Int,
    val movimientos: Int,
    val pendientesSync: Int
) {
    companion object {
        val L0 = Escalon("L0", 1, 1, 50, 200, 0)
        val L1 = Escalon("L1", 2, 1, 500, 5_000, 20)
        val L2 = Escalon("L2", 4, 2, 2_000, 35_000, 150)
        val L3 = Escalon("L3", 6, 3, 8_000, 150_000, 800)
        val L4 = Escalon("L4", 15, 5, 15_000, 750_000, 3_000)
        val L5 = Escalon("L5", 30, 10, 50_000, 3_000_000, 10_000)

        /** L5 excluido del barrido por defecto: ~20 min de siembra. */
        val BARRIDO = listOf(L0, L1, L2, L3, L4)
    }
}
