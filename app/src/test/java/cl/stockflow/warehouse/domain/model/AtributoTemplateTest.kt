package cl.stockflow.warehouse.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtributoTemplateTest {

    private fun template(
        tipo: TipoAtributo = TipoAtributo.TEXT,
        obligatorio: Boolean = false,
        clave: String = "principio_activo",
        etiqueta: String = "Principio activo",
        orden: Int = 0
    ) = AtributoTemplate(
        id = "t1",
        empresaId = "e1",
        clave = clave,
        etiqueta = etiqueta,
        tipo = tipo,
        obligatorio = obligatorio,
        orden = orden
    )

    @Test
    fun `tipo TEXT es el tipo por defecto del MVP`() {
        assertEquals(TipoAtributo.TEXT, template().tipo)
    }

    @Test
    fun `obligatorio false por defecto`() {
        assertFalse(template().obligatorio)
    }

    @Test
    fun `template obligatorio se marca correctamente`() {
        assertTrue(template(obligatorio = true).obligatorio)
    }

    @Test
    fun `todos los tipos de atributo existen en el enum`() {
        val tipos = TipoAtributo.values().map { it.name }
        assertTrue(tipos.contains("TEXT"))
        assertTrue(tipos.contains("NUMBER"))
        assertTrue(tipos.contains("DATE"))
    }

    @Test
    fun `orden se almacena correctamente`() {
        assertEquals(3, template(orden = 3).orden)
    }
}
