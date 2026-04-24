package cl.stockflow.warehouse.data.repository

import cl.stockflow.warehouse.data.local.dao.AuthSessionDao
import cl.stockflow.warehouse.data.local.dao.BodegaDao
import cl.stockflow.warehouse.data.local.dao.MovimientoDao
import cl.stockflow.warehouse.data.local.dao.ProductoDao
import cl.stockflow.warehouse.data.local.entity.MovimientoEntity
import cl.stockflow.warehouse.data.local.entity.ProductoEntity
import cl.stockflow.warehouse.data.local.entity.TipoMovimiento
import cl.stockflow.warehouse.domain.model.ProductoConStock
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductoRepository @Inject constructor(
    private val productoDao: ProductoDao,
    private val movimientoDao: MovimientoDao,
    private val authSessionDao: AuthSessionDao,
    private val bodegaDao: BodegaDao
) {
    suspend fun obtenerContexto(): Pair<String, String>? {
        val sesion = authSessionDao.obtenerSesion() ?: return null
        if (sesion.bodega_id.isBlank()) return null
        return sesion.empresa_id to sesion.bodega_id
    }

    fun observarProductos(bodegaId: String): Flow<List<ProductoConStock>> =
        productoDao.observarConStock(bodegaId)

    suspend fun obtenerPorId(id: String): ProductoEntity? = productoDao.obtenerPorId(id)

    suspend fun crear(
        empresa_id: String,
        bodega_id: String,
        nombre: String,
        descripcion: String?,
        sku: String?,
        precio: Double,
        stock_minimo: Int,
        stock_inicial: Int = 0
    ): Result<Unit> {
        if (productoDao.contarConNombre(bodega_id, nombre) > 0)
            return Result.failure(Exception("Ya existe un producto con ese nombre en esta bodega"))
        return try {
            val producto = ProductoEntity(
                empresa_id = empresa_id,
                bodega_id = bodega_id,
                nombre = nombre.trim(),
                descripcion = descripcion?.trim()?.ifBlank { null },
                sku = sku?.trim()?.ifBlank { null },
                precio = precio,
                stock_minimo = stock_minimo,
                synced = false
            )
            productoDao.insertar(producto)
            if (stock_inicial > 0) {
                movimientoDao.insertar(
                    MovimientoEntity(
                        producto_id = producto.id,
                        tipo = TipoMovimiento.ENTRADA,
                        cantidad = stock_inicial,
                        nota = "Stock inicial"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun actualizar(producto: ProductoEntity): Result<Unit> {
        if (productoDao.contarConNombre(producto.bodega_id, producto.nombre, producto.id) > 0)
            return Result.failure(Exception("Ya existe un producto con ese nombre en esta bodega"))
        return try {
            productoDao.actualizar(producto.copy(synced = false, updated_at = Date()))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminar(producto: ProductoEntity): Result<Unit> = try {
        productoDao.eliminar(producto)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
