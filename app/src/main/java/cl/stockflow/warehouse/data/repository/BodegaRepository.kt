package cl.stockflow.warehouse.data.repository

import cl.stockflow.warehouse.data.local.dao.AuthSessionDao
import cl.stockflow.warehouse.data.local.dao.BodegaDao
import cl.stockflow.warehouse.data.local.dao.ProductoDao
import cl.stockflow.warehouse.data.local.dao.SyncDao
import cl.stockflow.warehouse.data.local.entity.BodegaEntity
import cl.stockflow.warehouse.data.sync.toSyncDelete
import cl.stockflow.warehouse.data.sync.toSyncInsert
import cl.stockflow.warehouse.data.sync.toSyncUpdate
import cl.stockflow.warehouse.domain.model.Bodega
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodegaRepository @Inject constructor(
    private val bodegaDao: BodegaDao,
    private val authSessionDao: AuthSessionDao,
    private val syncDao: SyncDao,
    private val productoDao: ProductoDao
) {

    // flatMapLatest: cuando bodega_id cambia en sesión, esActiva se recalcula en todos los items
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observarBodegas(): Flow<List<Bodega>> = authSessionDao.observarSesion()
        .flatMapLatest { sesion ->
            if (sesion == null) flowOf(emptyList())
            else bodegaDao.observarPorEmpresa(sesion.empresa_id)
                .map { entities -> entities.map { it.toDomain(sesion.bodega_id) } }
        }

    suspend fun obtenerBodegaActiva(): Bodega? {
        val sesion = authSessionDao.obtenerSesion() ?: return null
        return bodegaDao.obtenerPorId(sesion.bodega_id)?.toDomain(sesion.bodega_id)
    }

    suspend fun crear(nombre: String, ubicacion: String?): Result<Unit> {
        return try {
            val sesion = authSessionDao.obtenerSesion()
                ?: return Result.failure(Exception("Sesión no encontrada"))
            val bodega = BodegaEntity(
                empresa_id = sesion.empresa_id,
                nombre = nombre.trim(),
                ubicacion = ubicacion?.trim()?.ifBlank { null }
            )
            bodegaDao.insertar(bodega)
            syncDao.encolar(bodega.toSyncInsert())
            Timber.d("BODEGA: creada id=${bodega.id}, nombre=${bodega.nombre}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "BODEGA: error al crear")
            Result.failure(e)
        }
    }

    suspend fun eliminar(id: String): Result<Unit> {
        return try {
            val sesion = authSessionDao.obtenerSesion()
                ?: return Result.failure(Exception("Sesión no encontrada"))
            val bodega = bodegaDao.obtenerPorId(id)
                ?: return Result.failure(Exception("Bodega no encontrada"))

            val cantidad = productoDao.contarPorBodega(id)
            if (cantidad > 0) {
                val destino = bodegaDao.obtenerMasAntiguaExcluyendo(sesion.empresa_id, id)
                    ?: return Result.failure(Exception("No hay bodega de destino disponible"))
                val productos = productoDao.obtenerListaPorBodega(id)
                val ahora = Date()
                productoDao.transferirABodega(id, destino.id)
                productos.forEach { producto ->
                    syncDao.encolar(
                        producto.copy(bodega_id = destino.id, updated_at = ahora).toSyncUpdate()
                    )
                }
                Timber.d("BODEGA: $cantidad productos transferidos a '${destino.nombre}'")
            }

            bodegaDao.eliminar(bodega)
            syncDao.encolar(bodega.toSyncDelete())
            Timber.d("BODEGA: eliminada id=$id")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "BODEGA: error al eliminar")
            Result.failure(e)
        }
    }

    suspend fun cambiarBodegaActiva(bodegaId: String): Result<Unit> {
        return try {
            authSessionDao.actualizarBodegaActiva(bodegaId)
            Timber.d("BODEGA: bodega activa cambiada a $bodegaId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "BODEGA: error al cambiar bodega activa")
            Result.failure(e)
        }
    }
}

private fun BodegaEntity.toDomain(bodegaActivaId: String): Bodega = Bodega(
    id = id,
    nombre = nombre,
    ubicacion = ubicacion,
    empresaId = empresa_id,
    esActiva = id == bodegaActivaId
)
