package cl.stockflow.warehouse.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsuarioTest {

    private fun usuario(rol: Rol) = Usuario(
        id = "u1",
        nombre = "Test",
        email = "test@test.com",
        rol = rol,
        empresaId = "e1"
    )

    @Test
    fun `esAdmin retorna true para ADMIN`() {
        assertTrue(usuario(Rol.ADMIN).esAdmin())
    }

    @Test
    fun `esAdmin retorna false para OPERADOR`() {
        assertFalse(usuario(Rol.OPERADOR).esAdmin())
    }

    @Test
    fun `puedeGestionarBodegas refleja esAdmin`() {
        assertTrue(usuario(Rol.ADMIN).puedeGestionarBodegas())
        assertFalse(usuario(Rol.OPERADOR).puedeGestionarBodegas())
    }

    @Test
    fun `puedeEliminarProductos refleja esAdmin`() {
        assertTrue(usuario(Rol.ADMIN).puedeEliminarProductos())
        assertFalse(usuario(Rol.OPERADOR).puedeEliminarProductos())
    }

    @Test
    fun `puedeRegistrarMovimientos es true para cualquier rol`() {
        assertTrue(usuario(Rol.ADMIN).puedeRegistrarMovimientos())
        assertTrue(usuario(Rol.OPERADOR).puedeRegistrarMovimientos())
    }
}
