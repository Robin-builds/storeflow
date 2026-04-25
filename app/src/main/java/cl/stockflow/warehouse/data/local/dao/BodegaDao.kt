package cl.stockflow.warehouse.data.local.dao

import androidx.room.*
import cl.stockflow.warehouse.data.local.entity.BodegaEntity
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

    @Query("SELECT * FROM bodegas WHERE synced = 0")
    suspend fun obtenerNoSincronizadas(): List<BodegaEntity>
}
