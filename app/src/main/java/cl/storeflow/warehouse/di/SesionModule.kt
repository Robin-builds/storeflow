package cl.storeflow.warehouse.di

import cl.storeflow.warehouse.data.sync.ClienteRefresh
import cl.storeflow.warehouse.data.sync.ClienteRefreshHttp
import cl.storeflow.warehouse.data.sync.ImportadorSesion
import cl.storeflow.warehouse.data.sync.ImportadorSesionSupabase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SesionModule {
    @Binds abstract fun bindClienteRefresh(impl: ClienteRefreshHttp): ClienteRefresh
    @Binds abstract fun bindImportadorSesion(impl: ImportadorSesionSupabase): ImportadorSesion
}
