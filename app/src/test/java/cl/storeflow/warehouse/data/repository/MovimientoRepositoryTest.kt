package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.MovimientoDao
import cl.storeflow.warehouse.data.local.dao.ProductoDao
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.AuthSessionEntity
import cl.storeflow.warehouse.data.local.entity.MovimientoEntity
import cl.storeflow.warehouse.data.local.entity.ProductoEntity
import cl.storeflow.warehouse.data.sync.SyncTrigger
import cl.storeflow.warehouse.domain.model.LoteConStock
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
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
    private lateinit var planRepository: PlanRepository
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

        planRepository = mockk()
        coEvery { planRepository.bodegasEnSoloLectura() } returns emptySet()
        coEvery { planRepository.incluyeLotes() } returns true

        repo = MovimientoRepository(
            movimientoDao, productoDao, syncDao, syncTrigger, loteRepository, authSessionDao, planRepository
        )
    }

    @Test
    fun `FR-025 - sin lotes, la entrada de un perecedero se registra sin lote`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns testSession
        coEvery { planRepository.incluyeLotes() } returns false
        coEvery { productoDao.obtenerPorId("prod-1") } returns productoNoPerecedero.copy(es_perecedero = true)
        val slot = slot<MovimientoEntity>()
        coEvery { movimientoDao.insertar(capture(slot)) } just Runs

        val resultado = repo.registrarEntrada("prod-1", 10, "compra", Date(), "L-1")

        assertTrue(resultado.isSuccess)
        assertNull(slot.captured.lote_id)
        assertEquals(10, slot.captured.cantidad)
        coVerify(exactly = 0) { loteRepository.crear(any(), any(), any(), any()) }
    }

    @Test
    fun `FR-025 - sin lotes, la salida descuenta de los lotes existentes y el resto sin lote`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns testSession
        coEvery { planRepository.incluyeLotes() } returns false
        coEvery { productoDao.calcularStock("prod-1") } returns 10
        coEvery { productoDao.obtenerPorId("prod-1") } returns productoNoPerecedero.copy(es_perecedero = true)
        coEvery { loteRepository.obtenerConStockFefo("prod-1") } returns listOf(
            LoteConStock(
                id = "lote-1", producto_id = "prod-1", empresa_id = "emp-1", numero_lote = null,
                fecha_caducidad = Date(), stock_actual = 4, synced = true, synced_at = null,
                created_at = Date(), updated_at = Date()
            )
        )
        val insertados = mutableListOf<MovimientoEntity>()
        coEvery { movimientoDao.insertar(capture(insertados)) } just Runs

        val resultado = repo.registrarSalida("prod-1", 6, "venta")

        assertTrue(resultado.isSuccess)
        assertEquals(listOf("lote-1", null), insertados.map { it.lote_id })
        assertEquals(listOf(-4, -2), insertados.map { it.cantidad })
    }

    @Test
    fun `no registra entrada, salida ni ajuste en una bodega en solo lectura`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns testSession
        coEvery { planRepository.bodegasEnSoloLectura() } returns setOf("bod-1")
        coEvery { productoDao.obtenerPorId("prod-1") } returns productoNoPerecedero
        coEvery { productoDao.calcularStock("prod-1") } returns 100

        assertTrue(repo.registrarEntrada("prod-1", 10, "compra").isFailure)
        assertTrue(repo.registrarSalida("prod-1", 5, "venta").isFailure)
        assertTrue(repo.registrarAjuste("prod-1", 60, "conteo").isFailure)

        coVerify(exactly = 0) { movimientoDao.insertar(any()) }
        coVerify(exactly = 0) { syncDao.encolar(any()) }
    }

    @Test
    fun `registra normalmente si la bodega del producto sigue activa`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns testSession
        coEvery { planRepository.bodegasEnSoloLectura() } returns setOf("bod-2")
        coEvery { productoDao.obtenerPorId("prod-1") } returns productoNoPerecedero

        assertTrue(repo.registrarEntrada("prod-1", 10, "compra").isSuccess)
        coVerify(exactly = 1) { movimientoDao.insertar(any()) }
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
