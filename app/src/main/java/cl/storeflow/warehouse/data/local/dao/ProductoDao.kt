package cl.storeflow.warehouse.data.local.dao

import androidx.room.*
import cl.storeflow.warehouse.data.local.entity.ProductoEntity
import cl.storeflow.warehouse.domain.model.ProductoConStock
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

    @Query("""
        SELECT p.id, p.empresa_id, p.bodega_id, p.nombre, p.descripcion, p.sku,
               p.precio, p.stock_minimo, p.synced, p.synced_at, p.created_at, p.updated_at,
               COALESCE(SUM(m.cantidad), 0) AS stock_actual
        FROM productos p
        LEFT JOIN movimientos m ON m.producto_id = p.id
        WHERE p.id = :productoId
        GROUP BY p.id
    """)
    fun observarProductoConStock(productoId: String): Flow<ProductoConStock?>

    @Query("""
        SELECT p.id, p.empresa_id, p.bodega_id, p.nombre, p.descripcion, p.sku,
               p.precio, p.stock_minimo, p.synced, p.synced_at, p.created_at, p.updated_at,
               COALESCE(SUM(m.cantidad), 0) AS stock_actual
        FROM productos p
        LEFT JOIN movimientos m ON m.producto_id = p.id
        WHERE p.bodega_id = :bodegaId
        GROUP BY p.id
        HAVING stock_actual < p.stock_minimo AND p.stock_minimo > 0
        ORDER BY (CAST(stock_actual AS REAL) / p.stock_minimo) ASC
    """)
    fun observarBajoMinimo(bodegaId: String): Flow<List<ProductoConStock>>

    @Query("UPDATE productos SET synced = 1, synced_at = :ahora WHERE id = :id")
    suspend fun marcarSincronizado(id: String, ahora: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(productos: List<ProductoEntity>)

    @Query("SELECT * FROM productos WHERE synced = 0")
    suspend fun obtenerNoSincronizados(): List<ProductoEntity>

    @Query("SELECT COUNT(*) FROM productos")
    suspend fun contarTodos(): Int

    @Query("SELECT COUNT(*) FROM productos WHERE bodega_id = :bodegaId")
    suspend fun contarPorBodega(bodegaId: String): Int

    @Query("SELECT * FROM productos WHERE bodega_id = :bodegaId")
    suspend fun obtenerListaPorBodega(bodegaId: String): List<ProductoEntity>

    @Query("UPDATE productos SET bodega_id = :bodegaIdDestino, updated_at = :ahora WHERE bodega_id = :bodegaIdOrigen")
    suspend fun transferirABodega(bodegaIdOrigen: String, bodegaIdDestino: String, ahora: Long = System.currentTimeMillis())

    @Query("SELECT * FROM productos WHERE id IN (:ids)")
    suspend fun obtenerPorIds(ids: List<String>): List<ProductoEntity>

    @Query("UPDATE productos SET bodega_id = :bodegaDestino, updated_at = :ahora, synced = 0 WHERE id IN (:ids)")
    suspend fun transferirSeleccionadosABodega(ids: List<String>, bodegaDestino: String, ahora: Long = System.currentTimeMillis())
}
