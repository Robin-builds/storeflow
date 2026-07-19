package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.dao.LoteDao
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.LoteEntity
import cl.storeflow.warehouse.data.sync.SyncTrigger
import cl.storeflow.warehouse.data.sync.toSyncInsert
import cl.storeflow.warehouse.domain.model.LoteConStock
import cl.storeflow.warehouse.domain.model.LoteProximoAVencer
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoteRepository @Inject constructor(
    private val loteDao: LoteDao,
    private val syncDao: SyncDao,
    private val syncTrigger: SyncTrigger
) {
    fun observarPorProducto(productoId: String): Flow<List<LoteEntity>> =
        loteDao.observarPorProducto(productoId)

    // Lotes con stock > 0, orden FEFO (caducidad más próxima primero)
    suspend fun obtenerConStockFefo(productoId: String): List<LoteConStock> =
        loteDao.obtenerConStockFefo(productoId)

    // Lotes con stock > 0 que vencen dentro de "dias" (incluye ya vencidos), orden FEFO
    fun observarProximosAVencer(bodegaId: String, dias: Int = 7): Flow<List<LoteProximoAVencer>> {
        val hasta = System.currentTimeMillis() + dias * 24 * 60 * 60 * 1000L
        return loteDao.observarProximosAVencer(bodegaId, hasta)
    }

    suspend fun crear(
        productoId: String,
        empresaId: String,
        fechaCaducidad: Date,
        numeroLote: String? = null
    ): Result<LoteEntity> = try {
        val lote = LoteEntity(
            producto_id = productoId,
            empresa_id = empresaId,
            numero_lote = numeroLote?.trim()?.ifBlank { null },
            fecha_caducidad = fechaCaducidad
        )
        loteDao.insertar(lote)
        syncDao.encolar(lote.toSyncInsert())
        syncTrigger.trigger()
        Result.success(lote)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
