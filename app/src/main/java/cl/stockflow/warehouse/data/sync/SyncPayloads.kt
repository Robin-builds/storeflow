package cl.stockflow.warehouse.data.sync

import cl.stockflow.warehouse.data.local.entity.BodegaEntity
import cl.stockflow.warehouse.data.local.entity.MovimientoEntity
import cl.stockflow.warehouse.data.local.entity.OperacionSync
import cl.stockflow.warehouse.data.local.entity.ProductoEntity
import cl.stockflow.warehouse.data.local.entity.SyncEntity
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

private fun MovimientoEntity.toSupabaseJson(): String = buildJsonObject {
    put("id", id)
    put("producto_id", producto_id)
    put("tipo", tipo.name)
    put("cantidad", cantidad)
    put("nota", nota)
    put("created_at", isoFmt.format(created_at))
    put("updated_at", isoFmt.format(updated_at))
}.toString()
