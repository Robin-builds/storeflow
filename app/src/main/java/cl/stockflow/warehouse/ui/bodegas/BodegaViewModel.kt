package cl.stockflow.warehouse.ui.bodegas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.stockflow.warehouse.data.local.entity.BodegaEntity
import cl.stockflow.warehouse.data.repository.AuthRepository
import cl.stockflow.warehouse.data.repository.BodegaRepository
import cl.stockflow.warehouse.data.repository.UsuarioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BodegasUiState {
    object Cargando : BodegasUiState()
    data class Listo(
        val bodegas: List<BodegaEntity>,
        val activa: BodegaEntity?,
        val esAdmin: Boolean
    ) : BodegasUiState()
    data class Error(val mensaje: String) : BodegasUiState()
}

@HiltViewModel
class BodegaViewModel @Inject constructor(
    private val bodegaRepository: BodegaRepository,
    private val authRepository: AuthRepository,
    private val usuarioRepository: UsuarioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BodegasUiState>(BodegasUiState.Cargando)
    val uiState: StateFlow<BodegasUiState> = _uiState.asStateFlow()

    // UI colecta este evento y vacía el backstack → Dashboard
    private val _navegarADashboard = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navegarADashboard: SharedFlow<Unit> = _navegarADashboard.asSharedFlow()

    // Mensajes de error / aviso para Snackbar
    private val _mensaje = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val mensaje: SharedFlow<String> = _mensaje.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                bodegaRepository.observarBodegas(),
                authRepository.observarSesion(),
                usuarioRepository.observarUsuarioActual()
            ) { bodegas, sesion, usuario ->
                val activa = bodegas.find { it.id == sesion?.bodega_id }
                val esAdmin = usuario?.esAdmin() ?: false
                BodegasUiState.Listo(bodegas, activa, esAdmin)
            }.collect { _uiState.value = it }
        }
    }

    fun crear(nombre: String, ubicacion: String?) {
        val estado = _uiState.value as? BodegasUiState.Listo ?: return
        if (!estado.esAdmin) {
            viewModelScope.launch { _mensaje.emit("Solo el administrador puede crear bodegas") }
            return
        }
        if (nombre.isBlank()) {
            viewModelScope.launch { _mensaje.emit("El nombre de la bodega es obligatorio") }
            return
        }
        viewModelScope.launch {
            bodegaRepository.crear(nombre, ubicacion)
                .onFailure { _mensaje.emit("Error al crear bodega: ${it.message}") }
        }
    }

    fun eliminar(id: String) {
        val estado = _uiState.value as? BodegasUiState.Listo ?: return
        if (!estado.esAdmin) {
            viewModelScope.launch { _mensaje.emit("Solo el administrador puede eliminar bodegas") }
            return
        }
        if (estado.activa?.id == id) {
            viewModelScope.launch { _mensaje.emit("No se puede eliminar la bodega activa") }
            return
        }
        if (estado.bodegas.size <= 1) {
            viewModelScope.launch { _mensaje.emit("No se puede eliminar la única bodega de la empresa") }
            return
        }
        viewModelScope.launch {
            bodegaRepository.eliminar(id)
                .onSuccess { _mensaje.emit("Bodega eliminada") }
                .onFailure { _mensaje.emit("Error al eliminar bodega: ${it.message}") }
        }
    }

    fun cambiarBodegaActiva(bodegaId: String) {
        viewModelScope.launch {
            bodegaRepository.cambiarBodegaActiva(bodegaId)
                .onSuccess { _navegarADashboard.emit(Unit) }
                .onFailure { _mensaje.emit("Error al cambiar bodega: ${it.message}") }
        }
    }
}
