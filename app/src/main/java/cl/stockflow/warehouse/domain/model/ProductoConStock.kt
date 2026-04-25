package cl.stockflow.warehouse.domain.model

import java.util.Date

data class ProductoConStock(
    val id: String,
    val empresa_id: String,
    val bodega_id: String,
    val nombre: String,
    val descripcion: String?,
    val sku: String?,
    val precio: Double,
    val stock_minimo: Int,
    val stock_actual: Int,
    val synced: Boolean,
    val synced_at: Date?,
    val created_at: Date,
    val updated_at: Date
)
