package cl.storeflow.warehouse.domain.model

/** Plan que ve el usuario. Contiene el tipo, nunca los valores de sus límites (FR-011). */
data class PlanActual(
    val tipo: String,
    val estado: String,
    val diasRestantesTrial: Int?,
    val bodegasSoloLectura: Set<String> = emptySet()
)
