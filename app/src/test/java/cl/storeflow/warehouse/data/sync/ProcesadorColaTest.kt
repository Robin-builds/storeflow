package cl.storeflow.warehouse.data.sync

import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.OperacionSync
import cl.storeflow.warehouse.data.local.entity.SyncEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/** Procesamiento de la cola según contracts/sync-cola.md. */
class ProcesadorColaTest {

    private val syncDao = mockk<SyncDao>(relaxed = true)
    private val cola = mutableListOf<SyncEntity>()
    private val enviados = mutableListOf<String>()
    private val sincronizados = mutableListOf<String>()
    private val respuestas = mutableMapOf<String, ArrayDeque<RespuestaPush>>()
    private var reloj = 1_000L

    private val procesador = ProcesadorCola(syncDao)

    @Before
    fun preparar() {
        coEvery { syncDao.obtenerCola() } answers { cola.sortedBy { it.created_at } }
        coEvery { syncDao.eliminar(any()) } answers {
            val item = firstArg<SyncEntity>()
            cola.removeAll { it.id == item.id }
        }
        coEvery { syncDao.marcarRechazo(any(), any(), any()) } answers {
            val id = firstArg<String>()
            val indice = cola.indexOfFirst { it.id == id }
            cola[indice] = cola[indice].copy(rechazo_codigo = secondArg(), rechazado_at = Date())
        }
        coEvery { syncDao.incrementarReintentos(any(), any()) } answers {
            val id = firstArg<String>()
            val indice = cola.indexOfFirst { it.id == id }
            cola[indice] = cola[indice].copy(reintentos = cola[indice].reintentos + 1)
        }
    }

    private fun encolar(
        id: String,
        tabla: String,
        operacion: OperacionSync = OperacionSync.INSERT,
        payload: String = "{}",
        reintentos: Int = 0,
        rechazo: String? = null
    ): SyncEntity = SyncEntity(
        id = id,
        entidad_tipo = tabla,
        entidad_id = "e-$id",
        operacion = operacion,
        payload = payload,
        reintentos = reintentos,
        created_at = Date(reloj++),
        rechazo_codigo = rechazo
    ).also { cola += it }

    private fun responder(id: String, vararg r: RespuestaPush) {
        respuestas[id] = ArrayDeque(r.toList())
    }

    private suspend fun procesar() = procesador.procesar(
        enviar = { item ->
            enviados += item.id
            respuestas[item.id]?.removeFirstOrNull() ?: RespuestaPush(201)
        },
        alSincronizar = { sincronizados += it.id }
    )

    @Test
    fun `un exito se marca sincronizado y sale de la cola`() = runTest {
        encolar("a", "productos")

        val r = procesar()

        assertEquals(listOf("a"), sincronizados)
        assertTrue(cola.isEmpty())
        assertFalse(r.hayQueReintentar)
    }

    @Test
    fun `un item SFP sigue retenido tras mas de 3 ejecuciones`() = runTest {
        encolar("a", "bodegas")
        responder("a", RespuestaPush(400, "SFP01"))

        repeat(5) { procesar() }

        assertEquals(1, cola.size)
        assertEquals("SFP01", cola.single().rechazo_codigo)
        assertEquals("se envía una sola vez", listOf("a"), enviados)
    }

    @Test
    fun `un retenido no provoca retry`() = runTest {
        encolar("a", "bodegas")
        responder("a", RespuestaPush(400, "SFP01"))

        assertFalse(procesar().hayQueReintentar)
    }

    @Test
    fun `un error transitorio no se descarta aunque supere los reintentos`() = runTest {
        encolar("a", "productos", reintentos = 10)
        responder("a", RespuestaPush(null))

        val r = procesar()

        assertEquals(1, cola.size)
        assertTrue(r.hayQueReintentar)
    }

