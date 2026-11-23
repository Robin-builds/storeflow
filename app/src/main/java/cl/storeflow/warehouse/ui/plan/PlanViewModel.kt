package cl.storeflow.warehouse.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.local.dao.RetenidoPorTipo
import cl.storeflow.warehouse.data.repository.PlanRepository
import cl.storeflow.warehouse.data.repository.UsuarioRepository
import cl.storeflow.warehouse.domain.model.PlanActual
import cl.storeflow.warehouse.domain.model.RetenidoUsuario
import cl.storeflow.warehouse.domain.model.Usuario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Cantidad retenida de un usuario, sin el detalle de su trabajo (FR-023). */
data class RetenidoVisible(val nombre: String, val cantidad: Int, val actualizadoAt: String)

sealed interface PlanUiState {
    data object SinPlan : PlanUiState
    data class Activo(
        val tipo: String,
        val estado: String,
        val diasRestantes: Int?,
        val retenidosPropios: List<RetenidoPorTipo> = emptyList(),
        val retenidosPorUsuario: List<RetenidoVisible> = emptyList()
    ) : PlanUiState
}

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val usuarioRepository: UsuarioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlanUiState>(PlanUiState.SinPlan)
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    private val _avisoActual = MutableStateFlow<String?>(null)
    val avisoActual: StateFlow<String?> = _avisoActual.asStateFlow()

    fun cerrarAviso() {
        val momento = _avisoActual.value ?: return
        viewModelScope.launch { planRepository.cerrarAviso(momento) }
    }

    init {
        viewModelScope.launch {
            planRepository.observarAvisoActual().collect { _avisoActual.value = it }
        }
        viewModelScope.launch {
            combine(
                planRepository.observarPlan(),
                planRepository.observarRetenidosPropios(),
                planRepository.retenidosEmpresa,
                usuarioRepository.observarUsuariosDeEmpresa()
            ) { plan, propios, empresa, usuarios ->
                aUiState(plan, propios, empresa, usuarios)
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch { planRepository.validarActual() }
    }

    private fun aUiState(
        plan: PlanActual?,
        propios: List<RetenidoPorTipo>,
        empresa: List<RetenidoUsuario>,
        usuarios: List<Usuario>
    ): PlanUiState =
        if (plan == null || plan.tipo == PlanRepository.SIN_PLAN) {
            PlanUiState.SinPlan
        } else {
            val nombres = usuarios.associate { it.id to it.nombre }
            PlanUiState.Activo(
                tipo = plan.tipo,
                estado = plan.estado,
                diasRestantes = plan.diasRestantesTrial,
                retenidosPropios = propios,
                retenidosPorUsuario = empresa.map {
                    RetenidoVisible(nombres[it.usuarioId] ?: "Usuario", it.cantidad, it.actualizadoAt)
                }
            )
        }
}
