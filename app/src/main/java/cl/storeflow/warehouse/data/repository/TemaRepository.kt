package cl.storeflow.warehouse.data.repository

import android.content.SharedPreferences
import cl.storeflow.warehouse.ui.theme.TemaApp
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemaRepository @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_TEMA = "tema_app"
    }

    fun getTema(): TemaApp {
        val nombre = prefs.getString(KEY_TEMA, TemaApp.CLARO.name) ?: TemaApp.CLARO.name
        return runCatching { TemaApp.valueOf(nombre) }.getOrDefault(TemaApp.CLARO)
    }

    fun setTema(tema: TemaApp) {
        prefs.edit().putString(KEY_TEMA, tema.name).apply()
    }
}
