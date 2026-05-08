package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.BodegaDao
import cl.storeflow.warehouse.data.local.dao.ProductoDao
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.AuthSessionEntity
import cl.storeflow.warehouse.data.local.entity.BodegaEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class BodegaRepositoryTest {

    private lateinit var authSessionDao: AuthSessionDao
    private lateinit var bodegaDao: BodegaDao
    private lateinit var syncDao: SyncDao
    private lateinit var productoDao: ProductoDao
    private lateinit var repo: BodegaRepository

    private val sessionFlow = MutableStateFlow<AuthSessionEntity?>(null)

    private val testSession = AuthSessionEntity(
        access_token = "token",
        refresh_token = "refresh",
        user_id = "user-1",
        empresa_id = "emp-1",
        bodega_id = "bod-1",
        expires_at = Date(System.currentTimeMillis() + 3_600_000)
    )

    private val entidadActiva = BodegaEntity(id = "bod-1", empresa_id = "emp-1", nombre = "Principal", ubicacion = null)
    private val entidadOtra  = BodegaEntity(id = "bod-2", empresa_id = "emp-1", nombre = "Norte", ubicacion = "Piso 2")

    @Before
    fun setUp() {
        authSessionDao = mockk()
        bodegaDao = mockk()
        syncDao = mockk()
        productoDao = mockk()
        every { authSessionDao.observarSesion() } returns sessionFlow
        repo = BodegaRepository(bodegaDao, authSessionDao, syncDao, productoDao)
    }

    @Test
    fun `cuando sesion es null, observarBodegas emite lista vacia`() = runTest {
        sessionFlow.value = null

        val result = repo.observarBodegas().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `observarBodegas marca como activa la bodega cuyo id coincide con sesion`() = runTest {
        sessionFlow.value = testSession
        every { bodegaDao.observarPorEmpresa("emp-1") } returns
                MutableStateFlow(listOf(entidadActiva, entidadOtra))

        val bodegas = repo.observarBodegas().first()

        assertEquals(2, bodegas.size)
        assertTrue(bodegas.first { it.id == "bod-1" }.esActiva)
        assertTrue(!bodegas.first { it.id == "bod-2" }.esActiva)
    }

    @Test
    fun `observarBodegas mapea ubicacion correctamente`() = runTest {
        sessionFlow.value = testSession
        every { bodegaDao.observarPorEmpresa("emp-1") } returns
                MutableStateFlow(listOf(entidadOtra))

        val bodega = repo.observarBodegas().first().first()

        assertEquals("Norte", bodega.nombre)
        assertEquals("Piso 2", bodega.ubicacion)
        assertEquals("emp-1", bodega.empresaId)
    }

    @Test
    fun `cuando cambia bodega_id en sesion, esActiva se recalcula`() = runTest {
        val bodegaFlow = MutableStateFlow(listOf(entidadActiva, entidadOtra))
        every { bodegaDao.observarPorEmpresa("emp-1") } returns bodegaFlow

        // Sesión inicial: bod-1 activa
        sessionFlow.value = testSession
        val primera = repo.observarBodegas().first()
        assertTrue(primera.first { it.id == "bod-1" }.esActiva)
        assertTrue(!primera.first { it.id == "bod-2" }.esActiva)

        // Cambio de sesión: bod-2 activa
        sessionFlow.value = testSession.copy(bodega_id = "bod-2")
        val segunda = repo.observarBodegas().first()
        assertTrue(!segunda.first { it.id == "bod-1" }.esActiva)
        assertTrue(segunda.first { it.id == "bod-2" }.esActiva)
    }

    @Test
    fun `obtenerBodegaActiva retorna null cuando no hay sesion`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns null

        assertNull(repo.obtenerBodegaActiva())
    }

    @Test
    fun `obtenerBodegaActiva retorna Bodega con esActiva true`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns testSession
        coEvery { bodegaDao.obtenerPorId("bod-1") } returns entidadActiva

        val bodega = repo.obtenerBodegaActiva()

        assertEquals("bod-1", bodega!!.id)
        assertTrue(bodega.esActiva)
    }
}
