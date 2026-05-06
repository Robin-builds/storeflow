package cl.storeflow.warehouse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cl.storeflow.warehouse.data.local.entity.ProductoAtributoEntity

data class ClaveValor(val clave: String, val valor: String)

@Dao
interface ProductoAtributoDao {

    @Query("""
        SELECT t.clave, a.valor
        FROM producto_atributos a
        JOIN atributo_templates t ON t.id = a.template_id
        WHERE a.producto_id = :productoId
    """)
    suspend fun obtenerClavesValores(productoId: String): List<ClaveValor>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(atributos: List<ProductoAtributoEntity>)

    @Query("DELETE FROM producto_atributos WHERE producto_id = :productoId")
    suspend fun eliminarPorProducto(productoId: String)
}
