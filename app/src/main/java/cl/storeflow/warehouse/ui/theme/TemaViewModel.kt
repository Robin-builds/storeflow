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

    val tema: StateFlow<TemaApp> = temaRepository.temaFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, TemaApp.CLARO)

    init {
        viewModelScope.launch { temaRepository.migrarSiNecesario() }
    }

    fun setTema(tema: TemaApp) {
        viewModelScope.launch { temaRepository.setTema(tema) }
    }
}