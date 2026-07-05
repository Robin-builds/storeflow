package cl.storeflow.warehouse.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.repository.TemaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemaViewModel @Inject constructor(
    private val temaRepository: TemaRepository
) : ViewModel() {

    val paletaSeleccionada: StateFlow<PaletaId> = temaRepository.paletaFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreferences.PALETA_DEFAULT)

    val oscuridadSeleccionada: StateFlow<OscuridadId> = temaRepository.oscuridadFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreferences.OSCURIDAD_DEFAULT)

    fun cambiarPaleta(paleta: PaletaId) {
        viewModelScope.launch { temaRepository.setPaleta(paleta) }
    }

    fun cambiarOscuridad(oscuridad: OscuridadId) {
        viewModelScope.launch { temaRepository.setOscuridad(oscuridad) }
    }
}
