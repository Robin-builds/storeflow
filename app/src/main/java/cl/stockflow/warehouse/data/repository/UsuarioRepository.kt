package cl.stockflow.warehouse.data.repository

import cl.stockflow.warehouse.data.local.dao.AuthSessionDao
import cl.stockflow.warehouse.data.local.dao.UsuarioDao
import cl.stockflow.warehouse.data.local.entity.UsuarioEntity
import cl.stockflow.warehouse.data.remote.SUPABASE_ANON_KEY
import cl.stockflow.warehouse.data.remote.SUPABASE_URL
import cl.stockflow.warehouse.domain.model.Rol
import cl.stockflow.warehouse.domain.model.Usuario
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsuarioRepository @Inject constructor(
    private val authSessionDao: AuthSessionDao,
    private val usuarioDao: UsuarioDao
) {
    // sesion.rol es la fuente autoritativa — funciona incluso antes de que PullWorker cargue UsuarioEntity
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observarUsuarioActual(): Flow<Usuario?> = authSessionDao.observarSesion()
        .flatMapLatest { sesion ->
            if (sesion == null) flowOf(null)
            else flow {
                val entity = usuarioDao.obtenerPorId(sesion.user_id)
                emit(Usuario(
                    id = sesion.user_id,
                    nombre = entity?.nombre ?: "",
                    email = entity?.email ?: "",
                    rol = Rol.fromString(sesion.rol),
                    empresaId = sesion.empresa_id
                ))
            }
        }

    suspend fun obtenerUsuarioActual(): Usuario? {
        val sesion = authSessionDao.obtenerSesion() ?: return null
        val entity = usuarioDao.obtenerPorId(sesion.user_id)
        return Usuario(
            id = sesion.user_id,
            nombre = entity?.nombre ?: "",
            email = entity?.email ?: "",
            rol = Rol.fromString(sesion.rol),
            empresaId = sesion.empresa_id
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observarUsuariosDeEmpresa(): Flow<List<Usuario>> = authSessionDao.observarSesion()
        .flatMapLatest { sesion ->
            if (sesion == null) flowOf(emptyList())
            else usuarioDao.observarPorEmpresa(sesion.empresa_id)
                .map { list -> list.map { it.toDomain() } }
        }

    suspend fun eliminar(usuario: Usuario): Result<Unit> {
        val sesion = authSessionDao.obtenerSesion()
            ?: return Result.failure(Exception("Sin sesión activa"))
        val httpClient = HttpClient(Android) { expectSuccess = false }
        return try {
            val response = httpClient.delete(
                "$SUPABASE_URL/rest/v1/usuarios?id=eq.${usuario.id}"
            ) {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer ${sesion.access_token}")
                }
            }
            if (!response.status.isSuccess()) {
                Timber.e("USUARIO: HTTP ${response.status.value} eliminando ${usuario.id} — ${response.bodyAsText()}")
                return Result.failure(Exception("Error al eliminar usuario"))
            }
            val entity = usuarioDao.obtenerPorId(usuario.id)
            if (entity != null) usuarioDao.eliminar(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "USUARIO: error eliminando ${usuario.id}")
            Result.failure(Exception("Error de conexión: ${e.message}"))
        } finally {
            httpClient.close()
        }
    }

    suspend fun cambiarRol(usuario: Usuario, nuevoRol: Rol): Result<Unit> {
        val sesion = authSessionDao.obtenerSesion()
            ?: return Result.failure(Exception("Sin sesión activa"))
        val httpClient = HttpClient(Android) { expectSuccess = false }
        return try {
            val body = buildJsonObject { put("rol", nuevoRol.name) }.toString()
            val response = httpClient.patch(
                "$SUPABASE_URL/rest/v1/usuarios?id=eq.${usuario.id}"
            ) {
                headers {
                    append("apikey", SUPABASE_ANON_KEY)
                    append("Authorization", "Bearer ${sesion.access_token}")
                    append("Content-Type", "application/json")
                    append("Prefer", "return=minimal")
                }
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                Timber.e("USUARIO: HTTP ${response.status.value} cambiando rol ${usuario.id} — ${response.bodyAsText()}")
                return Result.failure(Exception("Error al cambiar rol"))
            }
            val entity = usuarioDao.obtenerPorId(usuario.id)
            if (entity != null) usuarioDao.actualizar(entity.copy(rol = nuevoRol.name, updated_at = Date()))
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "USUARIO: error cambiando rol ${usuario.id}")
            Result.failure(Exception("Error de conexión: ${e.message}"))
        } finally {
            httpClient.close()
        }
    }

    suspend fun insertarLocal(id: String, email: String, nombre: String, rol: Rol): Result<Unit> {
        return try {
            val sesion = authSessionDao.obtenerSesion()
                ?: return Result.failure(Exception("Sin sesión activa"))
            usuarioDao.insertar(
                UsuarioEntity(
                    id = id,
                    empresa_id = sesion.empresa_id,
                    nombre = nombre,
                    email = email,
                    rol = rol.name,
                    synced = true
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "USUARIO: error insertando local $id")
            Result.failure(e)
        }
    }

    private fun UsuarioEntity.toDomain() = Usuario(
        id = id,
        nombre = nombre,
        email = email,
        rol = Rol.fromString(rol),
        empresaId = empresa_id
    )
}
