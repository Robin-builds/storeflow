package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.dao.AtributoTemplateDao
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.BodegaDao
import cl.storeflow.warehouse.data.local.dao.MovimientoDao
import cl.storeflow.warehouse.data.local.dao.ProductoAtributoDao
import cl.storeflow.warehouse.data.local.dao.ProductoDao
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.ProductoEntity
import cl.storeflow.warehouse.data.sync.SyncTrigger
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductoAtributosFormTest {

    private lateinit var productoDao: ProductoDao
    private lateinit var movimientoDao: MovimientoDao
    private lateinit var authSessionDao: AuthSessionDao
    private lateinit var bodegaDao: BodegaDao
    private lateinit var syncDao: SyncDao
    private lateinit var syncTrigger: SyncTrigger
    private lateinit var productoAtributoDao: ProductoAtributoDao
    private lateinit var atributoTemplateDao: AtributoTemplateDao
    private lateinit var repo: ProductoRepository

    @Before
    fun setUp() {
        productoDao = mockk()
        movimientoDao = mockk()
        authSessionDao = mockk()
        bodegaDao = mockk()
        syncDao = mockk()
        syncTrigger = mockk()
        productoAtributoDao = mockk()
        atributoTemplateDao = mockk()

        coJustRun { productoDao.insertar(any()) }
        coJustRun { productoDao.actualizar(any()) }
        coJustRun { syncDao.encolar(any()) }
        justRun { syncTrigger.trigger() }
        coJustRun { productoAtributoDao.upsertAll(any()) }
        coJustRun { productoAtributoDao.eliminarPorProducto(any()) }

        repo = ProductoRepository(
            productoDao, movimientoDao, authSessionDao, bodegaDao,
            syncDao, syncTrigger, productoAtributoDao, atributoTemplateDao
        )
    }

    @Test
    fun `crear con atributos guarda valores en dao`() = runTest {
        val atributos = mapOf("t-1" to "Acetaminofén", "t-2" to "500mg")

        val result = repo.crear("emp-1", "bod-1", "Paracetamol", null, null, 500, 10, atributos = atributos)

        assertTrue(result.isSuccess)
        coVerify {
            productoAtributoDao.upsertAll(
                match { list ->
                    list.size == 2 &&
                    list.any { it.template_id == "t-1" && it.valor == "Acetaminofén" } &&
                    list.any { it.template_id == "t-2" && it.valor == "500mg" }
                }
            )
        }
    }

    @Test
    fun `crear con atributos en blanco no llama upsertAll`() = runTest {
        val atributos = mapOf("t-1" to "", "t-2" to "  ")

        repo.crear("emp-1", "bod-1", "Ibuprofeno", null, null, 800, 5, atributos = atributos)

        coVerify(exactly = 0) { productoAtributoDao.upsertAll(any()) }
    }

    @Test
    fun `actualizar reemplaza atributos elimina anteriores y guarda nuevos`() = runTest {
        val entity = ProductoEntity(
            id = "p-1",
            empresa_id = "emp-1",
            bodega_id = "bod-1",
            nombre = "Paracetamol",
            precio = 500,
            stock_minimo = 10
        )

        val result = repo.actualizar(entity, mapOf("t-1" to "NuevoValor"))

        assertTrue(result.isSuccess)
        coVerifyOrder {
            productoAtributoDao.eliminarPorProducto("p-1")
            productoAtributoDao.upsertAll(
                match { it.size == 1 && it[0].template_id == "t-1" && it[0].valor == "NuevoValor" }
            )
        }
    }

    @Test
    fun `actualizar con mapa vacio elimina previos sin llamar upsertAll`() = runTest {
        val entity = ProductoEntity(
            id = "p-2",
            empresa_id = "emp-1",
            bodega_id = "bod-1",
            nombre = "Ibuprofeno",
            precio = 800,
            stock_minimo = 5
        )

        repo.actualizar(entity, emptyMap())

        coVerify { productoAtributoDao.eliminarPorProducto("p-2") }
        coVerify(exactly = 0) { productoAtributoDao.upsertAll(any()) }
    }
}
