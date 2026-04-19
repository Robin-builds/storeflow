package cl.stockflow.warehouse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "empresas")
data class EmpresaEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nombre: String,
    val rut: String,
    val synced: Boolean = false,
    val synced_at: Date? = null,
    val created_at: Date = Date(),
    val updated_at: Date = Date()
)
