package cl.stockflow.warehouse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cl.stockflow.warehouse.data.local.dao.*
import cl.stockflow.warehouse.data.local.entity.*

@Database(
    entities = [
        EmpresaEntity::class,
        UsuarioEntity::class,
        BodegaEntity::class,
        ProveedorEntity::class,
        ProductoEntity::class,
        MovimientoEntity::class,
        SyncEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun empresaDao(): EmpresaDao
    abstract fun usuarioDao(): UsuarioDao
    abstract fun bodegaDao(): BodegaDao
    abstract fun proveedorDao(): ProveedorDao
    abstract fun productoDao(): ProductoDao
    abstract fun movimientoDao(): MovimientoDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NOMBRE_DB = "stockflow.db"
    }
}
