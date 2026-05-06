package cl.storeflow.warehouse.ui.atributos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.repository.AtributoRepository
import cl.storeflow.warehouse.data.repository.UsuarioRepository
import cl.storeflow.warehouse.domain.model.AtributoTemplate
import cl.storeflow.warehouse.domain.model.TipoAtributo
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

sealed class AtributosUiState {
    object Cargando : AtributosUiState()
    data class Listo(
        val templates: List<AtributoTemplate>,
        val esAdmin: Boolean
    ) : AtributosUiState()
    data class Error(val mensaje: String) : AtributosUiState()
}

@HiltViewModel
class AtributoViewModel @Inject constructor(
    private val atributoRepository: AtributoRepository,
    private val usuarioRepository: UsuarioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AtributosUiState>(AtributosUiState.Cargando)
    val uiState: StateFlow<AtributosUiState> = _uiState.asStateFlow()

    private val _mensaje = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val mensaje: SharedFlow<String> = _mensaje.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                atributoRepository.observarTemplates(),
                usuarioRepository.observarUsuarioActual()
            ) { templates, usuario ->
                AtributosUiState.Listo(templates, usuario?.esAdmin() ?: false)
            }.collect { _uiState.value = it }
        }
    }

    fun crear(clave: String, etiqueta: String, obligatorio: Boolean) {
        val estado = _uiState.value as? AtributosUiState.Listo ?: return
        if (!estado.esAdmin) {
            viewModelScope.launch { _mensaje.emit("Solo el administrador puede configurar atributos") }
            return
        }
        if (clave.isBlank() || etiqueta.isBlank()) {
            viewModelScope.launch { _mensaje.emit("La clave y la etiqueta son obligatorias") }
            return
        }
        val siguienteOrden = estado.templates.size
        viewModelScope.launch {
            atributoRepository.crear(clave, etiqueta, TipoAtributo.TEXT, obligatorio, siguienteOrden)
                .onFailure { _mensaje.emit("Error al crear atributo: ${it.message}") }
        }
    }

    fun eliminar(template: AtributoTemplate) {
        val estado = _uiState.value as? AtributosUiState.Listo ?: return
        if (!estado.esAdmin) {
            viewModelScope.launch { _mensaje.emit("Solo el administrador puede eliminar atributos") }
            return
        }
        viewModelScope.launch {
            atributoRepository.eliminar(template)
                .onSuccess { _mensaje.emit("Atributo eliminado") }
                .onFailure { _mensaje.emit("Error al eliminar: ${it.message}") }
        }
    }
}
