package cl.storeflow.warehouse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

// INMUTABLE: nunca actualizar registros existentes, solo insertar nuevos
@Entity(
    tableName = "movimientos",
    foreignKeys = [
        ForeignKey(
            entity = ProductoEntity::class,
            parentColumns = ["id"],
            childColumns = ["producto_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["lote_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = UsuarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["usuario_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("producto_id"), Index("lote_id"), Index("usuario_id")]
)
data class MovimientoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val producto_id: String,
    val tipo: TipoMovimiento,
    val cantidad: Int,
    val nota: String? = null,
    // null = producto no perecedero, o entrada/ajuste sin lote asociado
    val lote_id: String? = null,
    // usuario que registró el movimiento — null si no hay sesión activa (no debería
    // pasar en uso normal, pero no bloquea la operación)
    val usuario_id: String? = null,
    val synced: Boolean = false,
    val synced_at: Date? = null,
    val created_at: Date = Date(),
    val updated_at: Date = Date()
)

enum class TipoMovimiento { ENTRADA, SALIDA, AJUSTE }
