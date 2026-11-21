package cl.storeflow.warehouse.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CapacidadesDto(val export: Boolean = false, val lotes: Boolean = false)

@Serializable
data class PermisosDto(val crear_bodega: Boolean = false, val crear_usuario: Boolean = false)

@Serializable
data class DegradacionDto(
    val bodegas_activas: List<String> = emptyList(),
    val bloqueado: Boolean = false,
    val capacidades: CapacidadesDto = CapacidadesDto(),
    val permisos: PermisosDto = PermisosDto(),
    val historial_desde: String? = null
)

/** Respuesta de `obtener_entitlement`. Un usuario bloqueado recibe solo cuatro campos. */
@Serializable
data class EntitlementDto(
    val bloqueado: Boolean = false,
    val empresa_id: String,
    val rol: String,
    val plan_efectivo: String? = null,
    val estado: String? = null,
    val capacidades: CapacidadesDto? = null,
    val permisos: PermisosDto? = null,
    val historial_desde: String? = null,
    val trial_fin: String? = null,
    val vencimiento: String? = null,
    val bodegas_solo_lectura: List<String> = emptyList(),
    val degradacion_free: DegradacionDto? = null,
    val ahora_servidor: String
)

sealed interface RespuestaEntitlement {
    data class Ok(val dto: EntitlementDto) : RespuestaEntitlement
    /** La RPC todavía no existe en este ambiente (PGRST202): no se restringe nada. */
    data object SinPlan : RespuestaEntitlement
    data object NoAutorizado : RespuestaEntitlement
    data object SinConexion : RespuestaEntitlement
    data class Error(val detalle: String) : RespuestaEntitlement
}

interface ClientePlan {
    suspend fun obtenerEntitlement(accessToken: String): RespuestaEntitlement
}

private val jsonPlan = Json { ignoreUnknownKeys = true }

internal fun clasificarRespuestaEntitlement(status: Int, cuerpo: String): RespuestaEntitlement {
    if (status in 200..299) {
        return try {
            RespuestaEntitlement.Ok(jsonPlan.decodeFromString<EntitlementDto>(cuerpo))
        } catch (e: SerializationException) {
            RespuestaEntitlement.Error("Respuesta de plan inválida")
        } catch (e: IllegalArgumentException) {
            RespuestaEntitlement.Error("Respuesta de plan inválida")
        }
    }
    if (status == 401) return RespuestaEntitlement.NoAutorizado
    if (status == 408 || status == 429 || status >= 500) return RespuestaEntitlement.SinConexion

    val code = try {
        ((Json.parseToJsonElement(cuerpo) as? JsonObject)?.get("code") as? JsonPrimitive)?.content
    } catch (e: IllegalArgumentException) {
        null
    }
    return if (code == "PGRST202") {
        RespuestaEntitlement.SinPlan
    } else {
        RespuestaEntitlement.Error("HTTP $status ${code.orEmpty()}".trim())
    }
}

@Singleton
class ClientePlanHttp @Inject constructor() : ClientePlan {
    override suspend fun obtenerEntitlement(accessToken: String): RespuestaEntitlement {
        val http = HttpClient(Android) { expectSuccess = false }
        return try {
            val respuesta = http.post("$SUPABASE_URL/rest/v1/rpc/obtener_entitlement") {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer $accessToken")
                    append("Content-Type", "application/json")
                }
                setBody("{}")
            }
            clasificarRespuestaEntitlement(respuesta.status.value, respuesta.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "PLAN: sin respuesta del servidor")
            RespuestaEntitlement.SinConexion
        } finally {
            http.close()
        }
    }
}
