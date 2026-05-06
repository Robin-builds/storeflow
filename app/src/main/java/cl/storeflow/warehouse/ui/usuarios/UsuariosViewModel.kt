package cl.storeflow.warehouse.ui.usuarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.repository.AuthRepository
import cl.storeflow.warehouse.data.repository.UsuarioRepository
import cl.storeflow.warehouse.domain.model.Rol
import cl.storeflow.warehouse.domain.model.Usuario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UsuariosUiState {
    object Cargando : UsuariosUiState()
    data class Listo(
        val usuarios: List<Usuario>,
        val usuarioActualId: String
    ) : UsuariosUiState()
    data class Error(val mensaje: String) : UsuariosUiState()
}

@HiltViewModel
class UsuariosViewModel @Inject constructor(
    private val usuarioRepository: UsuarioRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UsuariosUiState>(UsuariosUiState.Cargando)
    val uiState: StateFlow<UsuariosUiState> = _uiState.asStateFlow()

    private val _mensaje = MutableSharedFlow<String>()
    val mensaje: SharedFlow<String> = _mensaje.asSharedFlow()

    private val _operando = MutableStateFlow(false)
    val operando: StateFlow<Boolean> = _operando.asStateFlow()

    init {
        viewModelScope.launch {
            val idActual = usuarioRepository.obtenerUsuarioActual()?.id ?: ""
            usuarioRepository.observarUsuariosDeEmpresa()
                .collect { lista ->
                    _uiState.value = UsuariosUiState.Listo(
                        usuarios = lista,
                        usuarioActualId = idActual
                    )
                }
        }
    }

    fun registrar(email: String, password: String, nombre: String) {
        viewModelScope.launch {
            _operando.value = true
            authRepository.registrarUsuarioEnEmpresa(email, password, nombre)
                .onSuccess { userId ->
                    usuarioRepository.insertarLocal(userId, email, nombre, Rol.OPERADOR)
                    _mensaje.emit("Usuario registrado exitosamente")
                }
                .onFailure { _mensaje.emit(it.message ?: "Error al registrar usuario") }
            _operando.value = false
        }
    }

    fun eliminar(usuario: Usuario) {
        viewModelScope.launch {
            _operando.value = true
            usuarioRepository.eliminar(usuario)
                .onSuccess { _mensaje.emit("Usuario eliminado") }
                .onFailure { _mensaje.emit(it.message ?: "Error al eliminar") }
            _operando.value = false
        }
    }

    fun cambiarRol(usuario: Usuario, nuevoRol: Rol) {
        viewModelScope.launch {
            _operando.value = true
            usuarioRepository.cambiarRol(usuario, nuevoRol)
                .onSuccess { _mensaje.emit("Rol actualizado") }
                .onFailure { _mensaje.emit(it.message ?: "Error al cambiar rol") }
            _operando.value = false
        }
    }
}
