package cl.stockflow.warehouse.data.repository

import cl.stockflow.warehouse.data.local.dao.AuthSessionDao
import cl.stockflow.warehouse.data.local.dao.BodegaDao
import cl.stockflow.warehouse.data.local.dao.SyncDao
import cl.stockflow.warehouse.data.local.entity.BodegaEntity
import cl.stockflow.warehouse.data.sync.toSyncDelete
import cl.stockflow.warehouse.data.sync.toSyncInsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BodegaRepository @Inject constructor(
    private val bodegaDao: BodegaDao,
    private val authSessionDao: AuthSessionDao,
    private val syncDao: SyncDao
) {

    fun observarBodegas(): Flow<List<BodegaEntity>> = flow {
        val sesion = authSessionDao.obtenerSesion()
        if (sesion == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(bodegaDao.observarPorEmpresa(sesion.empresa_id))
    }

    suspend fun obtenerBodegaActiva(): BodegaEntity? {
        val sesion = authSessionDao.obtenerSesion() ?: return null
        return bodegaDao.obtenerPorId(sesion.bodega_id)
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
            val bodega = bodegaDao.obtenerPorId(id)
                ?: return Result.failure(Exception("Bodega no encontrada"))
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
