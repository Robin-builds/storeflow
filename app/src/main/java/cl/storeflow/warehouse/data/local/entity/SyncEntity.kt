package cl.storeflow.warehouse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "sync_queue")
data class SyncEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entidad_tipo: String,
    val entidad_id: String,
    val operacion: OperacionSync,
    val payload: String,
    val reintentos: Int = 0,
    val created_at: Date = Date(),
    val updated_at: Date = Date()
)

enum class OperacionSync { INSERT, UPDATE, DELETE }
