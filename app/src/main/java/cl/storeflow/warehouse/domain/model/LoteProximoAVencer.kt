package cl.storeflow.warehouse.domain.model

import java.util.Date

data class LoteProximoAVencer(
    val id: String,
    val producto_id: String,
    val producto_nombre: String,
    val numero_lote: String?,
    val fecha_caducidad: Date,
    val stock_actual: Int
)
