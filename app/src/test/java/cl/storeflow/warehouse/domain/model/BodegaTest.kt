package cl.storeflow.warehouse.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BodegaTest {

    private fun bodega(nombre: String, ubicacion: String? = null, esActiva: Boolean = false) =
        Bodega(id = "b1", nombre = nombre, ubicacion = ubicacion, empresaId = "e1", esActiva = esActiva)

    @Test
    fun `descripcion retorna nombre cuando ubicacion es null`() {
        assertEquals("Bodega Central", bodega("Bodega Central").descripcion())
    }

    @Test
    fun `descripcion retorna nombre guion ubicacion cuando ubicacion existe`() {
        assertEquals("Bodega Norte — Piso 2", bodega("Bodega Norte", "Piso 2").descripcion())
    }

    @Test
    fun `esActiva refleja el valor del constructor`() {
        assertEquals(false, bodega("B1", esActiva = false).esActiva)
        assertEquals(true, bodega("B1", esActiva = true).esActiva)
    }
}
