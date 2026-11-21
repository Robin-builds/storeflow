package cl.storeflow.warehouse.data.sync

import cl.storeflow.warehouse.data.local.entity.OperacionSync
import cl.storeflow.warehouse.data.local.entity.SyncEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Calcula qué ítems de la cola no se deben enviar (contracts/sync-cola.md).
 * Solo un INSERT retenido arrastra a sus dependientes: con un UPDATE el padre ya existe
 * en el servidor, y sus movimientos deben poder entrar al stock (FR-028).
 */
object ResolverRetencion {

    fun resolver(cola: List<SyncEntity>): Set<String> {
        val ordenada = cola.sortedBy { it.created_at }
        val retenidos = ordenada.filter { it.rechazo_codigo != null }.mapTo(mutableSetOf()) { it.id }
        val insertsRetenidos = ordenada
            .filter { it.id in retenidos && it.operacion == OperacionSync.INSERT }
            .mapTo(mutableSetOf()) { it.entidad_tipo to it.entidad_id }

        // Bodega → producto → lote → movimiento: se repite hasta que no cambie nada,
        // porque la cola puede traer a un dependiente antes que a su padre.
        do {
            var cambio = false
            for (item in ordenada) {
                if (item.id in retenidos || !dependeDeRetenido(item, insertsRetenidos)) continue
                retenidos += item.id
                if (item.operacion == OperacionSync.INSERT) {
                    insertsRetenidos += item.entidad_tipo to item.entidad_id
                }
                cambio = true
            }
        } while (cambio)

        ordenada.forEachIndexed { indice, item ->
            val esDeleteBodega = item.entidad_tipo == "bodegas" && item.operacion == OperacionSync.DELETE
            if (esDeleteBodega && ordenada.take(indice).any { it.entidad_tipo == "productos" }) {
                retenidos += item.id
            }
        }
        return retenidos
    }

    private fun dependeDeRetenido(
        item: SyncEntity,
        insertsRetenidos: Set<Pair<String, String>>
    ): Boolean {
        fun retenido(tabla: String, id: String?) = id != null && (tabla to id) in insertsRetenidos
        if (item.operacion == OperacionSync.DELETE) return false
        return when (item.entidad_tipo) {
            "productos" -> retenido("bodegas", campo(item, "bodega_id"))
            "lotes" -> retenido("productos", campo(item, "producto_id"))
            "producto_atributos" -> retenido("productos", item.entidad_id)
            "movimientos" -> retenido("productos", campo(item, "producto_id")) ||
                retenido("lotes", campo(item, "lote_id"))
            else -> false
        }
    }

    internal fun campo(item: SyncEntity, nombre: String): String? = try {
        ((Json.parseToJsonElement(item.payload) as? JsonObject)?.get(nombre) as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
    } catch (e: IllegalArgumentException) {
        null
    }
}
