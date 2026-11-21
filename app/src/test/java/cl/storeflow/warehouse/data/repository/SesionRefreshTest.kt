package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.AppDatabase
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.AuthSessionEntity
import cl.storeflow.warehouse.data.sync.ClienteRefresh
import cl.storeflow.warehouse.data.sync.GestorSesion
import cl.storeflow.warehouse.data.sync.ImportadorSesion
import cl.storeflow.warehouse.data.sync.RespuestaRefresh
import cl.storeflow.warehouse.data.sync.ResultadoToken
import cl.storeflow.warehouse.data.sync.clasificarRespuestaRefresh
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/** P1 y FR-030: una sesión vencida o inválida nunca borra trabajo sin sincronizar. */
class SesionRefreshTest {

    private val authSessionDao = mockk<AuthSessionDao>()
    private val syncDao = mockk<SyncDao>()
    private val db = mockk<AppDatabase>()
    private val clienteRefresh = mockk<ClienteRefresh>()
    private val importador = mockk<ImportadorSesion>()
    private lateinit var gestor: GestorSesion

    private fun sesion(vencida: Boolean = true) = AuthSessionEntity(
        access_token = "access-viejo",
        refresh_token = "refresh-viejo",
        user_id = "usuario-1",
        empresa_id = "empresa-1",
        bodega_id = "bodega-1",
        rol = "ADMIN",
        expires_at = Date(System.currentTimeMillis() + if (vencida) -60_000 else 3_600_000),
        correo = "a@test.invalid"
    )

    @Before
    fun preparar() {
        coEvery { authSessionDao.obtenerSesion() } returns sesion()
        coEvery { authSessionDao.guardarSesion(any()) } just Runs
        coEvery { syncDao.contarTrabajoSinSincronizar() } returns 0
        coEvery { importador.importar(any()) } just Runs
        every { db.clearAllTables() } just Runs
        gestor = GestorSesion(authSessionDao, syncDao, db, clienteRefresh, importador)
    }

    @Test
    fun `token vigente no llama a refresh`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns sesion(vencida = false)

        assertTrue(gestor.obtenerToken() is ResultadoToken.Vigente)
        coVerify(exactly = 0) { clienteRefresh.refrescar(any()) }
    }

    @Test
    fun `error de red conserva la sesion y no borra la base`() = runTest {
        coEvery { clienteRefresh.refrescar(any()) } returns RespuestaRefresh.SinRed

        assertTrue(gestor.obtenerToken() is ResultadoToken.SinRed)
        verify(exactly = 0) { db.clearAllTables() }
        coVerify(exactly = 0) { authSessionDao.guardarSesion(any()) }
    }

    @Test
    fun `refresh exitoso guarda en Room e importa al cliente Auth`() = runTest {
        coEvery { clienteRefresh.refrescar("refresh-viejo") } returns
            RespuestaRefresh.Ok("access-nuevo", "refresh-nuevo", 3600)
        val guardada = slot<AuthSessionEntity>()
        coEvery { authSessionDao.guardarSesion(capture(guardada)) } just Runs

        val r = gestor.obtenerToken()

        assertEquals("access-nuevo", (r as ResultadoToken.Vigente).sesion.access_token)
        assertEquals("refresh-nuevo", guardada.captured.refresh_token)
        coVerify { importador.importar(match { it.access_token == "access-nuevo" }) }
    }

    @Test
    fun `refresh rechazado sin trabajo pendiente limpia la base`() = runTest {
        coEvery { clienteRefresh.refrescar(any()) } returns RespuestaRefresh.Rechazado

        val r = gestor.obtenerToken()

        assertEquals(ResultadoToken.SesionInvalida(datosConservados = false), r)
        verify(exactly = 1) { db.clearAllTables() }
    }

    @Test
    fun `refresh rechazado con trabajo pendiente o retenido no borra nada`() = runTest {
        coEvery { clienteRefresh.refrescar(any()) } returns RespuestaRefresh.Rechazado
        coEvery { syncDao.contarTrabajoSinSincronizar() } returns 2

        val r = gestor.obtenerToken()

        assertEquals(ResultadoToken.SesionInvalida(datosConservados = true), r)
        verify(exactly = 0) { db.clearAllTables() }
    }

    @Test
    fun `refrescos simultaneos rotan el token una sola vez`() = runTest {
        var guardada = sesion()
        coEvery { authSessionDao.obtenerSesion() } answers { guardada }
        coEvery { authSessionDao.guardarSesion(any()) } answers { guardada = firstArg() }
        coEvery { clienteRefresh.refrescar(any()) } returns
            RespuestaRefresh.Ok("access-nuevo", "refresh-nuevo", 3600)

        List(5) { async { gestor.obtenerToken() } }.awaitAll()

        coVerify(exactly = 1) { clienteRefresh.refrescar(any()) }
    }

    @Test
    fun `sin trabajo cualquier usuario puede ingresar`() = runTest {
        assertTrue(gestor.puedeIngresar("otro-usuario"))
    }

    @Test
    fun `con trabajo solo el mismo usuario puede retomar la sesion`() = runTest {
        coEvery { syncDao.contarTrabajoSinSincronizar() } returns 1

        assertTrue(gestor.puedeIngresar("usuario-1"))
        assertFalse(gestor.puedeIngresar("otro-usuario"))
    }

    @Test
    fun `con trabajo no se puede registrar otra cuenta`() = runTest {
        coEvery { syncDao.contarTrabajoSinSincronizar() } returns 1

        assertTrue(gestor.puedeRegistrar("A@test.invalid "))
        assertFalse(gestor.puedeRegistrar("otra@test.invalid"))
    }

    @Test
    fun `logout se rechaza con trabajo pendiente o retenido`() = runTest {
        coEvery { syncDao.contarTrabajoSinSincronizar() } returns 1

        assertFalse(gestor.limpiarSiNoHayTrabajo())
        verify(exactly = 0) { db.clearAllTables() }
    }

    @Test
    fun `logout sin trabajo limpia la base`() = runTest {
        assertTrue(gestor.limpiarSiNoHayTrabajo())
        verify(exactly = 1) { db.clearAllTables() }
    }

    @Test
    fun `Auth rechaza el refresh solo con codigos de token invalido`() {
        listOf(
            400 to """{"error_code":"refresh_token_not_found"}""",
            400 to """{"error_code":"refresh_token_already_used"}""",
            400 to """{"error":"invalid_grant"}""",
            403 to """{"error_code":"session_not_found"}"""
        ).forEach { (status, cuerpo) ->
            assertEquals(RespuestaRefresh.Rechazado, clasificarRespuestaRefresh(status, cuerpo))
        }
    }

    @Test
    fun `5xx, 429 y cuerpos desconocidos no invalidan la sesion`() {
        listOf(
            500 to "{}",
            503 to "error",
            429 to """{"error_code":"over_request_rate_limit"}""",
            400 to "no-json"
        ).forEach { (status, cuerpo) ->
            assertEquals(RespuestaRefresh.SinRed, clasificarRespuestaRefresh(status, cuerpo))
        }
    }

    @Test
    fun `respuesta 200 entrega los tokens nuevos`() {
        val cuerpo = """{"access_token":"a","refresh_token":"r","expires_in":1800}"""
        assertEquals(RespuestaRefresh.Ok("a", "r", 1800), clasificarRespuestaRefresh(200, cuerpo))
    }
}
