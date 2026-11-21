package cl.storeflow.warehouse.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** DEF-2: la pantalla nunca muestra URL, headers ni claves de una excepción. */
class MensajesAuthTest {

    private val generico = "No se pudo completar la operación."

    private fun errorDeSupabase(mensaje: String) = Exception(
        "$mensaje\nURL: https://proyecto.supabase.co/auth/v1/token\n" +
            "Headers: [Authorization=[Bearer eyJabc.def.ghi], apikey=[eyJabc.def.ghi]]\nHttp Method: POST"
    )

    @Test
    fun `un error desconocido usa el texto generico y no filtra datos tecnicos`() {
        val texto = mensajeAuthSeguro(errorDeSupabase("Algo inesperado"), generico)

        assertEquals(generico, texto)
        listOf("URL", "Bearer", "apikey", "eyJ", "supabase").forEach {
            assertFalse(texto.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun `correo sin confirmar tiene mensaje propio y limpio`() {
        assertEquals(
            "Debes confirmar tu correo antes de ingresar.",
            mensajeAuthSeguro(errorDeSupabase("Email not confirmed"), generico)
        )
    }

    @Test
    fun `credenciales invalidas tienen mensaje propio`() {
        assertEquals(
            "Correo o contraseña incorrectos.",
            mensajeAuthSeguro(errorDeSupabase("Invalid login credentials"), generico)
        )
    }

    @Test
    fun `correo ya registrado tiene mensaje propio`() {
        assertEquals(
            "Este correo ya está registrado.",
            mensajeAuthSeguro(errorDeSupabase("User already registered"), generico)
        )
    }

    @Test
    fun `los errores de red indican falta de conexion, tambien como causa`() {
        val esperado = "Sin conexión a internet. Verifica tu WiFi o datos móviles e intenta nuevamente."
        listOf(
            UnknownHostException("host"),
            ConnectException("refused"),
            SocketTimeoutException("timeout"),
            Exception("envoltura", UnknownHostException("host")),
            Exception("Unable to resolve host \"proyecto.supabase.co\"")
        ).forEach { assertEquals(esperado, mensajeAuthSeguro(it, generico)) }
    }
}
