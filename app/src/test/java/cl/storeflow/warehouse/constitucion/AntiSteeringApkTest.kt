package cl.storeflow.warehouse.constitucion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FR-027: la app no incluye cobro, precios de planes, enlaces ni llamados a comprar.
 *
 * Los textos de Kotlin se revisan solo en lo relacionado con planes: "precio" es un campo
 * legítimo del producto en el resto de la app.
 */
class AntiSteeringApkTest {

    private val raiz: File = listOf(File("."), File(".."))
        .first { File(it, "gradle/libs.versions.toml").isFile }

    private val terminosDeCompra = listOf(
        Regex("""\$\s?\d"""),
        Regex("""\bCLP\b"""),
        Regex("""https?://""", RegexOption.IGNORE_CASE),
        Regex("""play\.google""", RegexOption.IGNORE_CASE),
        Regex("""\bprecios?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcompr(a|ar|as|e|es)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpag(a|ar|ue)\b""", RegexOption.IGNORE_CASE),
        Regex("""suscr[ií]b""", RegexOption.IGNORE_CASE),
        Regex("""\bupgrade\b""", RegexOption.IGNORE_CASE),
        Regex("""mejora\s+(tu|de)\s+plan""", RegexOption.IGNORE_CASE),
        Regex("""actualiza\s+tu\s+plan""", RegexOption.IGNORE_CASE)
    )

    private fun terminosEn(texto: String) =
        terminosDeCompra.mapNotNull { it.find(texto)?.value }

    private fun literales(archivo: File): List<String> =
        archivo.readLines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            .flatMap { linea -> Regex(""""((?:[^"\\]|\\.)*)"""").findAll(linea).map { it.groupValues[1] } }

    private fun archivosDePlanes(): List<File> {
        val base = File(raiz, "app/src/main/java/cl/storeflow/warehouse")
        return File(base, "ui/plan").listFiles { f -> f.extension == "kt" }!!.toList() +
            File(base, "data/repository/PlanRepository.kt")
    }

    @Test
    fun `el detector reconoce contenido de compra`() {
        listOf(
            "Plan Standard por \$9.990",
            "Compra tu plan",
            "Mejora tu plan hoy",
            "Suscríbete ahora",
            "https://storeflow.cl/planes",
            "Precio del plan"
        ).forEach { assertTrue("No detectó: $it", terminosEn(it).isNotEmpty()) }

        listOf(
            "Suscripción activa",
            "Para recuperar tu plan, contacta a StoreFlow.",
            "Tu período de prueba termina en 7 días"
        ).forEach { assertEquals("Falso positivo: $it", emptyList<String>(), terminosEn(it)) }
    }

    @Test
    fun `sin dependencia de cobro`() {
        listOf("app/build.gradle.kts", "gradle/libs.versions.toml").forEach { ruta ->
            val coincidencias = File(raiz, ruta).readLines()
                .filter { it.contains("billing", ignoreCase = true) }
            assertEquals("Dependencia de cobro en $ruta", emptyList<String>(), coincidencias)
        }
    }

    @Test
    fun `sin permiso de cobro, incluidos los que aportan las dependencias`() {
        val intermedios = File(raiz, "app/build/intermediates")
        val fusionados = intermedios.listFiles { f -> f.name.startsWith("merged_manifest") }
            .orEmpty()
            .flatMap { d -> d.walkTopDown().filter { it.name == "AndroidManifest.xml" }.toList() }

        (fusionados + File(raiz, "app/src/main/AndroidManifest.xml")).forEach { manifiesto ->
            assertTrue(
                "Permiso de cobro en ${manifiesto.path}",
                !manifiesto.readText().contains("BILLING", ignoreCase = true)
            )
        }
    }

    @Test
    fun `sin terminos de compra en los recursos de texto`() {
        val recursos = File(raiz, "app/src/main/res").listFiles { f -> f.name.startsWith("values") }!!
            .flatMap { d -> d.listFiles { f -> f.name.startsWith("strings") }.orEmpty().toList() }

        val hallazgos = recursos.flatMap { archivo ->
            Regex("<string[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                .findAll(archivo.readText())
                .map { it.groupValues[1] }
                .filter { terminosEn(it).isNotEmpty() }
                .map { "${archivo.name}: $it" }
                .toList()
        }
        assertEquals(emptyList<String>(), hallazgos)
    }

    @Test
    fun `sin terminos de compra en los textos de planes`() {
        val hallazgos = archivosDePlanes().flatMap { archivo ->
            literales(archivo)
                .filter { terminosEn(it).isNotEmpty() }
                .map { "${archivo.name}: $it" }
        }
        assertEquals(emptyList<String>(), hallazgos)
    }
}
