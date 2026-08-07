package cl.storeflow.warehouse.domain.model

import cl.storeflow.warehouse.data.local.entity.TipoMovimiento
import java.util.Date

data class MovimientoConProducto(
    val id: String,
    val producto_id: String,
    val tipo: TipoMovimiento,
    val cantidad: Int,
    val nota: String?,
    val created_at: Date,
    val producto_nombre: String,
    val producto_sku: String?
)
