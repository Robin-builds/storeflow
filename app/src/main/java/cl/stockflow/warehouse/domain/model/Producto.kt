package cl.stockflow.warehouse.domain.model

data class Producto(
    val id: String,
    val nombre: String,
    val descripcion: String?,
    val sku: String?,
    val precio: Int,
    val stockMinimo: Int,
    val stockActual: Int,
    val bodegaId: String,
    val empresaId: String,
    val atributos: Map<String, String> = emptyMap()
) {
    fun esBajoStock(): Boolean = stockActual < stockMinimo
    fun valorInventario(): Int = precio * stockActual
    fun ratioStock(): Float = if (stockMinimo > 0) stockActual.toFloat() / stockMinimo else 1f
    fun tieneStock(): Boolean = stockActual > 0
    fun descripcionCompleta(): String =
        listOfNotNull(descripcion, sku?.let { "SKU: $it" }).joinToString(" · ")
}
