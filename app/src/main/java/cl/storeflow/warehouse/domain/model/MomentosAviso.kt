package cl.storeflow.warehouse.domain.model

/** Momentos del ciclo de trial, del más antiguo al más reciente (FR-017). */
object MomentosAviso {
    const val DIA_0 = "DIA_0"
    const val MENOS_7 = "MENOS_7"
    const val MENOS_1 = "MENOS_1"
    const val TERMINO = "TERMINO"
    const val MAS_2 = "MAS_2"

    val ORDEN = listOf(DIA_0, MENOS_7, MENOS_1, TERMINO, MAS_2)

    private const val DIA_MS = 24L * 60 * 60 * 1_000

    /** Este momento y los anteriores: cerrar uno cierra también los previos. */
    fun hasta(momento: String): List<String> {
        val indice = ORDEN.indexOf(momento)
        return if (indice < 0) emptyList() else ORDEN.take(indice + 1)
    }

    /** Para mostrar el aviso sin señal, a partir del término del trial guardado. */
    fun alcanzados(trialFin: Long, ahora: Long): List<String> = buildList {
        add(DIA_0)
        if (ahora >= trialFin - 7 * DIA_MS) add(MENOS_7)
        if (ahora >= trialFin - DIA_MS) add(MENOS_1)
        if (ahora >= trialFin) add(TERMINO)
        if (ahora >= trialFin + 2 * DIA_MS) add(MAS_2)
    }

    /** El más reciente sin cerrar. Lo anterior a un aviso cerrado ya no se muestra. */
    fun vigente(emitidos: Collection<String>, cerrados: Collection<String>): String? {
        val ultimoCerrado = cerrados.maxOfOrNull { ORDEN.indexOf(it) } ?: -1
        return emitidos
            .filter { it !in cerrados && ORDEN.indexOf(it) > ultimoCerrado }
            .maxByOrNull { ORDEN.indexOf(it) }
    }
}
