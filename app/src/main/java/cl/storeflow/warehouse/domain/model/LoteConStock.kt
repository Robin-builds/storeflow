package cl.storeflow.warehouse.domain.model

import java.util.Date

data class LoteConStock(
    val id: String,
    val producto_id: String,
    val empresa_id: String,
    val numero_lote: String?,
    val fecha_caducidad: Date,
    val stock_actual: Int,
    val synced: Boolean,
    val synced_at: Date?,
    val created_at: Date,
    val updated_at: Date
)
