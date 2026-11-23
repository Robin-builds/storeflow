package cl.storeflow.warehouse.ui.plan

import cl.storeflow.warehouse.MainDispatcherRule
import cl.storeflow.warehouse.data.repository.PlanRepository
import cl.storeflow.warehouse.data.local.dao.RetenidoPorTipo
import cl.storeflow.warehouse.data.repository.ResultadoValidacion
import cl.storeflow.warehouse.data.repository.UsuarioRepository
import cl.storeflow.warehouse.domain.model.PlanActual
import cl.storeflow.warehouse.domain.model.RetenidoUsuario
import cl.storeflow.warehouse.domain.model.Rol
import cl.storeflow.warehouse.domain.model.Usuario
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    private val usuarioRepository = mockk<UsuarioRepository>()
    private val plan = MutableStateFlow<PlanActual?>(null)
    private val retenidosPropios = MutableStateFlow<List<RetenidoPorTipo>>(emptyList())
    private val retenidosEmpresa = MutableStateFlow<List<RetenidoUsuario>>(emptyList())
    private val aviso = MutableStateFlow<String?>(null)

    private fun viewModel(): PlanViewModel {
        every { repo.observarPlan() } returns plan
        every { repo.observarRetenidosPropios() } returns retenidosPropios
        every { repo.retenidosEmpresa } returns retenidosEmpresa
        every { repo.observarAvisoActual() } returns aviso
        coEvery { repo.cerrarAviso(any()) } just Runs
        every { usuarioRepository.observarUsuariosDeEmpresa() } returns flowOf(
            listOf(Usuario(id = "op-1", nombre = "Operador Uno", email = "o@test.invalid", rol = Rol.OPERADOR, empresaId = "e"))
        )
        coEvery { repo.validarActual() } returns ResultadoValidacion.SinConexion
        return PlanViewModel(repo, usuarioRepository)
    }

    @Test
    fun `FR-023 - muestra el detalle propio y la cantidad por usuario, sin el contenido`() = runTest {
        val vm = viewModel()
        val recoleccion = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        plan.value = PlanActual(tipo = "FREE", estado = "FREE", diasRestantesTrial = null)
        retenidosPropios.value = listOf(RetenidoPorTipo("movimientos", 2))
        retenidosEmpresa.value = listOf(RetenidoUsuario("op-1", 3, "2026-11-21T12:00:00Z"))
        advanceUntilIdle()

        val estado = vm.uiState.value as PlanUiState.Activo
        assertEquals(listOf(RetenidoPorTipo("movimientos", 2)), estado.retenidosPropios)
        assertEquals(
            listOf(RetenidoVisible("Operador Uno", 3, "2026-11-21T12:00:00Z")),
            estado.retenidosPorUsuario
        )
        recoleccion.cancel()
    }

    @Test
    fun `FR-017 - expone el aviso vigente y lo cierra`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        aviso.value = "MENOS_7"
        advanceUntilIdle()
        assertEquals("MENOS_7", vm.avisoActual.value)

        vm.cerrarAviso()
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.cerrarAviso("MENOS_7") }
    }

    @Test
    fun `FR-017 - sin aviso vigente no cierra nada`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.cerrarAviso()
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.cerrarAviso(any()) }
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
