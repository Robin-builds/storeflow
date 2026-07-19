package cl.storeflow.warehouse.domain.model

data class ProductoConStockYBodega(
    val id: String,
    val nombre: String,
    val sku: String?,
    val precio: Int,
    val stock_actual: Int,
    val bodega_id: String,
    val bodega_nombre: String
)
