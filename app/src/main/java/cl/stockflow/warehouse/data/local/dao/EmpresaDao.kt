package cl.stockflow.warehouse.data.local.dao

import androidx.room.*
import cl.stockflow.warehouse.data.local.entity.EmpresaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmpresaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(empresa: EmpresaEntity)

    @Update
    suspend fun actualizar(empresa: EmpresaEntity)

    @Delete
    suspend fun eliminar(empresa: EmpresaEntity)

    @Query("SELECT * FROM empresas WHERE id = :id")
    suspend fun obtenerPorId(id: String): EmpresaEntity?

    @Query("SELECT * FROM empresas ORDER BY nombre ASC")
    fun observarTodas(): Flow<List<EmpresaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(empresas: List<EmpresaEntity>)

    @Query("SELECT * FROM empresas WHERE synced = 0")
    suspend fun obtenerNoSincronizadas(): List<EmpresaEntity>
}
