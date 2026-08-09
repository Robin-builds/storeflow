package cl.storeflow.warehouse.ui.usuarios

import cl.storeflow.warehouse.MainDispatcherRule
import cl.storeflow.warehouse.data.repository.AuthRepository
import cl.storeflow.warehouse.data.repository.UsuarioRepository
import cl.storeflow.warehouse.domain.model.Rol
import cl.storeflow.warehouse.domain.model.Usuario
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UsuariosViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var usuarioRepository: UsuarioRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var viewModel: UsuariosViewModel

    private val testUsuario = Usuario(
        id = "op-1", nombre = "Operador Uno", email = "op1@empresa.cl",
        rol = Rol.OPERADOR, empresaId = "emp-1"
    )

    @Before
    fun setUp() {
        usuarioRepository = mockk()
        authRepository = mockk()
        coEvery { usuarioRepository.obtenerUsuarioActual() } returns testUsuario
        coEvery { usuarioRepository.observarUsuariosDeEmpresa() } returns flowOf(listOf(testUsuario))
        viewModel = UsuariosViewModel(usuarioRepository, authRepository)
    }

    @Test
    fun `resetearPassword exitoso emite mensaje de confirmacion`() = runTest {
        coEvery { authRepository.resetearPasswordUsuario("op-1", "nueva12345") } returns Result.success(Unit)

        val mensajeDeferred = async { viewModel.mensaje.first() }
        viewModel.resetearPassword(testUsuario, "nueva12345")

        assertEquals("Contraseña restablecida", mensajeDeferred.await())
    }

    @Test
    fun `resetearPassword fallido emite mensaje de error`() = runTest {
        coEvery { authRepository.resetearPasswordUsuario("op-1", "nueva12345") } returns
            Result.failure(Exception("Usuario no pertenece a tu empresa"))

        val mensajeDeferred = async { viewModel.mensaje.first() }
        viewModel.resetearPassword(testUsuario, "nueva12345")

        assertEquals("Usuario no pertenece a tu empresa", mensajeDeferred.await())
    }
}
