package cl.stockflow.warehouse.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductoConAtributosTest {

    private fun producto(atributos: Map<String, String> = emptyMap()) = Producto(
        id = "p1",
        nombre = "Paracetamol",
        descripcion = null,
        sku = null,
        precio = 500,
        stockMinimo = 10,
        stockActual = 20,
        bodegaId = "b1",
        empresaId = "e1",
        atributos = atributos
    )

    @Test
    fun `producto sin atributos tiene mapa vacio`() {
        assertTrue(producto().atributos.isEmpty())
    }

    @Test
    fun `atributos se almacenan como clave-valor`() {
        val attrs = mapOf("principio_activo" to "Acetaminofén", "concentracion" to "500mg")
        val p = producto(attrs)
        assertEquals("Acetaminofén", p.atributos["principio_activo"])
        assertEquals("500mg", p.atributos["concentracion"])
    }

    @Test
    fun `atributo inexistente retorna null`() {
        val p = producto(mapOf("principio_activo" to "Acetaminofén"))
        assertEquals(null, p.atributos["fabricante"])
    }

    @Test
    fun `producto con atributos mantiene comportamiento de dominio`() {
        val p = producto(mapOf("principio_activo" to "Acetaminofén"))
        assertTrue(p.esBajoStock().not())
        assertEquals(10000, p.valorInventario())
    }

    @Test
    fun `atributos no afectan esBajoStock`() {
        val bajoProdSinAttr = producto().copy(stockActual = 5)
        val bajoProdConAttr = producto(mapOf("clave" to "valor")).copy(stockActual = 5)
        assertEquals(bajoProdSinAttr.esBajoStock(), bajoProdConAttr.esBajoStock())
    }
}
