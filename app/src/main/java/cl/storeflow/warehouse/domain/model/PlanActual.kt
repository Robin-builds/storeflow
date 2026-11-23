package cl.storeflow.warehouse.domain.model

/** Plan que ve el usuario. Contiene el tipo y permisos resueltos, nunca los valores de sus límites (FR-011). */
data class PlanActual(
    val tipo: String,
    val estado: String,
    val diasRestantesTrial: Int?,
    val bodegasSoloLectura: Set<String> = emptySet(),
    val bloqueado: Boolean = false,
    val lotes: Boolean = true,
    val puedeCrearBodega: Boolean = true,
    val puedeCrearUsuario: Boolean = true,
    val historialDesde: Long? = null
)

/** Cantidad de trabajo retenido por usuario, sin su contenido (FR-023). */
data class RetenidoUsuario(
    val usuarioId: String,
    val cantidad: Int,
    val actualizadoAt: String
)
