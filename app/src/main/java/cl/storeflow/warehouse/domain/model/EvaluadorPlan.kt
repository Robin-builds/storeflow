package cl.storeflow.warehouse.domain.model

/** Fuente de tiempo del dispositivo, inyectada para poder probar la gracia offline. */
interface RelojPlan {
    fun ahoraMs(): Long
    fun elapsedMs(): Long
    fun bootCount(): Int
}

/** Estado ya resuelto por el servidor para aplicar sin red. No contiene valores de límites (FR-011). */
data class DegradacionFree(
    val bodegasActivas: Set<String>,
    val bloqueado: Boolean,
    val lotes: Boolean,
    val puedeCrearBodega: Boolean,
    val puedeCrearUsuario: Boolean,
    val historialDesde: Long?
)

data class PlanValidado(
    val planEfectivo: String,
    val estado: String,
    val bloqueado: Boolean,
    val lotes: Boolean,
    val puedeCrearBodega: Boolean,
    val puedeCrearUsuario: Boolean,
    val historialDesde: Long?,
    val bodegasSoloLectura: Set<String>,
    val degradacion: DegradacionFree?,
    val relojEnValidacion: Long,
    val mayorRelojObservado: Long,
    val elapsedEnValidacion: Long,
    val bootCount: Int
)

data class PlanEvaluado(
    val planEfectivo: String,
    val estado: String,
    val degradado: Boolean,
    val bloqueado: Boolean,
    val lotes: Boolean,
    val puedeCrearBodega: Boolean,
    val puedeCrearUsuario: Boolean,
    val historialDesde: Long?,
    val bodegasSoloLectura: Set<String>,
    val mayorRelojObservado: Long
)

/** Aplica la gracia offline de 7 días (FR-024, R11) y la degradación sin red (R22). */
class EvaluadorPlan(private val reloj: RelojPlan) {

    fun evaluar(plan: PlanValidado, bodegasLocales: Set<String>): PlanEvaluado {
        val ahora = reloj.ahoraMs()
        val mayorReloj = maxOf(ahora, plan.mayorRelojObservado)
        val porReloj = mayorReloj - plan.relojEnValidacion
        val porElapsed = if (reloj.bootCount() == plan.bootCount) {
            reloj.elapsedMs() - plan.elapsedEnValidacion
        } else {
            0L
        }
        val degradacion = plan.degradacion?.takeIf { maxOf(porReloj, porElapsed) >= GRACIA_MS }

        return if (degradacion == null) {
            PlanEvaluado(
                planEfectivo = plan.planEfectivo,
                estado = plan.estado,
                degradado = false,
                bloqueado = plan.bloqueado,
                lotes = plan.lotes,
                puedeCrearBodega = plan.puedeCrearBodega,
                puedeCrearUsuario = plan.puedeCrearUsuario,
                historialDesde = plan.historialDesde,
                bodegasSoloLectura = plan.bodegasSoloLectura,
                mayorRelojObservado = mayorReloj
            )
        } else {
            PlanEvaluado(
                planEfectivo = PLAN_FREE,
                estado = plan.estado,
                degradado = true,
                bloqueado = degradacion.bloqueado,
                lotes = degradacion.lotes,
                puedeCrearBodega = degradacion.puedeCrearBodega,
                puedeCrearUsuario = degradacion.puedeCrearUsuario,
                historialDesde = degradacion.historialDesde,
                bodegasSoloLectura = bodegasLocales - degradacion.bodegasActivas,
                mayorRelojObservado = mayorReloj
            )
        }
    }

    private companion object {
        const val GRACIA_MS = 7L * 24 * 60 * 60 * 1_000
        const val PLAN_FREE = "FREE"
    }
}
