package cl.stockflow.warehouse.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductoTest {

    private fun producto(
        stockActual: Int = 10,
        stockMinimo: Int = 5,
        precio: Int = 1000,
        descripcion: String? = null,
        sku: String? = null
    ) = Producto(
        id = "p1", nombre = "Test", descripcion = descripcion, sku = sku,
        precio = precio, stockMinimo = stockMinimo, stockActual = stockActual,
        bodegaId = "b1", empresaId = "e1"
    )

    @Test
    fun `esBajoStock retorna true cuando stock menor que minimo`() {
        assertTrue(producto(stockActual = 2, stockMinimo = 5).esBajoStock())
    }

    @Test
    fun `esBajoStock retorna false cuando stock igual al minimo`() {
        assertFalse(producto(stockActual = 5, stockMinimo = 5).esBajoStock())
    }

    @Test
    fun `esBajoStock retorna false cuando stock mayor al minimo`() {
        assertFalse(producto(stockActual = 10, stockMinimo = 5).esBajoStock())
    }

    @Test
    fun `valorInventario es precio por stockActual`() {
        assertEquals(5000, producto(stockActual = 5, precio = 1000).valorInventario())
    }

    @Test
    fun `valorInventario es cero cuando stock es cero`() {
        assertEquals(0, producto(stockActual = 0, precio = 1000).valorInventario())
    }

    @Test
    fun `ratioStock retorna cociente stockActual sobre stockMinimo`() {
        assertEquals(2f, producto(stockActual = 10, stockMinimo = 5).ratioStock(), 0.001f)
    }

    @Test
    fun `ratioStock retorna 1 cuando stockMinimo es cero`() {
        assertEquals(1f, producto(stockActual = 10, stockMinimo = 0).ratioStock(), 0.001f)
    }

    @Test
    fun `tieneStock retorna true cuando stock mayor a cero`() {
        assertTrue(producto(stockActual = 1).tieneStock())
    }

    @Test
    fun `tieneStock retorna false cuando stock es cero`() {
        assertFalse(producto(stockActual = 0).tieneStock())
    }

    @Test
    fun `descripcionCompleta con descripcion y sku`() {
        assertEquals(
            "Paracetamol 500mg · SKU: PARA-01",
            producto(descripcion = "Paracetamol 500mg", sku = "PARA-01").descripcionCompleta()
        )
    }

    @Test
    fun `descripcionCompleta solo con descripcion`() {
        assertEquals("Paracetamol 500mg", producto(descripcion = "Paracetamol 500mg").descripcionCompleta())
    }

    @Test
    fun `descripcionCompleta solo con sku`() {
        assertEquals("SKU: PARA-01", producto(sku = "PARA-01").descripcionCompleta())
    }

    @Test
    fun `descripcionCompleta vacia cuando ambos son null`() {
        assertEquals("", producto().descripcionCompleta())
    }

    @Test
    fun `toDomain en ProductoConStock mapea todos los campos`() {
        val pcs = ProductoConStock(
            id = "p1", empresa_id = "e1", bodega_id = "b1",
            nombre = "Mouse", descripcion = "Inalámbrico", sku = "MS-01",
            precio = 8000, stock_minimo = 12, stock_actual = 22,
            synced = true, synced_at = null,
            created_at = java.util.Date(), updated_at = java.util.Date()
        )
        val domain = pcs.toDomain()
        assertEquals("p1", domain.id)
        assertEquals("Mouse", domain.nombre)
        assertEquals("Inalámbrico", domain.descripcion)
        assertEquals("MS-01", domain.sku)
        assertEquals(8000, domain.precio)
        assertEquals(12, domain.stockMinimo)
        assertEquals(22, domain.stockActual)
        assertEquals("b1", domain.bodegaId)
        assertEquals("e1", domain.empresaId)
    }
}
