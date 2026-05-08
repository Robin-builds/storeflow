package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.UsuarioDao
import cl.storeflow.warehouse.data.local.entity.AuthSessionEntity
import cl.storeflow.warehouse.data.local.entity.UsuarioEntity
import cl.storeflow.warehouse.domain.model.Rol
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class UsuarioRepositoryTest {

    private lateinit var authSessionDao: AuthSessionDao
    private lateinit var usuarioDao: UsuarioDao
    private lateinit var repo: UsuarioRepository

    private val sessionFlow = MutableStateFlow<AuthSessionEntity?>(null)

    private val testSession = AuthSessionEntity(
        access_token = "token",
        refresh_token = "refresh",
        user_id = "user-123",
        empresa_id = "emp-456",
        bodega_id = "bod-789",
        rol = "ADMIN",
        expires_at = Date(System.currentTimeMillis() + 3_600_000)
    )

    private val testEntity = UsuarioEntity(
        id = "user-123",
        empresa_id = "emp-456",
        nombre = "María García",
        email = "maria@empresa.cl",
        rol = "ADMIN"
    )

    @Before
    fun setUp() {
        authSessionDao = mockk()
        usuarioDao = mockk()
        every { authSessionDao.observarSesion() } returns sessionFlow
        repo = UsuarioRepository(authSessionDao, usuarioDao)
    }

    @Test
    fun `cuando sesion es null, emite null`() = runTest {
        sessionFlow.value = null

        val result = repo.observarUsuarioActual().first()

        assertNull(result)
    }

    @Test
    fun `cuando sesion existe y entity es null, usa id y empresa de sesion`() = runTest {
        sessionFlow.value = testSession
        coEvery { usuarioDao.obtenerPorId("user-123") } returns null

        val result = repo.observarUsuarioActual().first()

        assertEquals("user-123", result!!.id)
        assertEquals("emp-456", result.empresaId)
        assertEquals(Rol.ADMIN, result.rol)
        assertEquals("", result.nombre)
        assertEquals("", result.email)
    }

    @Test
    fun `cuando sesion y entity existen, nombre y email vienen de entity`() = runTest {
        sessionFlow.value = testSession
        coEvery { usuarioDao.obtenerPorId("user-123") } returns testEntity

        val result = repo.observarUsuarioActual().first()

        assertEquals("María García", result!!.nombre)
        assertEquals("maria@empresa.cl", result.email)
    }

    @Test
    fun `rol de sesion tiene precedencia sobre rol de entity`() = runTest {
        // Sesión dice ADMIN, entity dice OPERADOR (puede ocurrir por datos desincronizados)
        sessionFlow.value = testSession.copy(rol = "ADMIN")
        coEvery { usuarioDao.obtenerPorId("user-123") } returns testEntity.copy(rol = "OPERADOR")

        val result = repo.observarUsuarioActual().first()

        assertEquals(Rol.ADMIN, result!!.rol)
        assertTrue(result.esAdmin())
    }

    @Test
    fun `sesion con rol OPERADOR produce esAdmin false`() = runTest {
        sessionFlow.value = testSession.copy(rol = "OPERADOR")
        coEvery { usuarioDao.obtenerPorId("user-123") } returns testEntity

        val result = repo.observarUsuarioActual().first()

        assertEquals(Rol.OPERADOR, result!!.rol)
        assertFalse(result.esAdmin())
    }

    @Test
    fun `obtenerUsuarioActual retorna null cuando no hay sesion`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns null

        val result = repo.obtenerUsuarioActual()

        assertNull(result)
    }

    @Test
    fun `obtenerUsuarioActual retorna usuario con rol de sesion`() = runTest {
        coEvery { authSessionDao.obtenerSesion() } returns testSession
        coEvery { usuarioDao.obtenerPorId("user-123") } returns testEntity

        val result = repo.obtenerUsuarioActual()

        assertEquals("user-123", result!!.id)
        assertEquals(Rol.ADMIN, result.rol)
        assertEquals("María García", result.nombre)
    }
}
