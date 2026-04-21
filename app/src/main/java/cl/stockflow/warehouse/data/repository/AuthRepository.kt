package cl.stockflow.warehouse.data.repository

import cl.stockflow.warehouse.data.local.dao.AuthSessionDao
import cl.stockflow.warehouse.data.local.entity.AuthSessionEntity
import cl.stockflow.warehouse.data.remote.supabaseClient
import cl.stockflow.warehouse.domain.model.SesionUsuario
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
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
    private val authSessionDao: AuthSessionDao
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

            val user = supabaseClient.gotrue.retrieveUserForCurrentSession(updateSession = true)

            // Obtener empresa_id desde tabla usuarios (más confiable que JWT claims en MVP)
            val fila = supabaseClient.postgrest["usuarios"]
                .select { eq("id", user.id) }
                .decodeSingle<JsonObject>()

            val empresa_id = fila["empresa_id"]?.jsonPrimitive?.content
                ?: return Result.failure(Exception("Usuario sin empresa asignada"))

            val expires_ms = session.expiresAt.toEpochMilliseconds()
            authSessionDao.guardarSesion(
                AuthSessionEntity(
                    access_token = session.accessToken,
                    refresh_token = session.refreshToken,
                    user_id = user.id,
                    empresa_id = empresa_id,
                    expires_at = Date(expires_ms)
                )
            )

            Result.success(
                SesionUsuario(
                    user_id = user.id,
                    empresa_id = empresa_id,
                    access_token = session.accessToken,
                    refresh_token = session.refreshToken,
                    expires_at = expires_ms
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "AUTH: error en login")
            Result.failure(Exception("Email o contraseña incorrecta"))
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
            // 1. Crear usuario en Supabase Auth
            supabaseClient.gotrue.signUpWith(Email) {
                email = correo.trim()
                password = contrasena
            }

            Timber.d("AUTH: signUpWith OK, haciendo login")
            // 2. Login para obtener token
            supabaseClient.gotrue.loginWith(Email) {
                email = correo.trim()
                password = contrasena
            }
            Timber.d("AUTH: loginWith OK post-registro")

            val session = supabaseClient.gotrue.currentSessionOrNull()
                ?: return Result.failure(Exception("Error al iniciar sesión tras registro"))

            val user = supabaseClient.gotrue.retrieveUserForCurrentSession(updateSession = true)
            Timber.d("AUTH: user.id=${user.id}")

            // 3. Crear empresa y obtener su id
            val empresa = supabaseClient.postgrest["empresas"]
                .insert(buildJsonObject {
                    put("nombre", nombre_empresa)
                    put("rubro", rubro)
                })
                .decodeSingle<JsonObject>()

            val empresa_id = empresa["id"]?.jsonPrimitive?.content
                ?: return Result.failure(Exception("Error al crear empresa"))
            Timber.d("AUTH: empresa creada id=$empresa_id")

            // 4. Crear registro de usuario con rol ADMIN
            supabaseClient.postgrest["usuarios"].insert(
                buildJsonObject {
                    put("id", user.id)
                    put("empresa_id", empresa_id)
                    put("nombre", correo.trim().substringBefore("@"))
                    put("email", correo.trim())
                    put("rol", "ADMIN")
                }
            )

            // 5. Crear bodega default
            supabaseClient.postgrest["bodegas"].insert(
                buildJsonObject {
                    put("empresa_id", empresa_id)
                    put("nombre", "Bodega Principal")
                }
            )

            val expires_ms = session.expiresAt.toEpochMilliseconds()
            authSessionDao.guardarSesion(
                AuthSessionEntity(
                    access_token = session.accessToken,
                    refresh_token = session.refreshToken,
                    user_id = user.id,
                    empresa_id = empresa_id,
                    expires_at = Date(expires_ms)
                )
            )

            Result.success(
                SesionUsuario(
                    user_id = user.id,
                    empresa_id = empresa_id,
                    access_token = session.accessToken,
                    refresh_token = session.refreshToken,
                    expires_at = expires_ms
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "AUTH: error en registro")
            val mensaje = when {
                e.message?.contains("already registered") == true -> "Email ya registrado"
                else -> "Error al registrar: ${e.message}"
            }
            Result.failure(Exception(mensaje))
        }
    }

    suspend fun logout() {
        try {
            supabaseClient.gotrue.logout()
        } finally {
            authSessionDao.limpiarSesion()
        }
    }

    suspend fun checkSession(): AuthSessionEntity? = authSessionDao.obtenerSesion()
}
