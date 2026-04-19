package cl.stockflow.warehouse.data.local

import androidx.room.TypeConverter
import cl.stockflow.warehouse.data.local.entity.OperacionSync
import cl.stockflow.warehouse.data.local.entity.TipoMovimiento
import java.util.Date

class DateConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun toTipoMovimiento(value: String?): TipoMovimiento? =
        value?.let { TipoMovimiento.valueOf(it) }

    @TypeConverter
    fun fromTipoMovimiento(tipo: TipoMovimiento?): String? = tipo?.name

    @TypeConverter
    fun toOperacionSync(value: String?): OperacionSync? =
        value?.let { OperacionSync.valueOf(it) }

    @TypeConverter
    fun fromOperacionSync(operacion: OperacionSync?): String? = operacion?.name
}
