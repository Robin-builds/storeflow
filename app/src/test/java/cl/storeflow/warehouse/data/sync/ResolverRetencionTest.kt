package cl.storeflow.warehouse.data.sync

import cl.storeflow.warehouse.data.local.entity.OperacionSync
import cl.storeflow.warehouse.data.local.entity.SyncEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** Reglas de retención de contracts/sync-cola.md. */
class ResolverRetencionTest {

    private var reloj = 1_000L

    private fun item(
        id: String,
        tabla: String,
        entidad: String,
        operacion: OperacionSync = OperacionSync.INSERT,
        payload: String = "{}",
        rechazo: String? = null
    ) = SyncEntity(
        id = id,
        entidad_tipo = tabla,
        entidad_id = entidad,
        operacion = operacion,
        payload = payload,
        created_at = Date(reloj++),
        rechazo_codigo = rechazo
    )

    private fun bodega(id: String = "b1", rechazo: String? = "SFP01") =
        item("i-$id", "bodegas", id, rechazo = rechazo)

    private fun producto(
        id: String = "p1",
        bodega: String = "b1",
        operacion: OperacionSync = OperacionSync.INSERT,
        rechazo: String? = null
    ) = item("i-$id", "productos", id, operacion, """{"id":"$id","bodega_id":"$bodega"}""", rechazo)

    private fun movimiento(id: String = "m1", producto: String = "p1", lote: String? = null): SyncEntity {
        val campoLote = lote?.let { ""","lote_id":"$it"""" } ?: ""
        return item("i-$id", "movimientos", id, payload = """{"id":"$id","producto_id":"$producto"$campoLote}""")
    }

    private fun lote(id: String = "l1", producto: String = "p1", rechazo: String? = null) =
        item("i-$id", "lotes", id, payload = """{"id":"$id","producto_id":"$producto"}""", rechazo = rechazo)

    @Test
    fun `cola sin rechazos no retiene nada`() {
        val cola = listOf(bodega(rechazo = null), producto(), movimiento())
        assertTrue(ResolverRetencion.resolver(cola).isEmpty())
    }

    @Test
    fun `un item rechazado queda retenido`() {
        val b = bodega()
        assertEquals(setOf(b.id), ResolverRetencion.resolver(listOf(b)))
    }

    @Test
    fun `producto de una bodega con INSERT retenido queda retenido`() {
        val b = bodega()
        val p = producto()
        assertTrue(p.id in ResolverRetencion.resolver(listOf(b, p)))
    }

    @Test
    fun `la retencion se propaga de bodega a producto, lote, atributo y movimiento`() {
        val b = bodega()
        val p = producto()
        val l = lote()
        val a = item("i-a", "producto_atributos", "p1", OperacionSync.UPDATE, "[]")
        val m = movimiento(lote = "l1")
        assertEquals(
            setOf(b.id, p.id, l.id, a.id, m.id),
            ResolverRetencion.resolver(listOf(b, p, l, a, m))
        )
    }

    @Test
    fun `movimiento de un lote con INSERT retenido queda retenido`() {
        val l = lote(rechazo = "SFP03")
        val m = movimiento(lote = "l1")
        assertTrue(m.id in ResolverRetencion.resolver(listOf(l, m)))
    }

    @Test
    fun `FR-028 - un UPDATE retenido de un producto no retiene sus movimientos`() {
        val p = producto(operacion = OperacionSync.UPDATE, rechazo = "SFP04")
        val l = lote()
        val m = movimiento()
        val retenidos = ResolverRetencion.resolver(listOf(p, l, m))
        assertEquals(setOf(p.id), retenidos)
        assertFalse(m.id in retenidos)
    }

    @Test
    fun `DELETE de bodega se retiene si hay un item anterior de productos retenido`() {
        val p = producto(bodega = "b2", operacion = OperacionSync.UPDATE, rechazo = "SFP04")
        val d = item("i-d", "bodegas", "b1", OperacionSync.DELETE)
        assertTrue(d.id in ResolverRetencion.resolver(listOf(p, d)))
    }

    @Test
    fun `DELETE de bodega se retiene si hay un item anterior de productos pendiente`() {
        val p = producto(bodega = "b2", operacion = OperacionSync.UPDATE)
        val d = item("i-d", "bodegas", "b1", OperacionSync.DELETE)
        assertEquals(setOf(d.id), ResolverRetencion.resolver(listOf(p, d)))
    }

    @Test
    fun `DELETE de bodega no se retiene por un item posterior de productos`() {
        val d = item("i-d", "bodegas", "b1", OperacionSync.DELETE)
        val p = producto(bodega = "b2", operacion = OperacionSync.UPDATE)
        assertFalse(d.id in ResolverRetencion.resolver(listOf(d, p)))
    }

    @Test
    fun `retenidos incluye al rechazado y a sus dependientes`() {
        val b = bodega()
        val p = producto()
        val m1 = movimiento("m1")
        val m2 = movimiento("m2")
        assertEquals(setOf(b.id, p.id, m1.id, m2.id), ResolverRetencion.retenidos(listOf(b, p, m1, m2)))
    }

    @Test
    fun `retenidos no cuenta trabajo pendiente sin rechazo`() {
        val p = producto(bodega = "b2", operacion = OperacionSync.UPDATE)
        val d = item("i-d", "bodegas", "b1", OperacionSync.DELETE)
        assertTrue(ResolverRetencion.retenidos(listOf(p, d)).isEmpty())
    }

    @Test
    fun `retenidos cuenta el DELETE de bodega detenido por un producto retenido`() {
        val p = producto(bodega = "b2", operacion = OperacionSync.UPDATE, rechazo = "SFP04")
        val d = item("i-d", "bodegas", "b1", OperacionSync.DELETE)
        assertEquals(setOf(p.id, d.id), ResolverRetencion.retenidos(listOf(p, d)))
    }

    @Test
    fun `un payload invalido no rompe la resolucion`() {
        val roto = item("i-x", "movimientos", "m9", payload = "no-json")
        assertTrue(ResolverRetencion.resolver(listOf(bodega(rechazo = null), roto)).isEmpty())
    }
}
