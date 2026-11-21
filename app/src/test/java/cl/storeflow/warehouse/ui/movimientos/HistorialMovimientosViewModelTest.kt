package cl.storeflow.warehouse.ui.movimientos

import cl.storeflow.warehouse.MainDispatcherRule
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.entity.AuthSessionEntity
import cl.storeflow.warehouse.data.local.entity.TipoMovimiento
import cl.storeflow.warehouse.data.repository.MovimientoRepository
import cl.storeflow.warehouse.data.repository.PlanRepository
import cl.storeflow.warehouse.domain.model.MovimientoConProducto
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class HistorialMovimientosViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authSessionDao = mockk<AuthSessionDao>()
    private val movimientoRepository = mockk<MovimientoRepository>()
    private val planRepository = mockk<PlanRepository>()
    private val historialDesde = MutableStateFlow<Long?>(null)

    private val sesion = AuthSessionEntity(
        access_token = "token",
        refresh_token = "refresh",
        user_id = "user-1",
        empresa_id = "emp-1",
        bodega_id = "bod-1",
        expires_at = Date(4_000_000_000_000L)
    )

    private fun movimiento(id: String, fecha: Long) = MovimientoConProducto(
        id = id,
        producto_id = "prod-1",
        tipo = TipoMovimiento.ENTRADA,
        cantidad = 1,
        nota = "nota",
        created_at = Date(fecha),
        producto_nombre = "Producto",
        producto_sku = null
    )

    private fun viewModel(): HistorialMovimientosViewModel {
        every { authSessionDao.observarSesion() } returns flowOf(sesion)
        every { movimientoRepository.observarMovimientosDeEmpresa("emp-1") } returns flowOf(
            listOf(movimiento("nuevo", 2_000L), movimiento("limite", 1_000L), movimiento("antiguo", 999L))
        )
        every { planRepository.observarHistorialDesde() } returns historialDesde
        return HistorialMovimientosViewModel(authSessionDao, movimientoRepository, planRepository)
    }

    @Test
    fun `un movimiento anterior a historial_desde no aparece`() = runTest {
        historialDesde.value = 1_000L
        val vm = viewModel()
        val recoleccion = launch { vm.movimientosVisibles.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("nuevo", "limite"), vm.movimientosVisibles.value.map { it.id })
        recoleccion.cancel()
    }

    @Test
    fun `con historial_desde nulo aparecen todos`() = runTest {
        val vm = viewModel()
        val recoleccion = launch { vm.movimientosVisibles.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("nuevo", "limite", "antiguo"), vm.movimientosVisibles.value.map { it.id })
        recoleccion.cancel()
    }
}
