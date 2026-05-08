package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.dao.MovimientoDao
import cl.storeflow.warehouse.data.local.dao.ProductoDao
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.MovimientoEntity
import cl.storeflow.warehouse.data.local.entity.TipoMovimiento
import cl.storeflow.warehouse.data.sync.SyncTrigger
import cl.storeflow.warehouse.data.sync.toSyncInsert
import cl.storeflow.warehouse.domain.model.Producto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovimientoRepository @Inject constructor(
    private val movimientoDao: MovimientoDao,
    private val productoDao: ProductoDao,
    private val syncDao: SyncDao,
    private val syncTrigger: SyncTrigger
) {
    fun observarProducto(productoId: String): Flow<Producto?> =
        productoDao.observarProductoConStock(productoId).map { it?.toDomain() }

    fun observarMovimientos(productoId: String): Flow<List<MovimientoEntity>> =
        movimientoDao.observarPorProducto(productoId)

    suspend fun registrarEntrada(productoId: String, cantidad: Int, nota: String?): Result<Unit> {
        if (nota.isNullOrBlank()) return Result.failure(Exception("Debe indicar la razón de la entrada"))
        if (cantidad <= 0) return Result.failure(Exception("La cantidad debe ser mayor a cero"))
        return try {
            val movimiento = MovimientoEntity(
                producto_id = productoId,
                tipo = TipoMovimiento.ENTRADA,
                cantidad = cantidad,
                nota = nota.trim()
            )
            movimientoDao.insertar(movimiento)
            syncDao.encolar(movimiento.toSyncInsert())
            syncTrigger.trigger()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registrarSalida(productoId: String, cantidad: Int, nota: String?): Result<Unit> {
        if (nota.isNullOrBlank()) return Result.failure(Exception("Debe indicar la razón de la salida"))
        if (cantidad <= 0) return Result.failure(Exception("La cantidad debe ser mayor a cero"))
        val stockActual = productoDao.calcularStock(productoId)
        if (stockActual < cantidad)
            return Result.failure(Exception("Stock insuficiente. Disponible: $stockActual"))
        return try {
            val movimiento = MovimientoEntity(
                producto_id = productoId,
                tipo = TipoMovimiento.SALIDA,
                cantidad = -cantidad,
                nota = nota.trim()
            )
            movimientoDao.insertar(movimiento)
            syncDao.encolar(movimiento.toSyncInsert())
            syncTrigger.trigger()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registrarAjuste(productoId: String, stockObjetivo: Int, nota: String?): Result<Unit> {
        if (nota.isNullOrBlank()) return Result.failure(Exception("Debe indicar la razón del ajuste"))
        if (stockObjetivo < 0) return Result.failure(Exception("El stock objetivo no puede ser negativo"))
        val stockActual = productoDao.calcularStock(productoId)
        val delta = stockObjetivo - stockActual
        if (delta == 0) return Result.failure(Exception("El stock ya es $stockActual, no hay cambio"))
        return try {
            val movimiento = MovimientoEntity(
                producto_id = productoId,
                tipo = TipoMovimiento.AJUSTE,
                cantidad = delta,
                nota = nota.trim()
            )
            movimientoDao.insertar(movimiento)
            syncDao.encolar(movimiento.toSyncInsert())
            syncTrigger.trigger()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
