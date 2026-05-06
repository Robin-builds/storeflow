package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.dao.AtributoTemplateDao
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.BodegaDao
import cl.storeflow.warehouse.data.local.dao.MovimientoDao
import cl.storeflow.warehouse.data.local.dao.ProductoAtributoDao
import cl.storeflow.warehouse.data.local.dao.ProductoDao
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.MovimientoEntity
import cl.storeflow.warehouse.data.local.entity.ProductoEntity
import cl.storeflow.warehouse.data.local.entity.TipoMovimiento
import cl.storeflow.warehouse.data.sync.SyncTrigger
import cl.storeflow.warehouse.data.sync.productoAtributosSyncItem
import cl.storeflow.warehouse.data.sync.toSyncDelete
import cl.storeflow.warehouse.data.sync.toSyncInsert
import cl.storeflow.warehouse.data.sync.toSyncUpdate
import cl.storeflow.warehouse.domain.model.Producto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductoRepository @Inject constructor(
    private val productoDao: ProductoDao,
    private val movimientoDao: MovimientoDao,
    private val authSessionDao: AuthSessionDao,
    private val bodegaDao: BodegaDao,
    private val syncDao: SyncDao,
    private val syncTrigger: SyncTrigger,
    private val productoAtributoDao: ProductoAtributoDao,
    private val atributoTemplateDao: AtributoTemplateDao
) {
    suspend fun obtenerContexto(): Pair<String, String>? {
        val sesion = authSessionDao.obtenerSesion() ?: return null
        if (sesion.bodega_id.isBlank()) return null
        return sesion.empresa_id to sesion.bodega_id
    }

    fun observarProductos(bodegaId: String): Flow<List<Producto>> =
        productoDao.observarConStock(bodegaId).map { list -> list.map { it.toDomain() } }

    fun observarBajoMinimo(bodegaId: String): Flow<List<Producto>> =
        productoDao.observarBajoMinimo(bodegaId).map { list -> list.map { it.toDomain() } }

    fun observarProducto(productoId: String): Flow<Producto?> =
        productoDao.observarProductoConStock(productoId).map { pcs ->
            if (pcs == null) null
            else {
                val atributos = productoAtributoDao.obtenerClavesValores(pcs.id)
                    .associate { it.clave to it.valor }
                pcs.toDomain(atributos)
            }
        }

    suspend fun obtenerPorId(id: String): ProductoEntity? = productoDao.obtenerPorId(id)

    suspend fun crear(
        empresa_id: String,
        bodega_id: String,
        nombre: String,
        descripcion: String?,
        sku: String?,
        precio: Int,
        stock_minimo: Int,
        stock_inicial: Int = 0,
        atributos: Map<String, String> = emptyMap()
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
            syncDao.encolar(producto.toSyncInsert())
            if (stock_inicial > 0) {
                val movimiento = MovimientoEntity(
                    producto_id = producto.id,
                    tipo = TipoMovimiento.ENTRADA,
                    cantidad = stock_inicial,
                    nota = "Stock inicial"
                )
                movimientoDao.insertar(movimiento)
                syncDao.encolar(movimiento.toSyncInsert())
            }
            guardarAtributos(producto.id, atributos)
            if (atributos.any { (_, v) -> v.isNotBlank() })
                syncDao.encolar(productoAtributosSyncItem(producto.id, atributos))
            syncTrigger.trigger()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun actualizar(
        producto: ProductoEntity,
        atributos: Map<String, String> = emptyMap()
    ): Result<Unit> {
        if (productoDao.contarConNombre(producto.bodega_id, producto.nombre, producto.id) > 0)
            return Result.failure(Exception("Ya existe un producto con ese nombre en esta bodega"))
        return try {
            val actualizado = producto.copy(synced = false, updated_at = Date())
            productoDao.actualizar(actualizado)
            syncDao.encolar(actualizado.toSyncUpdate())
            productoAtributoDao.eliminarPorProducto(actualizado.id)
            guardarAtributos(actualizado.id, atributos)
            syncDao.encolar(productoAtributosSyncItem(actualizado.id, atributos))
            syncTrigger.trigger()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun guardarAtributos(productoId: String, atributos: Map<String, String>) {
        val entities = atributos
            .filter { (_, valor) -> valor.isNotBlank() }
            .map { (templateId, valor) ->
                cl.storeflow.warehouse.data.local.entity.ProductoAtributoEntity(
                    producto_id = productoId,
                    template_id = templateId,
                    valor = valor.trim()
                )
            }
        if (entities.isNotEmpty()) productoAtributoDao.upsertAll(entities)
    }

    suspend fun eliminar(producto: ProductoEntity): Result<Unit> = try {
        productoDao.eliminar(producto)
        syncDao.encolar(producto.toSyncDelete())
        syncTrigger.trigger()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun eliminarVarios(ids: List<String>): Result<Unit> = try {
        val productos = productoDao.obtenerPorIds(ids)
        productos.forEach { producto ->
            productoDao.eliminar(producto)
            syncDao.encolar(producto.toSyncDelete())
        }
        syncTrigger.trigger()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun transferirSeleccionados(ids: List<String>, bodegaDestino: String): Result<Unit> = try {
        val ahora = System.currentTimeMillis()
        productoDao.transferirSeleccionadosABodega(ids, bodegaDestino, ahora)
        val productos = productoDao.obtenerPorIds(ids)
        productos.forEach { syncDao.encolar(it.toSyncUpdate()) }
        syncTrigger.trigger()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
