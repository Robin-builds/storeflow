package cl.storeflow.warehouse.loadtest

import android.os.Debug
import kotlinx.coroutines.runBlocking

/**
 * Cronómetro con dos modos de medición y registro de heap.
 *
 * Sin androidx.benchmark a propósito (ADR-015-A): esa librería exige clocks
 * bloqueados y falla el build en dispositivos normales. Acá se acepta más
 * ruido a cambio de poder correr en el Moto G60 sin ceremonia.
 */
object Medidor {

    data class Resultado(
        val escenario: String,
        val escalon: String,
        val capa: String,        // "dao" | "repo"
        val modo: String,        // "cold" | "warm"
        val medianaMs: Double,
        val p95Ms: Double,
        val maxMs: Double,
        val filas: Int,
        val heapDeltaKb: Long
    ) {
        fun aCsv() = "$escalon,$escenario,$capa,$modo,%.2f,%.2f,%.2f,$filas,$heapDeltaKb"
            .format(medianaMs, p95Ms, maxMs)

        companion object {
            const val CABECERA =
                "escalon,escenario,capa,modo,mediana_ms,p95_ms,max_ms,filas,heap_delta_kb"
        }
    }

    /**
     * Medición COLD: una sola ejecución, sin warmup.
     *
     * Es el caso que vive el usuario al abrir la app: page cache de la conexión
     * SQLite vacío. El DoD de StoreFlow dice "carga en < 1 segundo" y se refiere
     * a esto, no al caso caliente.
     *
     * LIMITACIÓN HONESTA: solo se puede vaciar el cache de la conexión (cerrando
     * y reabriendo la base). El page cache del kernel queda caliente y no se
     * puede limpiar sin root. El cold medido es por lo tanto OPTIMISTA respecto
     * de un arranque real en frío del sistema. Interpretar como cota inferior.
     *
     * @param bloque debe devolver la cantidad de filas, para detectar queries
     *               que "van rápido" porque no devuelven nada.
     */
    fun medirCold(
        escenario: String,
        escalon: String,
        capa: String,
        bloque: suspend () -> Int
    ): Resultado = runBlocking {
        val heapAntes = heapUsadoKb()
        val t0 = System.nanoTime()
        val filas = bloque()
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        Resultado(escenario, escalon, capa, "cold", ms, ms, ms, filas, heapUsadoKb() - heapAntes)
    }

    /**
     * Medición WARM: warmup + N muestras, percentiles.
     *
     * Es el caso del usuario que ya viene navegando: scroll, volver atrás,
     * refrescar. El presupuesto acá es de un frame, no de un segundo.
     */
    fun medirWarm(
        escenario: String,
        escalon: String,
        capa: String,
        warmup: Int = 3,
        iteraciones: Int = 10,
        bloque: suspend () -> Int
    ): Resultado = runBlocking {
        repeat(warmup) { bloque() }

        val heapAntes = heapUsadoKb()
        val muestras = DoubleArray(iteraciones)
        var filas = 0
        repeat(iteraciones) { i ->
            val t0 = System.nanoTime()
            filas = bloque()
            muestras[i] = (System.nanoTime() - t0) / 1_000_000.0
        }
        val heapDelta = heapUsadoKb() - heapAntes
        muestras.sort()

        Resultado(
            escenario = escenario,
            escalon = escalon,
            capa = capa,
            modo = "warm",
            medianaMs = muestras[iteraciones / 2],
            p95Ms = muestras[(iteraciones * 95 / 100).coerceAtMost(iteraciones - 1)],
            maxMs = muestras.last(),
            filas = filas,
            heapDeltaKb = heapDelta
        )
    }

    /**
     * Heap de la app en KB.
     *
     * Se fuerza GC antes de leer para que el delta refleje memoria retenida y no
     * basura pendiente de recolectar. No es determinista (System.gc() es una
     * sugerencia), así que el número es indicativo: sirve para comparar entre
     * escalones, no como valor absoluto.
     */
    private fun heapUsadoKb(): Long {
        System.gc()
        Thread.sleep(100)
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / 1024 + Debug.getNativeHeapAllocatedSize() / 1024
    }
}
