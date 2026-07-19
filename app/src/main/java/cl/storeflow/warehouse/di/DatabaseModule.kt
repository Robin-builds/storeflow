package cl.storeflow.warehouse.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import cl.storeflow.warehouse.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.NOMBRE_DB
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7
            )
            // Room es caché offline-first (integridad la garantiza Supabase) — un downgrade
            // de esquema (ej. dispositivo con una build de prueba más nueva) recrea la DB
            // local en vez de crashear; PullWorker repuebla los datos tras el login
            .fallbackToDestructiveMigrationOnDowngrade()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // Room es caché local — integridad referencial la garantiza Supabase
                    db.execSQL("PRAGMA foreign_keys = OFF")
                }
            })
            .build()

    @Provides fun provideEmpresaDao(db: AppDatabase) = db.empresaDao()
    @Provides fun provideUsuarioDao(db: AppDatabase) = db.usuarioDao()
    @Provides fun provideBodegaDao(db: AppDatabase) = db.bodegaDao()
    @Provides fun provideProveedorDao(db: AppDatabase) = db.proveedorDao()
    @Provides fun provideProductoDao(db: AppDatabase) = db.productoDao()
    @Provides fun provideMovimientoDao(db: AppDatabase) = db.movimientoDao()
    @Provides fun provideSyncDao(db: AppDatabase) = db.syncDao()
    @Provides fun provideAuthSessionDao(db: AppDatabase) = db.authSessionDao()
    @Provides fun provideAtributoTemplateDao(db: AppDatabase) = db.atributoTemplateDao()
    @Provides fun provideProductoAtributoDao(db: AppDatabase) = db.productoAtributoDao()
}
