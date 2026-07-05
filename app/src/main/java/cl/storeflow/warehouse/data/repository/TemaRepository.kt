package cl.storeflow.warehouse.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import cl.storeflow.warehouse.ui.theme.OscuridadId
import cl.storeflow.warehouse.ui.theme.PaletaId
import cl.storeflow.warehouse.ui.theme.ThemePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemaRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val paletaFlow: Flow<PaletaId> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val nombre = prefs[ThemePreferences.PALETA_KEY]
            PaletaId.entries.find { it.name == nombre } ?: ThemePreferences.PALETA_DEFAULT
        }

    val oscuridadFlow: Flow<OscuridadId> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val nombre = prefs[ThemePreferences.OSCURIDAD_KEY]
            OscuridadId.entries.find { it.name == nombre } ?: ThemePreferences.OSCURIDAD_DEFAULT
        }

    suspend fun setPaleta(paleta: PaletaId) {
        dataStore.edit { it[ThemePreferences.PALETA_KEY] = paleta.name }
    }

    suspend fun setOscuridad(oscuridad: OscuridadId) {
        dataStore.edit { it[ThemePreferences.OSCURIDAD_KEY] = oscuridad.name }
    }
}
