package cl.storeflow.warehouse.di

import cl.storeflow.warehouse.data.remote.ClientePlan
import cl.storeflow.warehouse.data.remote.ClientePlanHttp
import cl.storeflow.warehouse.data.repository.RelojPlanAndroid
import cl.storeflow.warehouse.domain.model.RelojPlan
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlanModule {
    @Binds abstract fun bindClientePlan(impl: ClientePlanHttp): ClientePlan
    @Binds abstract fun bindRelojPlan(impl: RelojPlanAndroid): RelojPlan
}
