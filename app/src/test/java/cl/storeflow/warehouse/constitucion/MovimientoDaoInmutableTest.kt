package cl.storeflow.warehouse.constitucion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Principio II de la constitución — historial de movimientos inmutable.
 *
 * Revisa el código fuente de TODOS los DAOs (no solo MovimientoDao) y falla si alguno:
 *  - declara @Update o @Delete sobre MovimientoEntity, o
 *  - tiene un @Query con UPDATE/DELETE sobre la tabla `movimientos` que toque algo más
 *    que los campos de sincronización (synced, synced_at).
 *
 * Se analiza el fuente porque las anotaciones de Room tienen retención BINARY y no son
 * visibles por reflexión en runtime.
 */
class MovimientoDaoInmutableTest {

    private val camposDeSyncPermitidos = setOf("synced", "synced_at")

    private fun fuentesDao(): List<File> {
        val directorio = listOf(
            File("src/main/java/cl/storeflow/warehouse/data/local/dao"),
            File("app/src/main/java/cl/storeflow/warehouse/data/local/dao")
        ).firstOrNull { it.isDirectory }
            ?: error("No se encontró el directorio de DAOs")
        return directorio.listFiles { f -> f.extension == "kt" }!!.toList()
    }

    /** Extrae el SQL de cada @Query("...") o @Query("""...""") del archivo. */
    private fun consultas(fuente: String): List<String> =
        Regex("""@Query\(\s*(\"\"\"[\s\S]*?\"\"\"|"(?:[^"\\]|\\.)*")\s*\)""")
            .findAll(fuente)
            .map { it.groupValues[1].trim('"').replace(Regex("\\s+"), " ").trim() }
            .toList()

    @Test
    fun `ningun DAO tiene Update o Delete sobre MovimientoEntity`() {
        val violaciones = fuentesDao().flatMap { archivo ->
            Regex("""@(Update|Delete)\b[^\n]*\n\s*(suspend\s+)?fun\s+\w+\(([^)]*)\)""")
                .findAll(archivo.readText())
                .filter { it.groupValues[3].contains("MovimientoEntity") }
                .map { "${archivo.name}: ${it.value.replace(Regex("\\s+"), " ")}" }
                .toList()
        }
        assertTrue("Movimientos son inmutables (principio II): $violaciones", violaciones.isEmpty())
    }

    @Test
    fun `ninguna query borra movimientos`() {
        val violaciones = fuentesDao().flatMap { archivo ->
            consultas(archivo.readText())
                .filter { Regex("""(?i)\bDELETE\s+FROM\s+movimientos\b""").containsMatchIn(it) }
                .map { "${archivo.name}: $it" }
        }
        assertTrue("Movimientos no se borran (principio II): $violaciones", violaciones.isEmpty())
    }

    @Test
    fun `los UPDATE sobre movimientos solo tocan campos de sincronizacion`() {
        val violaciones = fuentesDao().flatMap { archivo ->
            consultas(archivo.readText())
                .mapNotNull { sql ->
                    val set = Regex("""(?i)\bUPDATE\s+movimientos\s+SET\s+(.+?)(\s+WHERE\b|$)""")
                        .find(sql) ?: return@mapNotNull null
                    val columnas = set.groupValues[1].split(",")
                        .map { it.substringBefore("=").trim().lowercase() }
                    val prohibidas = columnas - camposDeSyncPermitidos
                    if (prohibidas.isEmpty()) null else "${archivo.name}: $sql → $prohibidas"
                }
        }
        assertTrue("UPDATE de campos de negocio en movimientos (principio II): $violaciones", violaciones.isEmpty())
    }
}
