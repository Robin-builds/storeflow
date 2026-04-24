package cl.stockflow.warehouse.data.local.dao

import androidx.room.*
import cl.stockflow.warehouse.data.local.entity.ProductoEntity
import cl.stockflow.warehouse.domain.model.ProductoConStock
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

    @Query("SELECT COUNT(*) FROM productos WHERE bodega_id = :bodegaId AND LOWER(nombre) = LOWER(:nombre) AND id != :excludeId")
    suspend fun contarConNombre(bodegaId: String, nombre: String, excludeId: String = ""): Int

    // stock calculado desde movimientos
    @Query("SELECT COALESCE(SUM(cantidad), 0) FROM movimientos WHERE producto_id = :productoId")
    suspend fun calcularStock(productoId: String): Int

    @Query("""
        SELECT p.id, p.empresa_id, p.bodega_id, p.nombre, p.descripcion, p.sku,
               p.precio, p.stock_minimo, p.synced, p.synced_at, p.created_at, p.updated_at,
               COALESCE(SUM(m.cantidad), 0) AS stock_actual
        FROM productos p
        LEFT JOIN movimientos m ON m.producto_id = p.id
        WHERE p.bodega_id = :bodegaId
        GROUP BY p.id
        ORDER BY p.nombre ASC
    """)
    fun observarConStock(bodegaId: String): Flow<List<ProductoConStock>>

    @Query("SELECT * FROM productos WHERE synced = 0")
    suspend fun obtenerNoSincronizados(): List<ProductoEntity>
}
