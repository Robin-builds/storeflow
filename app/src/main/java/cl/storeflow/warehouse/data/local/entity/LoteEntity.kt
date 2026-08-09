package cl.storeflow.warehouse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "lotes",
    foreignKeys = [
        ForeignKey(
            entity = ProductoEntity::class,
            parentColumns = ["id"],
            childColumns = ["producto_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EmpresaEntity::class,
            parentColumns = ["id"],
            childColumns = ["empresa_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("producto_id"), Index("empresa_id")]
)
data class LoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val producto_id: String,
    val empresa_id: String,
    val numero_lote: String? = null,
    val fecha_caducidad: Date,
    val synced: Boolean = false,
    val synced_at: Date? = null,
    val created_at: Date = Date(),
    val updated_at: Date = Date()
)
