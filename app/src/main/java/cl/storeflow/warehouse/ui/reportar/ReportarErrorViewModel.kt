package cl.storeflow.warehouse.ui.reportar

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ReportarErrorViewModel @Inject constructor() : ViewModel() {

    private val _imagenes = MutableStateFlow<List<Uri>>(emptyList())
    val imagenes: StateFlow<List<Uri>> = _imagenes.asStateFlow()

    private val _descripcion = MutableStateFlow("")
    val descripcion: StateFlow<String> = _descripcion.asStateFlow()

    fun agregarImagenes(uris: List<Uri>) {
        _imagenes.value = (_imagenes.value + uris).distinct()
    }

    fun eliminarImagen(uri: Uri) {
        _imagenes.value = _imagenes.value.filter { it != uri }
    }

    fun setDescripcion(texto: String) {
        _descripcion.value = texto
    }
}
