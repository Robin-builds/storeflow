package cl.stockflow.warehouse.data.repository

import cl.stockflow.warehouse.data.local.AppDatabase
import cl.stockflow.warehouse.data.local.dao.AuthSessionDao
import cl.stockflow.warehouse.data.local.entity.AuthSessionEntity
import cl.stockflow.warehouse.data.remote.supabaseClient
import cl.stockflow.warehouse.data.sync.PullTrigger
import cl.stockflow.warehouse.domain.model.Rol
import cl.stockflow.warehouse.domain.model.SesionUsuario
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authSessionDao: AuthSessionDao,
    private val db: AppDatabase,
    private val pullTrigger: PullTrigger
) {

    fun observarSesion(): Flow<AuthSessionEntity?> = authSessionDao.observarSesion()

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
                    expires_at = Date(expires_ms)
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
            Result.failure(Exception("Error: ${e.message}"))
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
                    expires_at = Date(expires_ms)
                )
            )

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
                else -> "Error al registrar: ${e.message}"
            }
            Result.failure(Exception(mensaje))
        }
    }

    suspend fun logout() {
        try {
            supabaseClient.gotrue.logout()
        } finally {
            withContext(Dispatchers.IO) { db.clearAllTables() }
        }
    }

    suspend fun checkSession(): AuthSessionEntity? {
        val sesion = authSessionDao.obtenerSesion() ?: return null
        if (sesion.expires_at.before(Date())) {
            Timber.d("AUTH: token expirado, limpiando sesión")
            withContext(Dispatchers.IO) { db.clearAllTables() }
            return null
        }
        return sesion
    }

    suspend fun obtenerRolActual(): Rol? {
        val sesion = authSessionDao.obtenerSesion() ?: return null
        return Rol.fromString(sesion.rol)
    }
}
