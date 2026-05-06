package cl.storeflow.warehouse.data.local.dao

import androidx.room.*
import cl.storeflow.warehouse.data.local.entity.BodegaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodegaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(bodega: BodegaEntity)

    @Update
    suspend fun actualizar(bodega: BodegaEntity)

    @Delete
    suspend fun eliminar(bodega: BodegaEntity)

    @Query("SELECT * FROM bodegas WHERE id = :id")
    suspend fun obtenerPorId(id: String): BodegaEntity?

    @Query("SELECT * FROM bodegas WHERE empresa_id = :empresaId ORDER BY nombre ASC")
    fun observarPorEmpresa(empresaId: String): Flow<List<BodegaEntity>>

    @Query("SELECT * FROM bodegas WHERE empresa_id = :empresaId ORDER BY nombre ASC LIMIT 1")
    suspend fun obtenerPrimeraParaEmpresa(empresaId: String): BodegaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(bodegas: List<BodegaEntity>)

    @Query("SELECT * FROM bodegas WHERE synced = 0")
    suspend fun obtenerNoSincronizadas(): List<BodegaEntity>

    @Query("SELECT * FROM bodegas WHERE empresa_id = :empresaId AND id != :excludeId ORDER BY created_at ASC LIMIT 1")
    suspend fun obtenerMasAntiguaExcluyendo(empresaId: String, excludeId: String): BodegaEntity?

    @Query("UPDATE bodegas SET synced = 1, synced_at = :ahora WHERE id = :id")
    suspend fun marcarSincronizado(id: String, ahora: Long)
}
