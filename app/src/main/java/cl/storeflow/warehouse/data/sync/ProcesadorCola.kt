package cl.storeflow.warehouse.data.sync

import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.OperacionSync
import cl.storeflow.warehouse.data.local.entity.SyncEntity
import timber.log.Timber
import javax.inject.Inject

data class ResultadoCola(val hayQueReintentar: Boolean)

/**
 * Recorre la cola por `created_at` y aplica contracts/sync-cola.md. Un ítem retenido no se
 * envía, no se borra y no provoca `retry`. Solo un 4xx permanente de una entidad que no sea
 * `movimientos` ni `lotes` se descarta, tras agotar sus reintentos.
 */
class ProcesadorCola @Inject constructor(private val syncDao: SyncDao) {

    suspend fun procesar(
        enviar: suspend (SyncEntity) -> RespuestaPush,
        alSincronizar: suspend (SyncEntity) -> Unit,
        usuarioBloqueado: Boolean = false
    ): ResultadoCola {
        val cola = syncDao.obtenerCola()
        val restantes = cola.toMutableList()
        var bloqueados = ResolverRetencion.resolver(restantes)
        var hayQueReintentar = false

        for (item in cola) {
            // El DELETE de bodega depende de lo que ya salió de la cola en esta misma pasada.
            if (esDeleteBodega(item)) bloqueados = ResolverRetencion.resolver(restantes)
            if (item.id in bloqueados) continue
            if (usuarioBloqueado && !enviableBloqueado(item, restantes)) continue

            val clase = ClasificadorRechazo.clasificar(enviar(item), item.entidad_tipo, item.operacion)
            when (clase) {
                ClaseRespuesta.Exito, ClaseRespuesta.YaExiste -> {
                    alSincronizar(item)
                    syncDao.eliminar(item)
                    restantes.removeAll { it.id == item.id }
                }
                is ClaseRespuesta.RechazoPlan -> {
                    bloqueados = retener(item, clase.codigo, restantes)
                }
                is ClaseRespuesta.PermanenteRetenido -> {
                    bloqueados = retener(item, clase.codigo, restantes)
                }
                ClaseRespuesta.Transitorio -> {
                    syncDao.incrementarReintentos(item.id)
                    hayQueReintentar = true
                }
                ClaseRespuesta.DependenciaPendiente -> {
                    hayQueReintentar = true
                }
                ClaseRespuesta.PermanenteDescartable -> {
                    if (item.reintentos >= MAX_REINTENTOS) {
                        Timber.e("SYNC: descartado tras $MAX_REINTENTOS reintentos — id=${item.id}")
                        syncDao.eliminar(item)
                        restantes.removeAll { it.id == item.id }
                    } else {
                        syncDao.incrementarReintentos(item.id)
                        hayQueReintentar = true
                    }
                }
            }
        }
        return ResultadoCola(hayQueReintentar)
    }

    private suspend fun retener(
        item: SyncEntity,
        codigo: String,
        restantes: MutableList<SyncEntity>
    ): Set<String> {
        Timber.w("SYNC: retenido $codigo — ${item.entidad_tipo}/${item.operacion} id=${item.id}")
        syncDao.marcarRechazo(item.id, codigo)
        val indice = restantes.indexOfFirst { it.id == item.id }
        restantes[indice] = item.copy(rechazo_codigo = codigo)
        return ResolverRetencion.resolver(restantes)
    }

    /**
     * Un usuario bloqueado solo envía INSERT de lotes y movimientos, y no los que dependan
     * de un INSERT de producto o lote que siga en la cola (FR-029).
     */
    private fun enviableBloqueado(item: SyncEntity, restantes: List<SyncEntity>): Boolean {
        if (item.operacion != OperacionSync.INSERT) return false
        if (item.entidad_tipo != "lotes" && item.entidad_tipo != "movimientos") return false

        fun enCola(tabla: String, id: String?) = id != null && restantes.any {
            it.entidad_tipo == tabla && it.operacion == OperacionSync.INSERT && it.entidad_id == id
        }
        if (enCola("productos", ResolverRetencion.campo(item, "producto_id"))) return false
        return item.entidad_tipo == "lotes" || !enCola("lotes", ResolverRetencion.campo(item, "lote_id"))
    }

    private fun esDeleteBodega(item: SyncEntity) =
        item.entidad_tipo == "bodegas" && item.operacion == OperacionSync.DELETE

    private companion object {
        const val MAX_REINTENTOS = 3
    }
}
