package cl.stockflow.warehouse.data.local.dao

import androidx.room.*
import cl.stockflow.warehouse.data.local.entity.ProductoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(producto: ProductoEntity)

    @Update
    suspend fun actualizar(producto: ProductoEntity)

    @Delete
    suspend fun eliminar(producto: ProductoEntity)

    @Query("SELECT * FROM productos WHERE id = :id")
    suspend fun obtenerPorId(id: String): ProductoEntity?

    @Query("SELECT * FROM productos WHERE empresa_id = :empresaId ORDER BY nombre ASC")
    fun observarPorEmpresa(empresaId: String): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE bodega_id = :bodegaId ORDER BY nombre ASC")
    fun observarPorBodega(bodegaId: String): Flow<List<ProductoEntity>>

    // stock calculado desde movimientos
    @Query("SELECT COALESCE(SUM(cantidad), 0) FROM movimientos WHERE producto_id = :productoId")
    suspend fun calcularStock(productoId: String): Int

    @Query("SELECT * FROM productos WHERE synced = 0")
    suspend fun obtenerNoSincronizados(): List<ProductoEntity>
}
