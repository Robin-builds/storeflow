package cl.storeflow.warehouse.data.local.dao

import androidx.room.*
import cl.storeflow.warehouse.data.local.entity.ProveedorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProveedorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(proveedor: ProveedorEntity)

    @Update
    suspend fun actualizar(proveedor: ProveedorEntity)

    @Delete
    suspend fun eliminar(proveedor: ProveedorEntity)

    @Query("SELECT * FROM proveedores WHERE id = :id")
    suspend fun obtenerPorId(id: String): ProveedorEntity?

    @Query("SELECT * FROM proveedores WHERE empresa_id = :empresaId ORDER BY nombre ASC")
    fun observarPorEmpresa(empresaId: String): Flow<List<ProveedorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(proveedores: List<ProveedorEntity>)

    @Query("SELECT * FROM proveedores WHERE synced = 0")
    suspend fun obtenerNoSincronizados(): List<ProveedorEntity>
}
