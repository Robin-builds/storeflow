package cl.storeflow.warehouse.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.repository.ProductoRepository
import cl.storeflow.warehouse.domain.model.Producto
import cl.storeflow.warehouse.domain.model.ProductoConStockYBodega
import cl.storeflow.warehouse.domain.model.Rol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val empresaIdFlow = authSessionDao.observarSesion()
        .map { it?.empresa_id ?: "" }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val productosDeEmpresa: StateFlow<List<ProductoConStockYBodega>> = empresaIdFlow
        .flatMapLatest { empresaId ->
            if (empresaId.isBlank()) flowOf(emptyList())
            else productoRepository.observarProductosDeEmpresa(empresaId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _busquedaGlobal = MutableStateFlow("")
    val busquedaGlobal: StateFlow<String> = _busquedaGlobal.asStateFlow()

    val resultadosBusquedaGlobal: StateFlow<List<ProductoConStockYBodega>> = combine(
        productosDeEmpresa, _busquedaGlobal
    ) { lista, query ->
        if (query.isBlank()) emptyList()
        else lista.filter {
            it.nombre.contains(query, ignoreCase = true) ||
            it.sku?.contains(query, ignoreCase = true) == true
        }.take(20)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun buscarProductoGlobal(query: String) {
        _busquedaGlobal.value = query
    }
}
