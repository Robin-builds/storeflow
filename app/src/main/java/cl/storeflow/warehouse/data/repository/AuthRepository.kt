package cl.storeflow.warehouse.data.repository

import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.local.entity.AuthSessionEntity
import cl.storeflow.warehouse.data.remote.SUPABASE_ANON_KEY
import cl.storeflow.warehouse.data.remote.SUPABASE_URL
import cl.storeflow.warehouse.data.remote.supabaseClient
import cl.storeflow.warehouse.data.sync.GestorSesion
import cl.storeflow.warehouse.data.sync.PullTrigger
import cl.storeflow.warehouse.data.sync.ResultadoToken
import cl.storeflow.warehouse.domain.model.Rol
import cl.storeflow.warehouse.domain.model.SesionUsuario
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authSessionDao: AuthSessionDao,
    private val gestorSesion: GestorSesion,
    private val pullTrigger: PullTrigger,
    private val planRepository: PlanRepository
) {

    private companion object {
        const val MENSAJE_OTRA_CUENTA =
            "Este dispositivo tiene trabajo sin sincronizar de otra cuenta. Ingresa con esa cuenta para enviarlo."
        const val MENSAJE_LOGOUT_BLOQUEADO =
            "Tienes trabajo sin sincronizar. Conéctate y espera a que se envíe antes de cerrar sesión."
    }

    fun observarSesion(): Flow<AuthSessionEntity?> = authSessionDao.observarSesion()

    private fun esErrorDeConexion(e: Throwable): Boolean {
        val causa = e.cause
        return e is java.net.UnknownHostException ||
            causa is java.net.UnknownHostException ||
            e.message?.contains("Unable to resolve host") == true
    }

    private fun mensajeError(e: Throwable, generico: String): String {
        if (esErrorDeConexion(e)) {
            return "Sin conexión a internet. Verifica tu WiFi o datos móviles e intenta nuevamente."
        }
        return generico
    }

    suspend fun login(correo: String, contrasena: String): Result<SesionUsuario> {
        return try {
            Timber.d("AUTH: iniciando login para $correo")
            supabaseClient.gotrue.loginWith(Email) {
                email = correo.trim()
                password = contrasena
            }
            Timber.d("AUTH: loginWith OK")

            val session = supabaseClient.gotrue.currentSessionOrNull()
                ?: return Result.failure(Exception("No se pudo obtener la sesión"))
            Timber.d("AUTH: session obtenida, accessToken=${session.accessToken.take(20)}...")

            val user = supabaseClient.gotrue.retrieveUserForCurrentSession(updateSession = true)
            Timber.d("AUTH: user.id=${user.id}")

            if (!gestorSesion.puedeIngresar(user.id)) {
                supabaseClient.gotrue.clearSession()
                return Result.failure(Exception(MENSAJE_OTRA_CUENTA))
            }

            // Antes de leer usuarios: un excedente no puede leer datos de la empresa (R20).
            // Un fallo de red no impide el ingreso.
            val validacion = planRepository.validar(session.accessToken)
            if (validacion is ResultadoValidacion.Actualizado && validacion.bloqueado) {
                val expira = session.expiresAt.toEpochMilliseconds()
                authSessionDao.guardarSesion(
                    AuthSessionEntity(
                        access_token = session.accessToken,
                        refresh_token = session.refreshToken,
                        user_id = user.id,
                        empresa_id = validacion.empresaId,
                        bodega_id = "",
                        rol = validacion.rol,
                        expires_at = Date(expira),
                        correo = correo.trim()
                    )
                )
                return Result.success(
                    SesionUsuario(
                        user_id = user.id,
                        empresa_id = validacion.empresaId,
                        access_token = session.accessToken,
                        refresh_token = session.refreshToken,
                        expires_at = expira,
                        rol = Rol.fromString(validacion.rol)
                    )
                )
            }

            Timber.d("AUTH: consultando tabla usuarios...")
            val filas = supabaseClient.postgrest["usuarios"]
                .select(filter = { eq("id", user.id) })
                .decodeList<JsonObject>()
            Timber.d("AUTH: filas encontradas: ${filas.size}")
            val fila = filas.firstOrNull()
                ?: return Result.failure(Exception("Usuario no tiene perfil en la base de datos. Contacte al administrador."))

            val empresa_id = fila["empresa_id"]?.jsonPrimitive?.content
                ?: return Result.failure(Exception("Usuario sin empresa asignada"))
            val rol = fila["rol"]?.jsonPrimitive?.content ?: "OPERADOR"
            Timber.d("AUTH: empresa_id=$empresa_id, rol=$rol")

            Timber.d("AUTH: consultando bodegas...")
            val bodegaFila = supabaseClient.postgrest["bodegas"]
                .select(filter = { eq("empresa_id", empresa_id) })
                .decodeList<JsonObject>()
                .firstOrNull()
                ?: return Result.failure(Exception("No hay bodegas configuradas para esta empresa"))
            val bodega_id = bodegaFila["id"]?.jsonPrimitive?.content
                ?: return Result.failure(Exception("Bodega sin ID"))
            Timber.d("AUTH: bodega_id=$bodega_id")

            val expires_ms = session.expiresAt.toEpochMilliseconds()
            authSessionDao.guardarSesion(
                AuthSessionEntity(
                    access_token = session.accessToken,
                    refresh_token = session.refreshToken,
                    user_id = user.id,
                    empresa_id = empresa_id,
                    bodega_id = bodega_id,
                    rol = rol,
                    expires_at = Date(expires_ms),
                    correo = correo.trim()
                )
            )
            Timber.d("AUTH: sesion guardada en Room OK")
            pullTrigger.trigger()

            Result.success(
                SesionUsuario(
                    user_id = user.id,
                    empresa_id = empresa_id,
                    access_token = session.accessToken,
                    refresh_token = session.refreshToken,
                    expires_at = expires_ms,
                    rol = Rol.fromString(rol)
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "AUTH: error en login — ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(Exception(mensajeError(e, "Error: ${e.message}")))
        }
    }

    suspend fun registrar(
        nombre_empresa: String,
        rubro: String,
        correo: String,
        contrasena: String
    ): Result<SesionUsuario> {
        return try {
            Timber.d("AUTH: iniciando registro para $correo, empresa=$nombre_empresa")

            // Antes de signUp: una cuenta nueva no debe crearse si no podrá usar este dispositivo.
            if (!gestorSesion.puedeRegistrar(correo)) {
                return Result.failure(Exception(MENSAJE_OTRA_CUENTA))
            }

            // 1. Crear usuario en Auth — si ya existe, podría ser un usuario huérfano
            var esRecuperacion = false
            try {
                supabaseClient.gotrue.signUpWith(Email) {
                    email = correo.trim()
                    password = contrasena
                }
                Timber.d("AUTH: signUpWith OK")
            } catch (e: Exception) {
                if (e.message?.contains("already registered") == true ||
                    e.message?.contains("User already registered") == true) {
                    Timber.d("AUTH: usuario ya existe en Auth, verificando si es huérfano")
                    esRecuperacion = true
                } else {
                    throw e
                }
            }

            // 2. Login para obtener token
            supabaseClient.gotrue.loginWith(Email) {
                email = correo.trim()
                password = contrasena
            }
            Timber.d("AUTH: loginWith OK")

            val session = supabaseClient.gotrue.currentSessionOrNull()
                ?: return Result.failure(Exception("Error al iniciar sesión"))
            val user = supabaseClient.gotrue.retrieveUserForCurrentSession(updateSession = true)
            Timber.d("AUTH: user.id=${user.id}")

            if (!gestorSesion.puedeIngresar(user.id)) {
                supabaseClient.gotrue.clearSession()
                return Result.failure(Exception(MENSAJE_OTRA_CUENTA))
            }

            // 3. Si es recuperación, verificar si ya tiene perfil completo
            if (esRecuperacion) {
                val perfilExistente = supabaseClient.postgrest["usuarios"]
                    .select(filter = { eq("id", user.id) })
                    .decodeList<JsonObject>()
                    .firstOrNull()
                if (perfilExistente != null) {
                    Timber.d("AUTH: perfil existe, correo ya registrado")
                    return Result.failure(Exception("Email ya registrado. Por favor inicia sesión."))
                }
                Timber.d("AUTH: usuario huérfano detectado, completando registro")
            }

            // 4. Crear empresa + usuario + bodega en una sola función atómica (SECURITY DEFINER bypasea RLS)
            val rpcResult = supabaseClient.postgrest.rpc(
                "registrar_empresa",
                buildJsonObject {
                    put("p_nombre", nombre_empresa)
                    put("p_rubro", rubro)
                    put("p_correo", correo.trim())
                }
            ).decodeAs<JsonObject>()

            val empresa_id = rpcResult["empresa_id"]?.jsonPrimitive?.content
                ?: return Result.failure(Exception("Error al crear empresa"))
            Timber.d("AUTH: empresa creada id=$empresa_id")

            val bodega_id = rpcResult["bodega_id"]?.jsonPrimitive?.content
                ?: supabaseClient.postgrest["bodegas"]
                    .select(filter = { eq("empresa_id", empresa_id) })
                    .decodeList<JsonObject>()
                    .firstOrNull()?.get("id")?.jsonPrimitive?.content
                ?: return Result.failure(Exception("Error al obtener bodega"))
            Timber.d("AUTH: bodega_id=$bodega_id")

            val expires_ms = session.expiresAt.toEpochMilliseconds()
            authSessionDao.guardarSesion(
                AuthSessionEntity(
                    access_token = session.accessToken,
                    refresh_token = session.refreshToken,
                    user_id = user.id,
                    empresa_id = empresa_id,
                    bodega_id = bodega_id,
                    rol = Rol.ADMIN.name,   // registrar_empresa siempre crea ADMIN
                    expires_at = Date(expires_ms),
                    correo = correo.trim()
                )
            )

            planRepository.validar(session.accessToken)

            Timber.d("AUTH: registro completo OK")
            Result.success(
                SesionUsuario(
                    user_id = user.id,
                    empresa_id = empresa_id,
                    access_token = session.accessToken,
                    refresh_token = session.refreshToken,
                    expires_at = expires_ms,
                    rol = Rol.ADMIN
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "AUTH: error en registro")
            val mensaje = when {
                e.message?.contains("Invalid login credentials") == true ->
                    "Contraseña incorrecta. Si ya tienes cuenta, inicia sesión."
                else -> mensajeError(e, "Error al registrar: ${e.message}")
            }
            Result.failure(Exception(mensaje))
        }
    }

    suspend fun registrarUsuarioEnEmpresa(
        email: String,
        password: String,
        nombre: String
    ): Result<String> {
        val sesion = authSessionDao.obtenerSesion()
            ?: return Result.failure(Exception("Sin sesión activa"))
        val httpClient = HttpClient(Android) { expectSuccess = false }
        return try {
            Timber.d("AUTH: registrarUsuarioEnEmpresa email=$email")
            val body = buildJsonObject {
                put("email", email.trim())
                put("password", password)
                put("nombre", nombre.trim())
            }.toString()
            val response = httpClient.post("$SUPABASE_URL/functions/v1/registrar-usuario-empresa") {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer ${sesion.access_token}")
                    append("Content-Type", "application/json")
                }
                setBody(body)
            }
            val responseBody = response.bodyAsText()
            Timber.d("AUTH: registrarUsuarioEnEmpresa HTTP ${response.status.value} — $responseBody")
            if (!response.status.isSuccess()) {
                val msg = try {
                    Json.parseToJsonElement(responseBody).jsonObject["error"]?.jsonPrimitive?.content
                        ?: responseBody
                } catch (e: Exception) { responseBody }
                Result.failure(Exception(msg))
            } else {
                val userId = Json.parseToJsonElement(responseBody).jsonObject["user_id"]?.jsonPrimitive?.content
                    ?: return Result.failure(Exception("Respuesta inesperada del servidor"))
                Result.success(userId)
            }
        } catch (e: Exception) {
            Timber.e(e, "AUTH: error en registrarUsuarioEnEmpresa")
            Result.failure(Exception("Error de conexión: ${e.message}"))
        } finally {
            httpClient.close()
        }
    }

    suspend fun resetearPasswordUsuario(userId: String, password: String): Result<Unit> {
        val sesion = authSessionDao.obtenerSesion()
            ?: return Result.failure(Exception("Sin sesión activa"))
        val httpClient = HttpClient(Android) { expectSuccess = false }
        return try {
            Timber.d("AUTH: resetearPasswordUsuario userId=$userId")
            val body = buildJsonObject {
                put("user_id", userId)
                put("password", password)
            }.toString()
            val response = httpClient.post("$SUPABASE_URL/functions/v1/resetear-password-usuario") {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer ${sesion.access_token}")
                    append("Content-Type", "application/json")
                }
                setBody(body)
            }
            val responseBody = response.bodyAsText()
            Timber.d("AUTH: resetearPasswordUsuario HTTP ${response.status.value} — $responseBody")
            if (!response.status.isSuccess()) {
                val msg = try {
                    Json.parseToJsonElement(responseBody).jsonObject["error"]?.jsonPrimitive?.content
                        ?: responseBody
                } catch (e: Exception) { responseBody }
                Result.failure(Exception(msg))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "AUTH: error en resetearPasswordUsuario")
            Result.failure(Exception("Error de conexión: ${e.message}"))
        } finally {
            httpClient.close()
        }
    }

    suspend fun logout(): Result<Unit> {
        // Se revisa antes de revocar la sesión en el servidor (FR-030).
        if (gestorSesion.tieneTrabajoSinSincronizar()) {
            return Result.failure(Exception(MENSAJE_LOGOUT_BLOQUEADO))
        }
        try {
            supabaseClient.gotrue.logout()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "AUTH: logout remoto falló, se limpia la sesión local")
        }
        return if (gestorSesion.limpiarSiNoHayTrabajo()) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(MENSAJE_LOGOUT_BLOQUEADO))
        }
    }

    suspend fun checkSession(): AuthSessionEntity? =
        when (val resultado = gestorSesion.obtenerToken()) {
            is ResultadoToken.Vigente -> {
                gestorSesion.importarAlCliente(resultado.sesion)
                resultado.sesion
            }
            is ResultadoToken.SinRed -> {
                Timber.d("AUTH: sin red, se mantiene la sesión guardada")
                gestorSesion.importarAlCliente(resultado.sesion)
                resultado.sesion
            }
            is ResultadoToken.SesionInvalida -> {
                Timber.w("AUTH: sesión inválida, datos conservados=${resultado.datosConservados}")
                null
            }
            ResultadoToken.SinSesion -> null
        }

    suspend fun obtenerRolActual(): Rol? {
        val sesion = authSessionDao.obtenerSesion() ?: return null
        return Rol.fromString(sesion.rol)
    }

    suspend fun cambiarPassword(actual: String, nueva: String): Result<Unit> {
        val sesion = authSessionDao.obtenerSesion()
            ?: return Result.failure(Exception("Sin sesión activa"))
        return try {
            Timber.d("AUTH: verificando contraseña actual para cambio")
            supabaseClient.gotrue.loginWith(Email) {
                email = sesion.correo
                password = actual
            }
            Timber.d("AUTH: contraseña actual verificada, aplicando cambio")
            supabaseClient.gotrue.modifyUser {
                password = nueva
            }
            val nuevaSesion = supabaseClient.gotrue.currentSessionOrNull()
                ?: throw Exception("sesión nula tras cambio de contraseña")
            val actualizada = sesion.copy(
                access_token = nuevaSesion.accessToken,
                refresh_token = nuevaSesion.refreshToken,
                expires_at = Date(nuevaSesion.expiresAt.toEpochMilliseconds()),
                updated_at = Date()
            )
            withContext(Dispatchers.IO) { authSessionDao.guardarSesion(actualizada) }
            Timber.d("AUTH: contraseña cambiada OK, sesión actualizada persistida en Room")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "AUTH: error cambiando password")
            val mensaje = if (e.message?.contains("Invalid login credentials") == true)
                "Contraseña actual incorrecta"
            else mensajeError(e, "Error al cambiar contraseña: ${e.message}")
            Result.failure(Exception(mensaje))
        }
    }
}
