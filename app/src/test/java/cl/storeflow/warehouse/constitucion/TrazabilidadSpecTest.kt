package cl.storeflow.warehouse.constitucion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Regla de trazabilidad de la spec 002: ningún requisito queda sin test o criterio asociado. */
class TrazabilidadSpecTest {

    private lateinit var lineas: List<String>
    private lateinit var requisitos: Set<String>
    private lateinit var criterios: Set<String>
    private lateinit var tabla: Map<String, String>

    @Before
    fun leerSpec() {
        val spec = listOf(File("."), File(".."))
            .map { File(it, "specs/002-planes-entitlements/spec.md") }
            .first { it.isFile }
        lineas = spec.readLines()

        requisitos = lineas.mapNotNull { Regex("""^- \*\*(FR-\d{3})\*\*""").find(it)?.groupValues?.get(1) }.toSet()
        criterios = lineas.mapNotNull { Regex("""^- \*\*(SC-\d{3})\*\*""").find(it)?.groupValues?.get(1) }.toSet()

        val inicio = lineas.indexOfFirst { it.startsWith("## Trazabilidad") }
        check(inicio >= 0) { "La spec no tiene sección de trazabilidad" }
        tabla = lineas.drop(inicio)
            .mapNotNull { Regex("""^\| (FR-\d{3}) \| (.*) \|$""").find(it) }
            .associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    @Test
    fun `la spec define sus requisitos y criterios`() {
        assertEquals(31, requisitos.size)
        assertEquals(17, criterios.size)
    }

    @Test
    fun `todo FR esta en la tabla de trazabilidad`() {
        assertEquals(emptySet<String>(), requisitos - tabla.keys)
    }

    @Test
    fun `la tabla no referencia requisitos inexistentes`() {
        assertEquals(emptySet<String>(), tabla.keys - requisitos)
    }

    @Test
    fun `todo FR tiene un test o criterio no vacio`() {
        val vacios = tabla.filterValues { it.isBlank() }.keys
        assertTrue("Sin test asociado: $vacios", vacios.isEmpty())
    }

    @Test
    fun `los criterios citados existen`() {
        val citados = tabla.values.flatMap { fila ->
            Regex("""SC-\d{3}""").findAll(fila).map { it.value }.toList()
        }.toSet()
        assertEquals(emptySet<String>(), citados - criterios)
    }
}
