package cl.storeflow.warehouse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cl.storeflow.warehouse.data.local.entity.PlanCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(plan: PlanCacheEntity)

    @Query("SELECT * FROM plan_cache WHERE empresa_id = :empresaId")
    suspend fun obtener(empresaId: String): PlanCacheEntity?

    @Query("SELECT * FROM plan_cache WHERE empresa_id = :empresaId")
    fun observar(empresaId: String): Flow<PlanCacheEntity?>

    @Query(
        "UPDATE plan_cache SET mayor_reloj_observado = :valor " +
            "WHERE empresa_id = :empresaId AND mayor_reloj_observado < :valor"
    )
    suspend fun actualizarMayorReloj(empresaId: String, valor: Long)
}
