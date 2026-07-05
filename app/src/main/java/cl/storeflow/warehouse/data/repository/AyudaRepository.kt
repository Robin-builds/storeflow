package cl.storeflow.warehouse.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AyudaRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_MOSTRAR_TOOLTIPS = booleanPreferencesKey("mostrar_ayuda")
        private val KEY_ONBOARDING_VISTO = booleanPreferencesKey("onboarding_visto")
        private const val MOSTRAR_TOOLTIPS_DEFAULT = true
    }

    val mostrarTooltipsFlow: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_MOSTRAR_TOOLTIPS] ?: MOSTRAR_TOOLTIPS_DEFAULT }

    val onboardingVistoFlow: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY_ONBOARDING_VISTO] ?: false }

    suspend fun setMostrarTooltips(habilitado: Boolean) {
        dataStore.edit { it[KEY_MOSTRAR_TOOLTIPS] = habilitado }
    }

    suspend fun marcarOnboardingVisto() {
        dataStore.edit { it[KEY_ONBOARDING_VISTO] = true }
    }
}
