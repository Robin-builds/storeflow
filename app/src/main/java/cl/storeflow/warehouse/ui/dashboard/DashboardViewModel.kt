package cl.storeflow.warehouse.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.repository.ProductoRepository
import cl.storeflow.warehouse.domain.model.Producto
import cl.storeflow.warehouse.domain.model.Rol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    authSessionDao: AuthSessionDao,
    productoRepository: ProductoRepository
) : ViewModel() {

    val nombreUsuario: StateFlow<String> = authSessionDao.observarSesion()
        .map { sesion ->
            sesion?.correo
                ?.substringBefore("@")
                ?.replaceFirstChar { it.uppercase() }
                ?: ""
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val rolActual: StateFlow<Rol> = authSessionDao.observarSesion()
        .map { Rol.fromString(it?.rol ?: "OPERADOR") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Rol.OPERADOR)

    private val bodegaIdFlow = authSessionDao.observarSesion()
        .map { it?.bodega_id ?: "" }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sinMovimientoReciente: StateFlow<List<Producto>> = bodegaIdFlow
        .flatMapLatest { bodegaId ->
            if (bodegaId.isBlank()) flowOf(emptyList())
            else productoRepository.observarSinMovimientoReciente(bodegaId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
