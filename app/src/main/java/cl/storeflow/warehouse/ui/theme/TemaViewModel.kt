package cl.storeflow.warehouse.ui.theme

import androidx.lifecycle.ViewModel
import cl.storeflow.warehouse.data.repository.TemaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class TemaViewModel @Inject constructor(
    private val temaRepository: TemaRepository
) : ViewModel() {

    private val _tema = MutableStateFlow(temaRepository.getTema())
    val tema: StateFlow<TemaApp> = _tema.asStateFlow()

    fun avanzar() {
        val siguiente = when (_tema.value) {
            TemaApp.CLARO       -> TemaApp.OSCURO
            TemaApp.OSCURO      -> TemaApp.OSCURO_PLUS
            TemaApp.OSCURO_PLUS -> TemaApp.CLARO
        }
        _tema.value = siguiente
        temaRepository.setTema(siguiente)
    }
}
