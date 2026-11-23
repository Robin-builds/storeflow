package cl.storeflow.warehouse.ui.configuracion

import cl.storeflow.warehouse.MainDispatcherRule
import cl.storeflow.warehouse.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfiguracionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: ConfiguracionViewModel
    private val trabajo = MutableStateFlow(false)

    @Before
    fun setUp() {
        authRepository = mockk()
        every { authRepository.observarTrabajoSinSincronizar() } returns trabajo
        viewModel = ConfiguracionViewModel(authRepository)
    }

    @Test
    fun `FR-030 - con trabajo sin sincronizar no se ofrece cerrar sesion`() = runTest {
        val job = launch { viewModel.hayTrabajoSinSincronizar.collect {} }

        trabajo.value = true
        advanceUntilIdle()
        assertTrue(viewModel.hayTrabajoSinSincronizar.value)

        trabajo.value = false
        advanceUntilIdle()
        assertFalse(viewModel.hayTrabajoSinSincronizar.value)

        job.cancel()
    }

    @Test
    fun `FR-030 - mientras no se conoce el estado no se ofrece cerrar sesion`() {
        assertTrue(viewModel.hayTrabajoSinSincronizar.value)
    }

    @Test
    fun `estado inicial es Idle`() = runTest {
        assertEquals(ConfiguracionUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `cambiarPassword exitoso emite mensaje y vuelve a Idle`() = runTest {
        coEvery { authRepository.cambiarPassword("actual123", "nueva12345") } coAnswers {
            yield()
            Result.success(Unit)
        }

        val estados = mutableListOf<ConfiguracionUiState>()
        val estadosJob = launch { viewModel.uiState.collect { estados.add(it) } }

        val mensajeDeferred = async { viewModel.mensaje.first() }
        viewModel.cambiarPassword("actual123", "nueva12345")

        assertEquals("Contraseña actualizada", mensajeDeferred.await())
        assertEquals(ConfiguracionUiState.Idle, viewModel.uiState.value)
        assertTrue(estados.contains(ConfiguracionUiState.Operando))

        estadosJob.cancel()
    }

    @Test
    fun `cambiarPassword fallido emite mensaje de error y vuelve a Idle`() = runTest {
        coEvery { authRepository.cambiarPassword("mala", "nueva12345") } returns
            Result.failure(Exception("Contraseña actual incorrecta"))

        val mensajeDeferred = async { viewModel.mensaje.first() }
        viewModel.cambiarPassword("mala", "nueva12345")

        assertEquals("Contraseña actual incorrecta", mensajeDeferred.await())
        assertEquals(ConfiguracionUiState.Idle, viewModel.uiState.value)
    }
}
