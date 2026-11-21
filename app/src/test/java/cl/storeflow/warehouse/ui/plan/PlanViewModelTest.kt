package cl.storeflow.warehouse.ui.plan

import cl.storeflow.warehouse.MainDispatcherRule
import cl.storeflow.warehouse.data.repository.PlanRepository
import cl.storeflow.warehouse.data.repository.ResultadoValidacion
import cl.storeflow.warehouse.domain.model.PlanActual
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = mockk<PlanRepository>()
    private val plan = MutableStateFlow<PlanActual?>(null)

    private fun viewModel(): PlanViewModel {
        every { repo.observarPlan() } returns plan
        coEvery { repo.validarActual() } returns ResultadoValidacion.SinConexion
        return PlanViewModel(repo)
    }

    @Test
    fun `sin plan guardado muestra SinPlan`() = runTest {
        assertEquals(PlanUiState.SinPlan, viewModel().uiState.value)
    }

    @Test
    fun `entrega tipo, estado y dias restantes del trial`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        plan.value = PlanActual(tipo = "STANDARD", estado = "TRIAL", diasRestantesTrial = 12)
        advanceUntilIdle()

        assertEquals(PlanUiState.Activo("STANDARD", "TRIAL", 12), vm.uiState.value)
    }

    @Test
    fun `plan sin trial no tiene dias restantes`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        plan.value = PlanActual(tipo = "FREE", estado = "FREE", diasRestantesTrial = null)
        advanceUntilIdle()

        assertEquals(PlanUiState.Activo("FREE", "FREE", null), vm.uiState.value)
    }

    @Test
    fun `el marcador sin plan del servidor se muestra como SinPlan`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        plan.value = PlanActual(PlanRepository.SIN_PLAN, PlanRepository.SIN_PLAN, null)
        advanceUntilIdle()

        assertEquals(PlanUiState.SinPlan, vm.uiState.value)
    }

    @Test
    fun `al abrir revalida el plan`() = runTest {
        viewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.validarActual() }
    }

    @Test
    fun `FR-011 - el estado no tiene campos de valores de limites`() {
        val campos = PlanUiState.Activo::class.java.declaredFields.map { it.name.lowercase() }

        assertFalse(campos.any { it.contains("max") || it.contains("limite") || it.contains("historial") })
    }
}
