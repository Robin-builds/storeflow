package cl.stockflow.warehouse.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.stockflow.warehouse.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Cargando : AuthUiState()
    object Autenticado : AuthUiState()
    data class Error(val mensaje: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        verificarSesion()
    }

    private fun verificarSesion() {
        viewModelScope.launch {
            val sesion = authRepository.checkSession()
            if (sesion != null) {
                _uiState.value = AuthUiState.Autenticado
            }
        }
    }

    fun login(correo: String, contrasena: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Cargando
            authRepository.login(correo, contrasena)
                .onSuccess { _uiState.value = AuthUiState.Autenticado }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Error desconocido") }
        }
    }

    fun registrar(nombre_empresa: String, rubro: String, correo: String, contrasena: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Cargando
            authRepository.registrar(nombre_empresa, rubro, correo, contrasena)
                .onSuccess { _uiState.value = AuthUiState.Autenticado }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Error desconocido") }
        }
    }

    fun registrarUsuarioEnEmpresa(email: String, password: String, nombre: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Cargando
            val result = authRepository.registrarUsuarioEnEmpresa(email, password, nombre)
            _uiState.value = AuthUiState.Idle
            onResult(result)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState.Idle
        }
    }

    fun limpiarError() {
        _uiState.value = AuthUiState.Idle
    }
}
