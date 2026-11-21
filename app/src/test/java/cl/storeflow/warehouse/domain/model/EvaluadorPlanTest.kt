package cl.storeflow.warehouse.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR-024 (gracia offline de 7 días), R11 (reloj) y R22 (degradación sin red). */
class EvaluadorPlanTest {

    private class RelojFalso(
        var ahora: Long = BASE,
        var elapsed: Long = 50_000L,
        var arranques: Int = 3
    ) : RelojPlan {
        override fun ahoraMs() = ahora
        override fun elapsedMs() = elapsed
        override fun bootCount() = arranques
    }

    private val degradacion = DegradacionFree(
        bodegasActivas = setOf("b1"),
        bloqueado = false,
        lotes = false,
        puedeCrearBodega = false,
        puedeCrearUsuario = false,
        historialDesde = 777L
    )

    private fun cache(reloj: RelojFalso, relojEnValidacion: Long = reloj.ahora) = PlanValidado(
        planEfectivo = "STANDARD",
        estado = "TRIAL",
        bloqueado = false,
        lotes = false,
        puedeCrearBodega = true,
        puedeCrearUsuario = true,
        historialDesde = null,
        bodegasSoloLectura = emptySet(),
        degradacion = degradacion,
        relojEnValidacion = relojEnValidacion,
        mayorRelojObservado = relojEnValidacion,
        elapsedEnValidacion = reloj.elapsed,
        bootCount = reloj.arranques
    )

    private val bodegas = setOf("b1", "b2")

    @Test
    fun `7 dias menos 1 segundo conserva el plan`() {
        val reloj = RelojFalso()
        val plan = cache(reloj)
        reloj.ahora += SIETE_DIAS - 1_000
        reloj.elapsed += SIETE_DIAS - 1_000

        val r = EvaluadorPlan(reloj).evaluar(plan, bodegas)

        assertFalse(r.degradado)
        assertEquals("STANDARD", r.planEfectivo)
        assertTrue(r.puedeCrearBodega)
        assertNull(r.historialDesde)
    }

    @Test
    fun `a los 7 dias aplica degradacion_free`() {
        val reloj = RelojFalso()
        val plan = cache(reloj)
        reloj.ahora += SIETE_DIAS
        reloj.elapsed += SIETE_DIAS

        val r = EvaluadorPlan(reloj).evaluar(plan, bodegas)

        assertTrue(r.degradado)
        assertEquals("FREE", r.planEfectivo)
        assertFalse(r.puedeCrearBodega)
        assertFalse(r.puedeCrearUsuario)
        assertEquals(777L, r.historialDesde)
    }

    @Test
    fun `atrasar el reloj no extiende la gracia`() {
        val reloj = RelojFalso()
        val plan = cache(reloj).copy(mayorRelojObservado = BASE + SIETE_DIAS)
        reloj.ahora = BASE - SIETE_DIAS
        reloj.elapsed += 1_000

        assertTrue(EvaluadorPlan(reloj).evaluar(plan, bodegas).degradado)
    }

    @Test
    fun `atrasar el reloj sin reiniciar se detecta con elapsedRealtime`() {
        val reloj = RelojFalso()
        val plan = cache(reloj)
        reloj.ahora = BASE - 1_000
        reloj.elapsed += SIETE_DIAS

        assertTrue(EvaluadorPlan(reloj).evaluar(plan, bodegas).degradado)
    }

    @Test
    fun `tras reiniciar se ignora elapsedRealtime`() {
        val reloj = RelojFalso()
        val plan = cache(reloj)
        reloj.arranques += 1
        reloj.elapsed = SIETE_DIAS * 2
        reloj.ahora += 1_000

        assertFalse(EvaluadorPlan(reloj).evaluar(plan, bodegas).degradado)
    }

    @Test
    fun `un desfase fijo del reloj no altera la cuenta`() {
        val desfase = 3L * 60 * 60 * 1_000
        val reloj = RelojFalso(ahora = BASE + desfase)
        val plan = cache(reloj)
        reloj.ahora += SIETE_DIAS - 1_000
        reloj.elapsed += SIETE_DIAS - 1_000

        assertFalse(EvaluadorPlan(reloj).evaluar(plan, bodegas).degradado)
    }

    @Test
    fun `informa el mayor reloj observado para persistirlo`() {
        val reloj = RelojFalso()
        val plan = cache(reloj)
        reloj.ahora += 5_000

        assertEquals(BASE + 5_000, EvaluadorPlan(reloj).evaluar(plan, bodegas).mayorRelojObservado)

        reloj.ahora = BASE - 5_000
        val atrasado = plan.copy(mayorRelojObservado = BASE + 5_000)
        assertEquals(BASE + 5_000, EvaluadorPlan(reloj).evaluar(atrasado, bodegas).mayorRelojObservado)
    }

    @Test
    fun `al degradar, una bodega fuera de las activas queda en solo lectura`() {
        val reloj = RelojFalso()
        val plan = cache(reloj)
        reloj.ahora += SIETE_DIAS
        reloj.elapsed += SIETE_DIAS

        val r = EvaluadorPlan(reloj).evaluar(plan, setOf("b1", "b2", "creada-offline"))

        assertEquals(setOf("b2", "creada-offline"), r.bodegasSoloLectura)
    }

    @Test
    fun `sin degradar usa las bodegas en solo lectura del servidor`() {
        val reloj = RelojFalso()
        val plan = cache(reloj).copy(bodegasSoloLectura = setOf("b2"))

        assertEquals(setOf("b2"), EvaluadorPlan(reloj).evaluar(plan, bodegas).bodegasSoloLectura)
    }

    @Test
    fun `sin degradacion_free conserva el plan aunque venza la gracia`() {
        val reloj = RelojFalso()
        val plan = cache(reloj).copy(degradacion = null)
        reloj.ahora += SIETE_DIAS
        reloj.elapsed += SIETE_DIAS

        val r = EvaluadorPlan(reloj).evaluar(plan, bodegas)

        assertFalse(r.degradado)
        assertEquals("STANDARD", r.planEfectivo)
    }

    private companion object {
        const val BASE = 1_800_000_000_000L
        const val SIETE_DIAS = 7L * 24 * 60 * 60 * 1_000
    }
}
