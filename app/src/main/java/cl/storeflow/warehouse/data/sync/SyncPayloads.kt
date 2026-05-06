package cl.storeflow.warehouse.data.sync

import cl.storeflow.warehouse.data.local.entity.AtributoTemplateEntity
import cl.storeflow.warehouse.data.local.entity.BodegaEntity
import cl.storeflow.warehouse.data.local.entity.MovimientoEntity
import cl.storeflow.warehouse.data.local.entity.OperacionSync
import cl.storeflow.warehouse.data.local.entity.ProductoEntity
import cl.storeflow.warehouse.data.local.entity.SyncEntity
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun ProductoEntity.toSyncInsert() = SyncEntity(
    entidad_tipo = "productos",
    entidad_id = id,
    operacion = OperacionSync.INSERT,
    payload = toSupabaseJson()
)

fun ProductoEntity.toSyncUpdate() = SyncEntity(
    entidad_tipo = "productos",
    entidad_id = id,
    operacion = OperacionSync.UPDATE,
    payload = toSupabaseJson()
)

fun ProductoEntity.toSyncDelete() = SyncEntity(
    entidad_tipo = "productos",
    entidad_id = id,
    operacion = OperacionSync.DELETE,
    payload = "{}"
)

private fun ProductoEntity.toSupabaseJson(): String = buildJsonObject {
    put("id", id)
    put("empresa_id", empresa_id)
    put("bodega_id", bodega_id)
    put("nombre", nombre)
    put("descripcion", descripcion)
    put("sku", sku)
    put("precio", precio)
    put("stock_minimo", stock_minimo)
    put("created_at", isoFmt.format(created_at))
    put("updated_at", isoFmt.format(updated_at))
}.toString()

fun MovimientoEntity.toSyncInsert() = SyncEntity(
    entidad_tipo = "movimientos",
    entidad_id = id,
    operacion = OperacionSync.INSERT,
    payload = toSupabaseJson()
)

fun BodegaEntity.toSyncInsert() = SyncEntity(
    entidad_tipo = "bodegas",
    entidad_id = id,
    operacion = OperacionSync.INSERT,
    payload = toSupabaseJson()
)

fun BodegaEntity.toSyncUpdate() = SyncEntity(
    entidad_tipo = "bodegas",
    entidad_id = id,
    operacion = OperacionSync.UPDATE,
    payload = toSupabaseJson()
)

fun BodegaEntity.toSyncDelete() = SyncEntity(
    entidad_tipo = "bodegas",
    entidad_id = id,
    operacion = OperacionSync.DELETE,
    payload = "{}"
)

private fun BodegaEntity.toSupabaseJson(): String = buildJsonObject {
    put("id", id)
    put("empresa_id", empresa_id)
    put("nombre", nombre)
    ubicacion?.let { put("ubicacion", it) }
    put("created_at", isoFmt.format(created_at))
    put("updated_at", isoFmt.format(updated_at))
}.toString()

fun AtributoTemplateEntity.toSyncInsert() = SyncEntity(
    entidad_tipo = "atributo_templates",
    entidad_id = id,
    operacion = OperacionSync.INSERT,
    payload = toSupabaseJson()
)

fun AtributoTemplateEntity.toSyncDelete() = SyncEntity(
    entidad_tipo = "atributo_templates",
    entidad_id = id,
    operacion = OperacionSync.DELETE,
    payload = "{}"
)

private fun AtributoTemplateEntity.toSupabaseJson(): String = buildJsonObject {
    put("id", id)
    put("empresa_id", empresa_id)
    put("clave", clave)
    put("etiqueta", etiqueta)
    put("tipo", tipo)
    put("obligatorio", obligatorio)
    put("orden", orden)
    put("created_at", isoFmt.format(created_at))
    put("updated_at", isoFmt.format(updated_at))
}.toString()

fun productoAtributosSyncItem(productoId: String, atributos: Map<String, String>) = SyncEntity(
    entidad_tipo = "producto_atributos",
    entidad_id = productoId,
    operacion = OperacionSync.UPDATE,
    payload = buildJsonArray {
        atributos.filter { (_, v) -> v.isNotBlank() }.forEach { (templateId, valor) ->
            addJsonObject {
                put("producto_id", productoId)
                put("template_id", templateId)
                put("valor", valor.trim())
            }
        }
    }.toString()
)

private fun MovimientoEntity.toSupabaseJson(): String = buildJsonObject {
    put("id", id)
    put("producto_id", producto_id)
    put("tipo", tipo.name)
    put("cantidad", cantidad)
    put("nota", nota)
    put("created_at", isoFmt.format(created_at))
    put("updated_at", isoFmt.format(updated_at))
}.toString()
