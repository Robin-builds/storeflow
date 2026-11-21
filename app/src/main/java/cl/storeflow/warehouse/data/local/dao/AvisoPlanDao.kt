package cl.storeflow.warehouse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cl.storeflow.warehouse.data.local.entity.AvisoPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AvisoPlanDao {
    // IGNORE: un aviso ya guardado conserva su estado de cierre local.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarSiNoExiste(aviso: AvisoPlanEntity)

    @Query("SELECT * FROM avisos_plan WHERE empresa_id = :empresaId")
    fun observar(empresaId: String): Flow<List<AvisoPlanEntity>>

    @Query("SELECT * FROM avisos_plan WHERE empresa_id = :empresaId AND cierre_pendiente = 1")
    suspend fun obtenerCierresPendientes(empresaId: String): List<AvisoPlanEntity>

    @Query(
        "UPDATE avisos_plan SET cerrado = 1, cierre_pendiente = 1 " +
            "WHERE empresa_id = :empresaId AND momento IN (:momentos) AND cerrado = 0"
    )
    suspend fun cerrarLocal(empresaId: String, momentos: List<String>)

    @Query(
        "UPDATE avisos_plan SET cierre_pendiente = 0 " +
            "WHERE empresa_id = :empresaId AND momento = :momento"
    )
    suspend fun confirmarCierre(empresaId: String, momento: String)
}
