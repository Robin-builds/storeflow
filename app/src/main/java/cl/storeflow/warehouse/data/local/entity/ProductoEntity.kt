package cl.storeflow.warehouse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "productos",
    foreignKeys = [
        ForeignKey(
            entity = EmpresaEntity::class,
            parentColumns = ["id"],
            childColumns = ["empresa_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BodegaEntity::class,
            parentColumns = ["id"],
            childColumns = ["bodega_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("empresa_id"), Index("bodega_id")]
)
data class ProductoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val empresa_id: String,
    val bodega_id: String,
    val nombre: String,
    val descripcion: String? = null,
    val sku: String? = null,
    val precio: Int = 0,
    val stock_minimo: Int = 0,
    val es_perecedero: Boolean = false,
    // stock NO se almacena — siempre se calcula via MovimientoEntity
    val synced: Boolean = false,
    val synced_at: Date? = null,
    val created_at: Date = Date(),
    val updated_at: Date = Date()
)
