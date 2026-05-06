package cl.storeflow.warehouse.ui.movimientos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.local.entity.MovimientoEntity
import cl.storeflow.warehouse.data.repository.MovimientoRepository
import cl.storeflow.warehouse.domain.model.Producto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MovimientosUiState {
    object Cargando : MovimientosUiState()
    data class Listo(val producto: Producto, val movimientos: List<MovimientoEntity>) : MovimientosUiState()
    data class Error(val mensaje: String) : MovimientosUiState()
}

sealed class MovFormState {
    object Idle : MovFormState()
    object Cargando : MovFormState()
    data class Guardado(val mensaje: String) : MovFormState()
    data class Error(val mensaje: String) : MovFormState()
}

@HiltViewModel
class MovimientoViewModel @Inject constructor(
    private val repository: MovimientoRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val productoId: String = checkNotNull(savedStateHandle["productoId"])

    private val _uiState = MutableStateFlow<MovimientosUiState>(MovimientosUiState.Cargando)
    val uiState: StateFlow<MovimientosUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow<MovFormState>(MovFormState.Idle)
    val formState: StateFlow<MovFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observarProducto(productoId),
                repository.observarMovimientos(productoId)
            ) { producto, movimientos ->
                if (producto == null) MovimientosUiState.Error("Producto no encontrado")
                else MovimientosUiState.Listo(producto, movimientos)
            }.collect { _uiState.value = it }
        }
    }

    fun registrarEntrada(cantidad: Int, nota: String?) {
        viewModelScope.launch {
            _formState.value = MovFormState.Cargando
            repository.registrarEntrada(productoId, cantidad, nota)
                .onSuccess { _formState.value = MovFormState.Guardado("Entrada registrada") }
                .onFailure { _formState.value = MovFormState.Error(it.message ?: "Error") }
        }
    }

    fun registrarSalida(cantidad: Int, nota: String?) {
        viewModelScope.launch {
            _formState.value = MovFormState.Cargando
            repository.registrarSalida(productoId, cantidad, nota)
                .onSuccess { _formState.value = MovFormState.Guardado("Salida registrada") }
                .onFailure { _formState.value = MovFormState.Error(it.message ?: "Error") }
        }
    }

    fun registrarAjuste(stockObjetivo: Int, nota: String?) {
        viewModelScope.launch {
            _formState.value = MovFormState.Cargando
            repository.registrarAjuste(productoId, stockObjetivo, nota)
                .onSuccess { _formState.value = MovFormState.Guardado("Stock ajustado") }
                .onFailure { _formState.value = MovFormState.Error(it.message ?: "Error") }
        }
    }

    fun limpiarFormState() { _formState.value = MovFormState.Idle }
}
