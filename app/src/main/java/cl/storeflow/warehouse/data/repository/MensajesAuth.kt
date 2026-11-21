package cl.storeflow.warehouse.data.repository

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Traduce una excepción a un texto apto para la pantalla. El mensaje de supabase-kt incluye
 * URL y headers, así que nunca se muestra tal cual: el detalle queda solo en el log.
 */
internal fun mensajeAuthSeguro(e: Throwable, generico: String): String {
    val mensaje = e.message.orEmpty()
    return when {
        esErrorDeRed(e) -> "Sin conexión a internet. Verifica tu WiFi o datos móviles e intenta nuevamente."
        mensaje.contains("Email not confirmed") -> "Debes confirmar tu correo antes de ingresar."
        mensaje.contains("Invalid login credentials") -> "Correo o contraseña incorrectos."
        mensaje.contains("already registered") -> "Este correo ya está registrado."
        else -> generico
    }
}

private fun esErrorDeRed(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.take(5).any {
        it is UnknownHostException || it is ConnectException || it is SocketTimeoutException
    } || e.message?.contains("Unable to resolve host") == true
