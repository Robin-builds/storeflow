package cl.storeflow.warehouse.data.sync

import cl.storeflow.warehouse.data.local.AppDatabase
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.dao.SyncDao
import cl.storeflow.warehouse.data.local.entity.AuthSessionEntity
import cl.storeflow.warehouse.data.remote.SUPABASE_ANON_KEY
import cl.storeflow.warehouse.data.remote.SUPABASE_URL
import cl.storeflow.warehouse.data.remote.supabaseClient
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.gotrue.user.UserSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RespuestaRefresh {
    data class Ok(val accessToken: String, val refreshToken: String, val expiraEnSeg: Long) : RespuestaRefresh
    /** Auth confirmó que el refresh token ya no sirve. */
    data object Rechazado : RespuestaRefresh
    /** Red, timeout, 5xx o respuesta no reconocida: la sesión sigue siendo válida. */
    data object SinRed : RespuestaRefresh
}

sealed interface ResultadoToken {
    data class Vigente(val sesion: AuthSessionEntity) : ResultadoToken
    data class SinRed(val sesion: AuthSessionEntity) : ResultadoToken
    data class SesionInvalida(val datosConservados: Boolean) : ResultadoToken
    data object SinSesion : ResultadoToken
}

interface ClienteRefresh {
    suspend fun refrescar(refreshToken: String): RespuestaRefresh
}

interface ImportadorSesion {
    suspend fun importar(sesion: AuthSessionEntity)
}

private val CODIGOS_TOKEN_INVALIDO = setOf(
    "invalid_grant",
    "refresh_token_not_found",
    "refresh_token_already_used",
    "session_not_found",
    "session_expired"
)

/** Solo un código explícito de Auth invalida la sesión. Ante la duda, se conserva. */
internal fun clasificarRespuestaRefresh(status: Int, cuerpo: String): RespuestaRefresh {
    val json = try {
        Json.parseToJsonElement(cuerpo) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }
    fun texto(campo: String) = (json?.get(campo) as? JsonPrimitive)?.takeIf { it.isString }?.content

    if (status in 200..299) {
        val access = texto("access_token") ?: return RespuestaRefresh.SinRed
        val refresh = texto("refresh_token") ?: return RespuestaRefresh.SinRed
        val expira = (json?.get("expires_in") as? JsonPrimitive)?.content?.toLongOrNull() ?: 3600L
        return RespuestaRefresh.Ok(access, refresh, expira)
    }
    val codigo = texto("error_code") ?: texto("error")
    return if (status in 400..403 && codigo in CODIGOS_TOKEN_INVALIDO) {
        RespuestaRefresh.Rechazado
    } else {
        RespuestaRefresh.SinRed
    }
}

@Singleton
class ClienteRefreshHttp @Inject constructor() : ClienteRefresh {
    override suspend fun refrescar(refreshToken: String): RespuestaRefresh {
        val http = HttpClient(Android) { expectSuccess = false }
        return try {
            val respuesta = http.post("$SUPABASE_URL/auth/v1/token?grant_type=refresh_token") {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Content-Type", "application/json")
                }
                setBody(buildJsonObject { put("refresh_token", refreshToken) }.toString())
            }
            clasificarRespuestaRefresh(respuesta.status.value, respuesta.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "SESION: refresh sin respuesta")
            RespuestaRefresh.SinRed
        } finally {
            http.close()
        }
    }
}

@Singleton
class ImportadorSesionSupabase @Inject constructor() : ImportadorSesion {
    override suspend fun importar(sesion: AuthSessionEntity) {
        val expiraEn = ((sesion.expires_at.time - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        supabaseClient.gotrue.importSession(
            UserSession(
                accessToken = sesion.access_token,
                refreshToken = sesion.refresh_token,
                expiresIn = expiraEn,
                tokenType = "bearer",
                user = null
            ),
            autoRefresh = false
        )
    }
}

/**
 * Única vía de refresh de sesión (R17), con Room como fuente. El Mutex evita que dos actores
 * roten el mismo refresh token. Nunca borra trabajo sin sincronizar (FR-030).
 */
@Singleton
class GestorSesion @Inject constructor(
    private val authSessionDao: AuthSessionDao,
    private val syncDao: SyncDao,
    private val db: AppDatabase,
    private val clienteRefresh: ClienteRefresh,
    private val importador: ImportadorSesion
) {
    private val mutex = Mutex()

    suspend fun obtenerToken(): ResultadoToken = mutex.withLock {
        // Se relee dentro del lock: otro actor pudo haber rotado el token mientras se esperaba.
        val sesion = authSessionDao.obtenerSesion() ?: return@withLock ResultadoToken.SinSesion
        if (sesion.expires_at.after(Date())) return@withLock ResultadoToken.Vigente(sesion)

        when (val respuesta = clienteRefresh.refrescar(sesion.refresh_token)) {
            is RespuestaRefresh.Ok -> {
                val ahora = System.currentTimeMillis()
                val nueva = sesion.copy(
                    access_token = respuesta.accessToken,
                    refresh_token = respuesta.refreshToken,
                    expires_at = Date(ahora + respuesta.expiraEnSeg * 1000),
                    updated_at = Date(ahora)
                )
                authSessionDao.guardarSesion(nueva)
                importarAlCliente(nueva)
                ResultadoToken.Vigente(nueva)
            }
            RespuestaRefresh.SinRed -> ResultadoToken.SinRed(sesion)
            RespuestaRefresh.Rechazado -> {
                val hayTrabajo = syncDao.contarTrabajoSinSincronizar() > 0
                if (hayTrabajo) {
                    Timber.w("SESION: inválida con trabajo sin sincronizar, se conservan los datos")
                } else {
                    limpiarBase()
                }
                ResultadoToken.SesionInvalida(datosConservados = hayTrabajo)
            }
        }
    }

    suspend fun importarAlCliente(sesion: AuthSessionEntity) {
        try {
            importador.importar(sesion)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SESION: no se pudo importar la sesión al cliente Auth")
        }
    }

    suspend fun tieneTrabajoSinSincronizar(): Boolean = syncDao.contarTrabajoSinSincronizar() > 0

    /** Con trabajo en el dispositivo, solo su mismo usuario puede retomar la sesión. */
    suspend fun puedeIngresar(userId: String): Boolean {
        if (!tieneTrabajoSinSincronizar()) return true
        val guardada = authSessionDao.obtenerSesion() ?: return true
        return guardada.user_id == userId
    }

    suspend fun puedeRegistrar(correo: String): Boolean {
        if (!tieneTrabajoSinSincronizar()) return true
        val guardada = authSessionDao.obtenerSesion() ?: return true
        return guardada.correo.equals(correo.trim(), ignoreCase = true)
    }

    suspend fun limpiarSiNoHayTrabajo(): Boolean = mutex.withLock {
        if (tieneTrabajoSinSincronizar()) return@withLock false
        limpiarBase()
        true
    }

    private suspend fun limpiarBase() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }
}
