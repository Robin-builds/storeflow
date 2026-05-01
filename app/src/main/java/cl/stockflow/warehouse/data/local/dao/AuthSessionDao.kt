package cl.stockflow.warehouse.data.local.dao

import androidx.room.*
import cl.stockflow.warehouse.data.local.entity.AuthSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthSessionDao {
    @Query("SELECT * FROM auth_sessions WHERE id = 1 LIMIT 1")
    fun observarSesion(): Flow<AuthSessionEntity?>

    @Query("SELECT * FROM auth_sessions WHERE id = 1 LIMIT 1")
    suspend fun obtenerSesion(): AuthSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarSesion(sesion: AuthSessionEntity)

    @Query("UPDATE auth_sessions SET bodega_id = :bodegaId WHERE id = 1")
    suspend fun actualizarBodegaActiva(bodegaId: String)

    @Query("DELETE FROM auth_sessions")
    suspend fun limpiarSesion()
}
