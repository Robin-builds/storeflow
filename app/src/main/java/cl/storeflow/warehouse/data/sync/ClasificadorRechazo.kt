package cl.storeflow.warehouse.data.sync

import cl.storeflow.warehouse.data.local.entity.OperacionSync

/** Respuesta de un envío. `status` nulo significa que no hubo respuesta (error de red). */
data class RespuestaPush(
    val status: Int?,
    val code: String? = null,
    val mensaje: String? = null
)

sealed interface ClaseRespuesta {
    data object Exito : ClaseRespuesta
    data object YaExiste : ClaseRespuesta
    data class RechazoPlan(val codigo: String) : ClaseRespuesta
    data object Transitorio : ClaseRespuesta
    data object DependenciaPendiente : ClaseRespuesta
    data class PermanenteRetenido(val codigo: String) : ClaseRespuesta
    data object PermanenteDescartable : ClaseRespuesta
}

/** Implementa la tabla de contracts/codigos-rechazo.md. Clasifica por `code`, no por status. */
object ClasificadorRechazo {

    private val CODIGOS_PLAN = setOf("SFP01", "SFP02", "SFP03", "SFP04", "SFP05", "SFP06")
    private val TABLAS_SIN_DESCARTE = setOf("movimientos", "lotes")
    private const val FK_VIOLADA = "23503"
    private const val UNIQUE_VIOLADA = "23505"

    fun clasificar(
        respuesta: RespuestaPush,
        tabla: String,
        operacion: OperacionSync
    ): ClaseRespuesta {
        val status = respuesta.status ?: return ClaseRespuesta.Transitorio
        val code = respuesta.code
        return when {
            status in 200..299 -> ClaseRespuesta.Exito
            code in CODIGOS_PLAN -> ClaseRespuesta.RechazoPlan(code!!)
            status == 401 || status == 408 || status == 429 || status >= 500 ->
                ClaseRespuesta.Transitorio
            code == FK_VIOLADA -> ClaseRespuesta.DependenciaPendiente
            esPkDuplicadaEnInsert(respuesta, operacion) -> ClaseRespuesta.YaExiste
            tabla in TABLAS_SIN_DESCARTE ->
                ClaseRespuesta.PermanenteRetenido(code ?: "HTTP_$status")
            else -> ClaseRespuesta.PermanenteDescartable
        }
    }

    private fun esPkDuplicadaEnInsert(respuesta: RespuestaPush, operacion: OperacionSync) =
        operacion == OperacionSync.INSERT &&
            respuesta.code == UNIQUE_VIOLADA &&
            respuesta.mensaje?.contains("_pkey") == true
}
