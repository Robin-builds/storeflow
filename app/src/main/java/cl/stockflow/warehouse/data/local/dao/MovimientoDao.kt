package cl.stockflow.warehouse.data.local.dao

import androidx.room.*
import cl.stockflow.warehouse.data.local.entity.MovimientoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimientoDao {
    // solo INSERT — los movimientos son inmutables
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(movimiento: MovimientoEntity)

    @Query("SELECT * FROM movimientos WHERE producto_id = :productoId ORDER BY created_at DESC")
    fun observarPorProducto(productoId: String): Flow<List<MovimientoEntity>>

    @Query("SELECT * FROM movimientos WHERE synced = 0")
    suspend fun obtenerNoSincronizados(): List<MovimientoEntity>
}
