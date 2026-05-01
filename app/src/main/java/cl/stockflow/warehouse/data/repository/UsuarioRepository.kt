package cl.stockflow.warehouse.data.repository

import cl.stockflow.warehouse.data.local.dao.AuthSessionDao
import cl.stockflow.warehouse.data.local.dao.UsuarioDao
import cl.stockflow.warehouse.domain.model.Rol
import cl.stockflow.warehouse.domain.model.Usuario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
}
