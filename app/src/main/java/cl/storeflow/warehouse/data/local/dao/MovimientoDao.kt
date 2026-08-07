package cl.storeflow.warehouse.data.local.dao

import androidx.room.*
import cl.storeflow.warehouse.data.local.entity.MovimientoEntity
import cl.storeflow.warehouse.domain.model.MovimientoConProducto
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimientoDao {
    // solo INSERT — los movimientos son inmutables
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(movimiento: MovimientoEntity)

    @Query("SELECT * FROM movimientos WHERE producto_id = :productoId ORDER BY created_at DESC")
    fun observarPorProducto(productoId: String): Flow<List<MovimientoEntity>>

    @Query("""
        SELECT m.id, m.producto_id, m.tipo, m.cantidad, m.nota, m.created_at,
               p.nombre AS producto_nombre, p.sku AS producto_sku
        FROM movimientos m
        INNER JOIN productos p ON p.id = m.producto_id
        WHERE p.empresa_id = :empresaId
        ORDER BY m.created_at DESC
    """)
    fun observarPorEmpresa(empresaId: String): Flow<List<MovimientoConProducto>>

    @Query("UPDATE movimientos SET synced = 1, synced_at = :ahora WHERE id = :id")
    suspend fun marcarSincronizado(id: String, ahora: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movimientos: List<MovimientoEntity>)

    @Query("SELECT * FROM movimientos WHERE synced = 0")
    suspend fun obtenerNoSincronizados(): List<MovimientoEntity>

    @Query("SELECT COUNT(*) FROM movimientos")
    suspend fun contarTodos(): Int
}
