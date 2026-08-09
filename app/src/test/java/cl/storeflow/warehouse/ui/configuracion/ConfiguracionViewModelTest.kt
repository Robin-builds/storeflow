package cl.storeflow.warehouse.ui.configuracion

import cl.storeflow.warehouse.MainDispatcherRule
import cl.storeflow.warehouse.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConfiguracionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: ConfiguracionViewModel

    @Before
    fun setUp() {
        authRepository = mockk()
        viewModel = ConfiguracionViewModel(authRepository)
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
