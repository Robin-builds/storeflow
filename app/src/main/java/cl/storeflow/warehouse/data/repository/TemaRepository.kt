package cl.storeflow.warehouse.data.repository

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import cl.storeflow.warehouse.ui.theme.TemaApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemaRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val legacyPrefs: SharedPreferences
) {
    companion object {
        private val KEY_TEMA = stringPreferencesKey("tema_app")
        private const val LEGACY_KEY = "tema_app"
    }

    val temaFlow: Flow<TemaApp> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val nombre = prefs[KEY_TEMA] ?: TemaApp.CLARO.name
            runCatching { TemaApp.valueOf(nombre) }.getOrDefault(TemaApp.CLARO)
        }

    suspend fun setTema(tema: TemaApp) {
        dataStore.edit { it[KEY_TEMA] = tema.name }
    }

    // Migración única desde SharedPreferences → DataStore
    suspend fun migrarSiNecesario() {
        val prefs = dataStore.data.first()
        if (prefs[KEY_TEMA] != null) return
        val legacyValue = legacyPrefs.getString(LEGACY_KEY, null) ?: return
        val legacyTema = runCatching { TemaApp.valueOf(legacyValue) }.getOrNull() ?: return
        setTema(legacyTema)
        legacyPrefs.edit().remove(LEGACY_KEY).apply()
    }
}