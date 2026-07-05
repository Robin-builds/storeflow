package cl.storeflow.warehouse.ui.theme

import androidx.datastore.preferences.core.stringPreferencesKey

enum class PaletaId(val paleta: PaletaAcento) {
    FORJA(PaletaForja),
    PLANTA(PaletaPlanta),
    BUNKER(PaletaBunker);
}

enum class OscuridadId(val oscuridad: NivelOscuridad) {
    PENUMBRA(Penumbra),
    NOCTURNO(Nocturno),
    ABISMO(Abismo);
}

object ThemePreferences {
    val PALETA_KEY = stringPreferencesKey("tema_paleta")
    val OSCURIDAD_KEY = stringPreferencesKey("tema_oscuridad")

    val PALETA_DEFAULT = PaletaId.BUNKER
    val OSCURIDAD_DEFAULT = OscuridadId.NOCTURNO
}
