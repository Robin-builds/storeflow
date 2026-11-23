package cl.storeflow.warehouse.data.local.dao

import androidx.room.*
import cl.storeflow.warehouse.data.local.entity.SyncEntity
import kotlinx.coroutines.flow.Flow

data class RetenidoPorTipo(val entidad_tipo: String, val cantidad: Int)

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun encolar(sync: SyncEntity)

    @Delete
    suspend fun eliminar(sync: SyncEntity)

    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    suspend fun obtenerCola(): List<SyncEntity>

    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    fun observarCola(): Flow<List<SyncEntity>>

    @Query("UPDATE sync_queue SET reintentos = reintentos + 1, updated_at = :ahora WHERE id = :id")
    suspend fun incrementarReintentos(id: String, ahora: Long = System.currentTimeMillis())

    @Query("SELECT * FROM sync_queue WHERE rechazo_codigo IS NULL ORDER BY created_at ASC")
    suspend fun obtenerActivos(): List<SyncEntity>

    @Query("SELECT * FROM sync_queue WHERE rechazo_codigo IS NOT NULL ORDER BY created_at ASC")
    suspend fun obtenerRetenidos(): List<SyncEntity>

    @Query("UPDATE sync_queue SET rechazo_codigo = :codigo, rechazado_at = :ahora, updated_at = :ahora WHERE id = :id")
    suspend fun marcarRechazo(id: String, codigo: String, ahora: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET rechazo_codigo = NULL, rechazado_at = NULL, reintentos = 0, updated_at = :ahora WHERE rechazo_codigo IS NOT NULL")
    suspend fun limpiarRechazos(ahora: Long = System.currentTimeMillis())

    // Excluye los retenidos: esos tienen su propio contador.
    @Query("SELECT COUNT(*) FROM sync_queue WHERE rechazo_codigo IS NULL")
    fun observarPendientes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE rechazo_codigo IS NOT NULL")
    fun observarRetenidos(): Flow<Int>

    // Pendientes y retenidos: lo que no se puede borrar al cerrar o perder la sesión (FR-030).
    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun contarTrabajoSinSincronizar(): Int

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observarTrabajoSinSincronizar(): Flow<Int>
}
