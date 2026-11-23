package cl.storeflow.warehouse.data.sync

import cl.storeflow.warehouse.data.local.entity.OperacionSync
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tabla de contracts/codigos-rechazo.md. Se clasifica por `code`, no por status. */
class ClasificadorRechazoTest {

    private fun clasificar(
        status: Int?,
        code: String? = null,
        mensaje: String? = null,
        tabla: String = "productos",
        operacion: OperacionSync = OperacionSync.INSERT
    ) = ClasificadorRechazo.clasificar(RespuestaPush(status, code, mensaje), tabla, operacion)

    @Test
    fun `2xx es exito`() {
        listOf(200, 201, 204).forEach { assertEquals(ClaseRespuesta.Exito, clasificar(it)) }
    }

    @Test
    fun `cada codigo SFP se retiene aunque cambie el status`() {
        listOf("SFP01", "SFP02", "SFP03", "SFP04", "SFP05", "SFP06").forEach { codigo ->
            listOf(400, 403, 409).forEach { status ->
                assertEquals(ClaseRespuesta.RechazoPlan(codigo), clasificar(status, codigo))
            }
        }
    }

    @Test
    fun `23503 es dependencia pendiente en cualquier tabla`() {
        listOf("movimientos", "lotes", "productos").forEach { tabla ->
            assertEquals(
                ClaseRespuesta.DependenciaPendiente,
                clasificar(409, "23503", tabla = tabla)
            )
        }
    }

    @Test
    fun `23505 de PK en INSERT cuenta como ya existente`() {
        val mensaje = "duplicate key value violates unique constraint \"movimientos_pkey\""
        assertEquals(
            ClaseRespuesta.YaExiste,
            clasificar(409, "23505", mensaje, tabla = "movimientos")
        )
    }

    @Test
    fun `23505 que no es de PK no cuenta como exito`() {
        val mensaje = "duplicate key value violates unique constraint \"productos_sku_key\""
        assertEquals(
            ClaseRespuesta.PermanenteDescartable,
            clasificar(409, "23505", mensaje, tabla = "productos")
        )
        assertEquals(
            ClaseRespuesta.PermanenteRetenido("23505"),
            clasificar(409, "23505", mensaje, tabla = "movimientos")
        )
    }

    @Test
    fun `23505 de PK fuera de un INSERT no cuenta como exito`() {
        val mensaje = "duplicate key value violates unique constraint \"productos_pkey\""
        assertEquals(
            ClaseRespuesta.PermanenteDescartable,
            clasificar(409, "23505", mensaje, operacion = OperacionSync.UPDATE)
        )
    }

    @Test
    fun `red, 408, 429, 5xx y 401 son transitorios`() {
        listOf(null, 408, 429, 500, 502, 503, 504, 401).forEach { status ->
            assertEquals(ClaseRespuesta.Transitorio, clasificar(status))
            assertEquals(ClaseRespuesta.Transitorio, clasificar(status, tabla = "movimientos"))
        }
    }

    @Test
    fun `otro 4xx en movimientos y lotes se retiene con su codigo`() {
        listOf("movimientos", "lotes").forEach { tabla ->
            assertEquals(
                ClaseRespuesta.PermanenteRetenido("P0001"),
                clasificar(400, "P0001", tabla = tabla)
            )
        }
    }

    @Test
    fun `otro 4xx sin codigo en movimientos se retiene con el status`() {
        assertEquals(
            ClaseRespuesta.PermanenteRetenido("HTTP_422"),
            clasificar(422, tabla = "movimientos")
        )
    }

    @Test
    fun `otro 4xx en otras entidades mantiene el comportamiento actual`() {
        listOf("productos", "bodegas", "atributo_templates", "producto_atributos").forEach { tabla ->
            assertEquals(
                ClaseRespuesta.PermanenteDescartable,
                clasificar(400, "22P02", tabla = tabla)
            )
        }
    }
}
