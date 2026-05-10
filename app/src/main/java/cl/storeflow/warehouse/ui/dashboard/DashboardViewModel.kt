package cl.storeflow.warehouse.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.storeflow.warehouse.data.local.dao.AuthSessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    authSessionDao: AuthSessionDao
) : ViewModel() {

    val nombreUsuario: StateFlow<String> = authSessionDao.observarSesion()
        .map { sesion ->
            sesion?.correo
                ?.substringBefore("@")
                ?.replaceFirstChar { it.uppercase() }
                ?: ""
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
}
