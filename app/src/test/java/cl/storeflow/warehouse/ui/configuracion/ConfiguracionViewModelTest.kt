package cl.storeflow.warehouse.ui.configuracion

import cl.storeflow.warehouse.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConfiguracionViewModelTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: ConfiguracionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        authRepository = mockk()
        viewModel = ConfiguracionViewModel(authRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `estado inicial es Idle`() = runTest {
        assertEquals(ConfiguracionUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `cambiarPassword exitoso emite mensaje y vuelve a Idle`() = runTest {
        coEvery { authRepository.cambiarPassword("actual123", "nueva12345") } returns Result.success(Unit)

        val mensajeDeferred = async { viewModel.mensaje.first() }
        viewModel.cambiarPassword("actual123", "nueva12345")

        assertEquals("Contraseña actualizada", mensajeDeferred.await())
        assertEquals(ConfiguracionUiState.Idle, viewModel.uiState.value)
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
