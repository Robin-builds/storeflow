package cl.stockflow.warehouse.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cl.stockflow.warehouse.data.local.entity.AtributoTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AtributoTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(template: AtributoTemplateEntity)

    @Delete
    suspend fun eliminar(template: AtributoTemplateEntity)

    @Query("SELECT * FROM atributo_templates WHERE empresa_id = :empresaId ORDER BY orden ASC")
    fun observarPorEmpresa(empresaId: String): Flow<List<AtributoTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(templates: List<AtributoTemplateEntity>)
}
