package cl.storeflow.warehouse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "atributo_templates",
    foreignKeys = [ForeignKey(
        entity = EmpresaEntity::class,
        parentColumns = ["id"],
        childColumns = ["empresa_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("empresa_id")]
)
data class AtributoTemplateEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val empresa_id: String,
    val clave: String,
    val etiqueta: String,
    val tipo: String,
    val obligatorio: Boolean,
    val orden: Int,
    val synced: Boolean = false,
    val synced_at: Date? = null,
    val created_at: Date = Date(),
    val updated_at: Date = Date()
)
