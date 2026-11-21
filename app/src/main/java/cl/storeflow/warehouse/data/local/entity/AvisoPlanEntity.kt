package cl.storeflow.warehouse.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "avisos_plan",
    indices = [Index(value = ["empresa_id", "momento"], unique = true)]
)
data class AvisoPlanEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val empresa_id: String,
    val momento: String,
    val cerrado: Boolean = false,
    val cierre_pendiente: Boolean = false
)
