package cl.stockflow.warehouse.data.local.dao

import androidx.room.*
import cl.stockflow.warehouse.data.local.entity.SyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun encolar(sync: SyncEntity)

    @Delete
    suspend fun eliminar(sync: SyncEntity)

    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    suspend fun obtenerCola(): List<SyncEntity>

    @Query("UPDATE sync_queue SET reintentos = reintentos + 1, updated_at = :ahora WHERE id = :id")
    suspend fun incrementarReintentos(id: String, ahora: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observarPendientes(): Flow<Int>
}
