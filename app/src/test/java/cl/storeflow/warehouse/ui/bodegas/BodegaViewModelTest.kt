package cl.storeflow.warehouse.ui.bodegas

import cl.storeflow.warehouse.MainDispatcherRule
import cl.storeflow.warehouse.data.repository.BodegaRepository
import cl.storeflow.warehouse.data.repository.PlanRepository
import cl.storeflow.warehouse.data.repository.UsuarioRepository
import cl.storeflow.warehouse.domain.model.Rol
import cl.storeflow.warehouse.domain.model.Usuario
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BodegaViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bodegaRepository = mockk<BodegaRepository>()
    private val usuarioRepository = mockk<UsuarioRepository>()
    private val planRepository = mockk<PlanRepository>()

    @Before
    fun preparar() {
        every { bodegaRepository.observarBodegas() } returns flowOf(emptyList())
        every { usuarioRepository.observarUsuarioActual() } returns flowOf(
            Usuario(id = "admin", nombre = "Admin", email = "a@test.invalid", rol = Rol.ADMIN, empresaId = "emp-1")
        )
        coEvery { bodegaRepository.crear(any(), any()) } returns Result.success(mockk(relaxed = true))
    }

    @Test
    fun `sin permiso no llama al repositorio y el mensaje no menciona limites ni compra`() = runTest {
        coEvery { planRepository.puedeCrearBodega() } returns false
        val vm = BodegaViewModel(bodegaRepository, usuarioRepository, planRepository)
        advanceUntilIdle()

        val mensaje = async { vm.mensaje.first() }
        advanceUntilIdle()
        vm.crear("Nueva", null)
        advanceUntilIdle()

        coVerify(exactly = 0) { bodegaRepository.crear(any(), any()) }
        assertEquals(PlanRepository.MENSAJE_SIN_PERMISO_BODEGA, mensaje.await())
        assertFalse(mensaje.await().contains(Regex("[0-9$]|comprar|precio|mejora", RegexOption.IGNORE_CASE)))
    }

    @Test
    fun `con permiso crea la bodega`() = runTest {
        coEvery { planRepository.puedeCrearBodega() } returns true
        val vm = BodegaViewModel(bodegaRepository, usuarioRepository, planRepository)
        advanceUntilIdle()

        vm.crear("Nueva", null)
        advanceUntilIdle()

        coVerify(exactly = 1) { bodegaRepository.crear("Nueva", null) }
    }
}
