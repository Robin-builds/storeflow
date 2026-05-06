package cl.storeflow.warehouse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cl.storeflow.warehouse.data.local.dao.*
import cl.storeflow.warehouse.data.local.entity.*

@Database(
    entities = [
        EmpresaEntity::class,
        UsuarioEntity::class,
        BodegaEntity::class,
        ProveedorEntity::class,
        ProductoEntity::class,
        MovimientoEntity::class,
        SyncEntity::class,
        AuthSessionEntity::class,
        AtributoTemplateEntity::class,
        ProductoAtributoEntity::class
    ],
    version = 6,
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
    abstract fun atributoTemplateDao(): AtributoTemplateDao
    abstract fun productoAtributoDao(): ProductoAtributoDao

    companion object {
        const val NOMBRE_DB = "storeflow.db"

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE auth_sessions ADD COLUMN rol TEXT NOT NULL DEFAULT 'ADMIN'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS atributo_templates (
                        id TEXT NOT NULL PRIMARY KEY,
                        empresa_id TEXT NOT NULL,
                        clave TEXT NOT NULL,
                        etiqueta TEXT NOT NULL,
                        tipo TEXT NOT NULL,
                        obligatorio INTEGER NOT NULL,
                        orden INTEGER NOT NULL,
                        synced INTEGER NOT NULL,
                        synced_at INTEGER,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(empresa_id) REFERENCES empresas(id) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_atributo_templates_empresa_id ON atributo_templates (empresa_id)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS producto_atributos (
                        producto_id TEXT NOT NULL,
                        template_id TEXT NOT NULL,
                        valor TEXT NOT NULL,
                        PRIMARY KEY(producto_id, template_id),
                        FOREIGN KEY(producto_id) REFERENCES productos(id) ON DELETE CASCADE ON UPDATE NO ACTION,
                        FOREIGN KEY(template_id) REFERENCES atributo_templates(id) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_producto_atributos_producto_id ON producto_atributos (producto_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_producto_atributos_template_id ON producto_atributos (template_id)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite no soporta ALTER COLUMN — se recrea la tabla con precio INTEGER
                // Sin DEFAULT en columnas (Room espera defaultValue='undefined')
                // Con FK constraints igual al esquema original
                db.execSQL("""
                    CREATE TABLE productos_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        empresa_id TEXT NOT NULL,
                        bodega_id TEXT NOT NULL,
                        nombre TEXT NOT NULL,
                        descripcion TEXT,
                        sku TEXT,
                        precio INTEGER NOT NULL,
                        stock_minimo INTEGER NOT NULL,
                        synced INTEGER NOT NULL,
                        synced_at INTEGER,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(empresa_id) REFERENCES empresas(id) ON DELETE CASCADE ON UPDATE NO ACTION,
                        FOREIGN KEY(bodega_id) REFERENCES bodegas(id) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO productos_new
                    SELECT id, empresa_id, bodega_id, nombre, descripcion, sku,
                           CAST(precio AS INTEGER), stock_minimo, synced, synced_at, created_at, updated_at
                    FROM productos
                """.trimIndent())
                db.execSQL("DROP TABLE productos")
                db.execSQL("ALTER TABLE productos_new RENAME TO productos")
                db.execSQL("CREATE INDEX index_productos_empresa_id ON productos (empresa_id)")
                db.execSQL("CREATE INDEX index_productos_bodega_id ON productos (bodega_id)")
            }
        }
    }
}