    @Test
    fun `un DELETE de bodega no se envia si su traslado esta retenido`() = runTest {
        encolar("p", "productos", OperacionSync.UPDATE, """{"bodega_id":"b2"}""")
        encolar("d", "bodegas", OperacionSync.DELETE)
        responder("p", RespuestaPush(400, "SFP04"))

        procesar()

        assertEquals(listOf("p"), enviados)
        assertEquals(2, cola.size)
    }

    @Test
    fun `un DELETE de bodega no se envia si su traslado fallo por red`() = runTest {
        encolar("p", "productos", OperacionSync.UPDATE, """{"bodega_id":"b2"}""")
        encolar("d", "bodegas", OperacionSync.DELETE)
        responder("p", RespuestaPush(null))

        val r = procesar()

        assertEquals(listOf("p"), enviados)
        assertTrue(r.hayQueReintentar)
    }

    @Test
    fun `un DELETE de bodega se envia cuando su traslado ya paso`() = runTest {
        encolar("p", "productos", OperacionSync.UPDATE, """{"bodega_id":"b2"}""")
        encolar("d", "bodegas", OperacionSync.DELETE)

        procesar()

        assertEquals(listOf("p", "d"), enviados)
        assertTrue(cola.isEmpty())
    }

    @Test
    fun `un movimiento con 23503 no se descarta y se reenvia tras su padre`() = runTest {
        encolar("m", "movimientos", payload = """{"producto_id":"e-p"}""", reintentos = 10)
        encolar("p", "productos")
        responder("m", RespuestaPush(409, "23503"), RespuestaPush(201))

        val primera = procesar()
        assertEquals(listOf("p"), sincronizados)
        assertEquals("m", cola.single().id)
        assertTrue(primera.hayQueReintentar)

        procesar()
        assertTrue(cola.isEmpty())
    }

    @Test
    fun `23505 de PK cuenta como exito`() = runTest {
        encolar("m", "movimientos")
        responder("m", RespuestaPush(409, "23505", "violates unique constraint \"movimientos_pkey\""))

        procesar()

        assertEquals(listOf("m"), sincronizados)
        assertTrue(cola.isEmpty())
    }

    @Test
    fun `otro 4xx en movimientos o lotes queda retenido`() = runTest {
        encolar("m", "movimientos", reintentos = 10)
        encolar("l", "lotes", reintentos = 10)
        responder("m", RespuestaPush(400, "P0001"))
        responder("l", RespuestaPush(422))

        val r = procesar()

        assertEquals(setOf("P0001", "HTTP_422"), cola.map { it.rechazo_codigo }.toSet())
        assertFalse(r.hayQueReintentar)
    }

    @Test
    fun `otro 4xx en otra entidad se descarta tras 3 reintentos`() = runTest {
        encolar("a", "productos", reintentos = 3)
        responder("a", RespuestaPush(400, "22P02"))

        procesar()

        assertTrue(cola.isEmpty())
        assertTrue(sincronizados.isEmpty())
    }

    @Test
    fun `otro 4xx en otra entidad reintenta antes del limite`() = runTest {
        encolar("a", "productos", reintentos = 1)
        responder("a", RespuestaPush(400, "22P02"))

        val r = procesar()

        assertEquals(2, cola.single().reintentos)
        assertTrue(r.hayQueReintentar)
    }

    @Test
    fun `los dependientes de un INSERT retenido no se envian ni se marcan`() = runTest {
        encolar("b", "bodegas", payload = "{}")
        encolar("p", "productos", payload = """{"bodega_id":"e-b"}""")
        responder("b", RespuestaPush(400, "SFP01"))

        val r = procesar()

        assertEquals(listOf("b"), enviados)
        assertEquals(2, cola.size)
        assertEquals(null, cola.first { it.id == "p" }.rechazo_codigo)
        assertFalse(r.hayQueReintentar)
        coVerify(exactly = 1) { syncDao.marcarRechazo("b", "SFP01", any()) }
    }
}
