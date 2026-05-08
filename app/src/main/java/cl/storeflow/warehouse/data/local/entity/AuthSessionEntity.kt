package cl.storeflow.warehouse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "auth_sessions")
data class AuthSessionEntity(
    @PrimaryKey val id: Int = 1,
    val access_token: String,
    val refresh_token: String,
    val user_id: String,
    val empresa_id: String,
    val bodega_id: String = "",
    val rol: String = "ADMIN",
    val expires_at: Date,
    val created_at: Date = Date(),
    val updated_at: Date = Date()
)
