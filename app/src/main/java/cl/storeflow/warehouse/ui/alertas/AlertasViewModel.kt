package cl.storeflow.warehouse.ui.alertas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.repository.BodegaRepository
import cl.storeflow.warehouse.data.repository.ProductoRepository
import cl.storeflow.warehouse.domain.model.Producto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AlertasUiState {
    object Cargando : AlertasUiState()
    data class Listo(val alertas: List<Producto>) : AlertasUiState()
}

@HiltViewModel
class AlertasViewModel @Inject constructor(
    private val repository: ProductoRepository,
    private val bodegaRepository: BodegaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AlertasUiState>(AlertasUiState.Cargando)
    val uiState: StateFlow<AlertasUiState> = _uiState.asStateFlow()

    private val _bodegaNombre = MutableStateFlow("")
    val bodegaNombre: StateFlow<String> = _bodegaNombre.asStateFlow()

    init {
        viewModelScope.launch {
            _bodegaNombre.value = bodegaRepository.obtenerBodegaActiva()?.nombre ?: ""
            val ctx = repository.obtenerContexto()
            if (ctx == null) {
                _uiState.value = AlertasUiState.Listo(emptyList())
                return@launch
            }
            repository.observarBajoMinimo(ctx.second).collect { lista ->
                _uiState.value = AlertasUiState.Listo(lista)
            }
        }
    }
}
