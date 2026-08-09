package cl.storeflow.warehouse.ui.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import cl.storeflow.warehouse.data.repository.MovimientoRepository
import cl.storeflow.warehouse.domain.model.MovimientoConProducto
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
class HistorialMovimientosViewModel @Inject constructor(
    authSessionDao: AuthSessionDao,
    movimientoRepository: MovimientoRepository
) : ViewModel() {

    private val empresaIdFlow = authSessionDao.observarSesion()
        .map { it?.empresa_id ?: "" }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todosLosMovimientos: StateFlow<List<MovimientoConProducto>> = empresaIdFlow
        .flatMapLatest { empresaId ->
            if (empresaId.isBlank()) flowOf(emptyList())
            else movimientoRepository.observarMovimientosDeEmpresa(empresaId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda.asStateFlow()

    val movimientosFiltrados: StateFlow<List<MovimientoConProducto>> = combine(
        todosLosMovimientos, _busqueda
    ) { lista, query ->
        if (query.isBlank()) lista
        else lista.filter {
            it.producto_nombre.contains(query, ignoreCase = true) ||
            it.producto_sku?.contains(query, ignoreCase = true) == true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _tamanioPagina = MutableStateFlow(25)
    val tamanioPagina: StateFlow<Int> = _tamanioPagina.asStateFlow()

    private val _cantidadVisible = MutableStateFlow(_tamanioPagina.value)

    val movimientosVisibles: StateFlow<List<MovimientoConProducto>> = combine(
        movimientosFiltrados, _cantidadVisible
    ) { lista, cantidad -> lista.take(cantidad) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hayMas: StateFlow<Boolean> = combine(
        movimientosFiltrados, _cantidadVisible
    ) { lista, cantidad -> cantidad < lista.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setBusqueda(query: String) {
        _busqueda.value = query
        _cantidadVisible.value = _tamanioPagina.value
    }

    fun setTamanioPagina(tamanio: Int) {
        _tamanioPagina.value = tamanio
        _cantidadVisible.value = tamanio
    }

    fun cargarMas() {
        _cantidadVisible.value += _tamanioPagina.value
    }
}