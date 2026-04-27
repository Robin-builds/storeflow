package cl.stockflow.warehouse.data.local.dao

import androidx.room.*
import cl.stockflow.warehouse.data.local.entity.UsuarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(usuario: UsuarioEntity)

    @Update
    suspend fun actualizar(usuario: UsuarioEntity)

    @Delete
    suspend fun eliminar(usuario: UsuarioEntity)

    @Query("SELECT * FROM usuarios WHERE id = :id")
    suspend fun obtenerPorId(id: String): UsuarioEntity?

    @Query("SELECT * FROM usuarios WHERE empresa_id = :empresaId ORDER BY nombre ASC")
    fun observarPorEmpresa(empresaId: String): Flow<List<UsuarioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(usuarios: List<UsuarioEntity>)

    @Query("SELECT * FROM usuarios WHERE synced = 0")
    suspend fun obtenerNoSincronizados(): List<UsuarioEntity>
}
