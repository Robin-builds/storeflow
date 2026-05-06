package cl.storeflow.warehouse.domain.model

import java.util.Date

data class ProductoConStock(
    val id: String,
    val empresa_id: String,
    val bodega_id: String,
    val nombre: String,
    val descripcion: String?,
    val sku: String?,
    val precio: Int,
    val stock_minimo: Int,
    val stock_actual: Int,
    val synced: Boolean,
    val synced_at: Date?,
    val created_at: Date,
    val updated_at: Date
) {
    fun toDomain(atributos: Map<String, String> = emptyMap()): Producto = Producto(
        id = id,
        nombre = nombre,
        descripcion = descripcion,
        sku = sku,
        precio = precio,
        stockMinimo = stock_minimo,
        stockActual = stock_actual,
        bodegaId = bodega_id,
        empresaId = empresa_id,
        atributos = atributos
    )
}
