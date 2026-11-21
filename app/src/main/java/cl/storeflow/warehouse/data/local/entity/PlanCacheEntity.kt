package cl.storeflow.warehouse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Último plan validado por el servidor. Sin columnas de valores de límites (FR-011). */
@Entity(tableName = "plan_cache")
data class PlanCacheEntity(
    @PrimaryKey val empresa_id: String,
    val plan_efectivo: String,
    val estado: String,
    val bloqueado: Boolean,
    val puede_crear_bodega: Boolean,
    val puede_crear_usuario: Boolean,
    val historial_desde: Long?,
    val lotes: Boolean,
    val degradacion_free: String?,
    val bodegas_solo_lectura: String,
    val dispositivo_id: String,
    val trial_fin: Long?,
    val vencimiento: Long?,
    val validado_en_servidor: Long,
    val reloj_en_validacion: Long,
    val mayor_reloj_observado: Long,
    val elapsed_en_validacion: Long,
    val boot_count: Int
)
