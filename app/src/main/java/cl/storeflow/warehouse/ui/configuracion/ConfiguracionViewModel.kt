package cl.storeflow.warehouse.ui.configuracion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConfiguracionUiState {
    object Idle : ConfiguracionUiState()
    object Operando : ConfiguracionUiState()
}

@HiltViewModel
class ConfiguracionViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConfiguracionUiState>(ConfiguracionUiState.Idle)
    val uiState: StateFlow<ConfiguracionUiState> = _uiState.asStateFlow()

    private val _mensaje = MutableSharedFlow<String>()
    val mensaje: SharedFlow<String> = _mensaje.asSharedFlow()

    // Parte en true: no se ofrece cerrar sesión hasta confirmar que no hay trabajo (FR-030).
    val hayTrabajoSinSincronizar: StateFlow<Boolean> = authRepository.observarTrabajoSinSincronizar()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun cambiarPassword(actual: String, nueva: String) {
        viewModelScope.launch {
            _uiState.value = ConfiguracionUiState.Operando
            authRepository.cambiarPassword(actual, nueva)
                .onSuccess { _mensaje.emit("Contraseña actualizada") }
                .onFailure { _mensaje.emit(it.message ?: "Error al cambiar contraseña") }
            _uiState.value = ConfiguracionUiState.Idle
        }
    }
}
