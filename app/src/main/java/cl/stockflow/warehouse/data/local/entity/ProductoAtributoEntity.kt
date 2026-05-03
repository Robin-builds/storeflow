package cl.stockflow.warehouse.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "producto_atributos",
    primaryKeys = ["producto_id", "template_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProductoEntity::class,
            parentColumns = ["id"],
            childColumns = ["producto_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AtributoTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("producto_id"), Index("template_id")]
)
data class ProductoAtributoEntity(
    val producto_id: String,
    val template_id: String,
    val valor: String
)
