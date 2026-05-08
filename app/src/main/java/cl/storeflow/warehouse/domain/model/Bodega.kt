package cl.storeflow.warehouse.domain.model

data class Bodega(
    val id: String,
    val nombre: String,
    val ubicacion: String?,
    val empresaId: String,
    val esActiva: Boolean = false
) {
    fun descripcion(): String = ubicacion?.let { "$nombre — $it" } ?: nombre
}
