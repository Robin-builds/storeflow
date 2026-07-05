package cl.storeflow.warehouse.ui.ayuda

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.repository.AyudaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

val LocalMostrarAyuda = compositionLocalOf { true }

@HiltViewModel
class AyudaViewModel @Inject constructor(
    private val ayudaRepository: AyudaRepository
) : ViewModel() {

    val mostrarTooltips: StateFlow<Boolean> = ayudaRepository.mostrarTooltipsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val onboardingVisto: StateFlow<Boolean> = ayudaRepository.onboardingVistoFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleTooltips(habilitado: Boolean) {
        viewModelScope.launch { ayudaRepository.setMostrarTooltips(habilitado) }
    }

    fun marcarOnboardingVisto() {
        viewModelScope.launch { ayudaRepository.marcarOnboardingVisto() }
    }
}
