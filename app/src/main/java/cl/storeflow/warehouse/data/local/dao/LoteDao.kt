package cl.storeflow.warehouse.data.local.dao

import androidx.room.*
import cl.storeflow.warehouse.data.local.entity.LoteEntity
import cl.storeflow.warehouse.domain.model.LoteConStock
import kotlinx.coroutines.flow.Flow

@Dao
interface LoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(lote: LoteEntity)

    @Update
    suspend fun actualizar(lote: LoteEntity)

    @Delete
    suspend fun eliminar(lote: LoteEntity)

    @Query("SELECT * FROM lotes WHERE id = :id")
    suspend fun obtenerPorId(id: String): LoteEntity?

    @Query("SELECT * FROM lotes WHERE producto_id = :productoId ORDER BY fecha_caducidad ASC")
    fun observarPorProducto(productoId: String): Flow<List<LoteEntity>>

    // Lotes con stock residual > 0, ordenados por caducidad más próxima primero (orden FEFO)
    @Query("""
        SELECT l.id, l.producto_id, l.empresa_id, l.numero_lote, l.fecha_caducidad,
               l.synced, l.synced_at, l.created_at, l.updated_at,
               COALESCE(SUM(m.cantidad), 0) AS stock_actual
        FROM lotes l
        LEFT JOIN movimientos m ON m.lote_id = l.id
        WHERE l.producto_id = :productoId
        GROUP BY l.id
        HAVING stock_actual > 0
        ORDER BY l.fecha_caducidad ASC
    """)
    suspend fun obtenerConStockFefo(productoId: String): List<LoteConStock>

    @Query("""
        SELECT l.id, l.producto_id, l.empresa_id, l.numero_lote, l.fecha_caducidad,
               l.synced, l.synced_at, l.created_at, l.updated_at,
               COALESCE(SUM(m.cantidad), 0) AS stock_actual
        FROM lotes l
        LEFT JOIN movimientos m ON m.lote_id = l.id
        WHERE l.empresa_id = :empresaId
        GROUP BY l.id
        HAVING stock_actual > 0
        ORDER BY l.fecha_caducidad ASC
    """)
    fun observarConStockPorEmpresa(empresaId: String): Flow<List<LoteConStock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(lotes: List<LoteEntity>)

    @Query("SELECT * FROM lotes WHERE synced = 0")
    suspend fun obtenerNoSincronizados(): List<LoteEntity>

    @Query("UPDATE lotes SET synced = 1, synced_at = :ahora WHERE id = :id")
    suspend fun marcarSincronizado(id: String, ahora: Long)
}
