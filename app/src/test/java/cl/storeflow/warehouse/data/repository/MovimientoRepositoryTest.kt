package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.MovimientoDao
import cl.storeflow.warehouse.data.local.dao.ProductoDao
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.AuthSessionEntity
import cl.storeflow.warehouse.data.local.entity.MovimientoEntity
import cl.storeflow.warehouse.data.local.entity.ProductoEntity
import cl.storeflow.warehouse.data.sync.SyncTrigger
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class MovimientoRepositoryTest {

    private lateinit var movimientoDao: MovimientoDao
    private lateinit var productoDao: ProductoDao
    private lateinit var syncDao: SyncDao
    private lateinit var syncTrigger: SyncTrigger
    private lateinit var loteRepository: LoteRepository
    private lateinit var authSessionDao: AuthSessionDao
    private lateinit var repo: MovimientoRepository

    private val testSession = AuthSessionEntity(
        access_token = "token",
        refresh_token = "refresh",
        user_id = "user-1",
        empresa_id = "emp-1",
        bodega_id = "bod-1",
        expires_at = Date(System.currentTimeMillis() + 3_600_000)
    )

    private val productoNoPerecedero = ProductoEntity(
        id = "prod-1",
        empresa_id = "emp-1",
        bodega_id = "bod-1",
        nombre = "Producto de prueba",
        es_perecedero = false
    )

    @Before
    fun setUp() {
        movimientoDao = mockk()
        productoDao = mockk()
        syncDao = mockk()
        syncTrigger = mockk()
        loteRepository = mockk()
        authSessionDao = mockk()

        coEvery { movimientoDao.insertar(any()) } just Runs
        coEvery { syncDao.encolar(any()) } just Runs
        every { syncTrigger.trigger() } just Runs

        repo = MovimientoRepository(movimientoDao, productoDao, syncDao, syncTrigger, loteRepository, authSessionDao)
    }

    @Test
    fun `registrarEntrada con sesion activa setea usuario_id desde la sesion`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns testSession
        val slot = slot<MovimientoEntity>()
        coEvery { movimientoDao.insertar(capture(slot)) } just Runs

        val resultado = repo.registrarEntrada("prod-1", 10, "compra")

        assertTrue(resultado.isSuccess)
        assertEquals("user-1", slot.captured.usuario_id)
    }

    @Test
    fun `registrarSalida (no perecedero) con sesion activa setea usuario_id`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns testSession
        coEvery { productoDao.calcularStock("prod-1") } returns 100
        coEvery { productoDao.obtenerPorId("prod-1") } returns productoNoPerecedero
        val slot = slot<MovimientoEntity>()
        coEvery { movimientoDao.insertar(capture(slot)) } just Runs

        val resultado = repo.registrarSalida("prod-1", 5, "venta")

        assertTrue(resultado.isSuccess)
        assertEquals("user-1", slot.captured.usuario_id)
    }

    @Test
    fun `registrarAjuste con sesion activa setea usuario_id`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns testSession
        coEvery { productoDao.calcularStock("prod-1") } returns 50
        val slot = slot<MovimientoEntity>()
        coEvery { movimientoDao.insertar(capture(slot)) } just Runs

        val resultado = repo.registrarAjuste("prod-1", 60, "conteo fisico")

        assertTrue(resultado.isSuccess)
        assertEquals("user-1", slot.captured.usuario_id)
    }

    @Test
    fun `sin sesion activa, el movimiento se crea igual con usuario_id null`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns null
        val slot = slot<MovimientoEntity>()
        coEvery { movimientoDao.insertar(capture(slot)) } just Runs

        val resultado = repo.registrarEntrada("prod-1", 10, "compra")

        assertTrue(resultado.isSuccess)
        assertNull(slot.captured.usuario_id)
    }
}
