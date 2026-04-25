package cl.stockflow.warehouse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        SyncEntity::class,
        AuthSessionEntity::class
    ],
    version = 3,
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
    abstract fun authSessionDao(): AuthSessionDao

    companion object {
        const val NOMBRE_DB = "stockflow.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS auth_sessions (
                        id INTEGER NOT NULL PRIMARY KEY,
                        access_token TEXT NOT NULL,
                        refresh_token TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        empresa_id TEXT NOT NULL,
                        expires_at INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE auth_sessions ADD COLUMN bodega_id TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
