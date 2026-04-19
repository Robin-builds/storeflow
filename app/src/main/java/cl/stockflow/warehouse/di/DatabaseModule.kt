package cl.stockflow.warehouse.di

import android.content.Context
import androidx.room.Room
import cl.stockflow.warehouse.data.local.AppDatabase
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
        ).build()

    @Provides fun provideEmpresaDao(db: AppDatabase) = db.empresaDao()
    @Provides fun provideUsuarioDao(db: AppDatabase) = db.usuarioDao()
    @Provides fun provideBodegaDao(db: AppDatabase) = db.bodegaDao()
    @Provides fun provideProveedorDao(db: AppDatabase) = db.proveedorDao()
    @Provides fun provideProductoDao(db: AppDatabase) = db.productoDao()
    @Provides fun provideMovimientoDao(db: AppDatabase) = db.movimientoDao()
    @Provides fun provideSyncDao(db: AppDatabase) = db.syncDao()
}
