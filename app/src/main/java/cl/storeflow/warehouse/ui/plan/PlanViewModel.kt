package cl.storeflow.warehouse.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.repository.PlanRepository
import cl.storeflow.warehouse.domain.model.PlanActual
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlanUiState {
    data object SinPlan : PlanUiState
    data class Activo(
        val tipo: String,
        val estado: String,
        val diasRestantes: Int?
    ) : PlanUiState
}

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val planRepository: PlanRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlanUiState>(PlanUiState.SinPlan)
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            planRepository.observarPlan().collect { _uiState.value = aUiState(it) }
        }
        viewModelScope.launch { planRepository.validarActual() }
    }

    private fun aUiState(plan: PlanActual?): PlanUiState =
        if (plan == null || plan.tipo == PlanRepository.SIN_PLAN) {
            PlanUiState.SinPlan
        } else {
            PlanUiState.Activo(plan.tipo, plan.estado, plan.diasRestantesTrial)
        }
}
