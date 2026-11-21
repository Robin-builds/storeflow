package cl.storeflow.warehouse.constitucion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Verifica invariantes de la constitución (.specify/memory/constitution.md) sobre el
 * esquema Room exportado más reciente (app/schemas/.../<versión>.json).
 *
 * Principio I  — stock siempre derivado: ninguna tabla guarda stock.
 * Principio IV — montos en CLP enteros: productos.precio es INTEGER.
 * Principio V  — identidad global: toda PK es TEXT (UUID), salvo auth_sessions.
 *
 * Room regenera el JSON en cada build (exportSchema = true), así que este test ve
 * cualquier cambio de entidad apenas compila.
 */
class EsquemaInvariantesTest {

    private lateinit var tablas: List<JsonObject>
    private var versionEsquema = 0

    @Before
    fun cargarEsquemaMasReciente() {
        val directorio = listOf(
            File("schemas/cl.storeflow.warehouse.data.local.AppDatabase"),
            File("app/schemas/cl.storeflow.warehouse.data.local.AppDatabase")
        ).firstOrNull { it.isDirectory }
            ?: error("No se encontró el directorio de esquemas Room exportados")

        val archivo = directorio.listFiles { f -> f.extension == "json" }!!
            .maxBy { it.nameWithoutExtension.toInt() }

        val raiz = Json.parseToJsonElement(archivo.readText()).jsonObject
        val database = raiz["database"]!!.jsonObject
        versionEsquema = database["version"]!!.jsonPrimitive.int
        tablas = database["entities"]!!.jsonArray.map { it.jsonObject }
    }

    private fun nombre(tabla: JsonObject) = tabla["tableName"]!!.jsonPrimitive.content

    private fun columnas(tabla: JsonObject): Map<String, String> =
        tabla["fields"]!!.jsonArray.associate {
            val campo = it.jsonObject
            campo["columnName"]!!.jsonPrimitive.content to campo["affinity"]!!.jsonPrimitive.content
        }

    @Test
    fun `principio I - ninguna tabla almacena stock`() {
        val violaciones = tablas.flatMap { tabla ->
            columnas(tabla).keys
                .filter { it.contains("stock", ignoreCase = true) && it != "stock_minimo" }
                .map { "${nombre(tabla)}.$it" }
        }
        assertTrue(
            "Esquema v$versionEsquema guarda stock en $violaciones — el stock se deriva de movimientos (principio I)",
            violaciones.isEmpty()
        )
    }

    @Test
    fun `principio IV - precio de productos es entero`() {
        val productos = tablas.first { nombre(it) == "productos" }
        assertEquals(
            "productos.precio debe ser INTEGER (CLP sin decimales, principio IV)",
            "INTEGER",
            columnas(productos)["precio"]
        )
    }

    @Test
    fun `principio V - toda PK es TEXT salvo auth_sessions`() {
        val violaciones = tablas
            .filter { nombre(it) != "auth_sessions" }
            .flatMap { tabla ->
                val tipos = columnas(tabla)
                tabla["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray
                    .map { it.jsonPrimitive.content }
                    .filter { tipos[it] != "TEXT" }
                    .map { "${nombre(tabla)}.$it (${tipos[it]})" }
            }
        assertTrue(
            "PK no UUID/TEXT en esquema v$versionEsquema: $violaciones (principio V)",
            violaciones.isEmpty()
        )
    }
}
