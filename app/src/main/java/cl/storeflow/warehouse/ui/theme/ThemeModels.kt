package cl.storeflow.warehouse.ui.theme

import androidx.compose.ui.graphics.Color

data class PaletaAcento(
    val nombre: String,

    val primario: Color,
    val primarioClaro: Color,
    val primarioSuave: Color,

    val neutro: Color,
    val neutroClaro: Color,
    val neutroOscuro: Color,

    val alerta: Color,
    val alertaClaro: Color,
    val alertaSuave: Color,
)

val PaletaForja = PaletaAcento(
    nombre = "Forja",
    primario       = Color(0xFFD97706),
    primarioClaro  = Color(0xFFF59E0B),
    primarioSuave  = Color(0xFFFCD34D),
    neutro         = Color(0xFF64748B),
    neutroClaro    = Color(0xFF94A3B8),
    neutroOscuro   = Color(0xFF334155),
    alerta         = Color(0xFFDC2626),
    alertaClaro    = Color(0xFFEF4444),
    alertaSuave    = Color(0xFFFCA5A5),
)

val PaletaPlanta = PaletaAcento(
    nombre = "Planta",
    primario       = Color(0xFF059669),
    primarioClaro  = Color(0xFF34D399),
    primarioSuave  = Color(0xFF6EE7B7),
    neutro         = Color(0xFF78716C),
    neutroClaro    = Color(0xFFA8A29E),
    neutroOscuro   = Color(0xFF44403C),
    alerta         = Color(0xFFDC2626),
    alertaClaro    = Color(0xFFEF4444),
    alertaSuave    = Color(0xFFFCA5A5),
)

val PaletaBunker = PaletaAcento(
    nombre = "Búnker",
    primario       = Color(0xFF0891B2),
    primarioClaro  = Color(0xFF22D3EE),
    primarioSuave  = Color(0xFF67E8F9),
    neutro         = Color(0xFF6B7280),
    neutroClaro    = Color(0xFF9CA3AF),
    neutroOscuro   = Color(0xFF374151),
    alerta         = Color(0xFFDC2626),
    alertaClaro    = Color(0xFFEF4444),
    alertaSuave    = Color(0xFFFCA5A5),
)

data class NivelOscuridad(
    val nombre: String,

    val fondoTop: Color,
    val fondoMid: Color,
    val fondoBottom: Color,

    val superficie: Color,
    val superficieVariante: Color,
    val cardAlpha: Float,
    val borderAlpha: Float,

    val textoPrimario: Color,
    val textoSecundario: Color,
    val textoTerciario: Color,
    val textoDesactivado: Color,
)

val Penumbra = NivelOscuridad(
    nombre = "Penumbra",
    fondoTop     = Color(0xFF1C1F26),
    fondoMid     = Color(0xFF20242C),
    fondoBottom  = Color(0xFF181B21),
    superficie         = Color(0xFF252930),
    superficieVariante = Color(0xFF2A2F37),
    cardAlpha    = 0.08f,
    borderAlpha  = 0.10f,
    textoPrimario    = Color(0xFFF5F5F5),
    textoSecundario  = Color(0xFFD1D5DB),
    textoTerciario   = Color(0xFF9CA3AF),
    textoDesactivado = Color(0xFF6B7280),
)

val Nocturno = NivelOscuridad(
    nombre = "Nocturno",
    fondoTop     = Color(0xFF131620),
    fondoMid     = Color(0xFF171B25),
    fondoBottom  = Color(0xFF0F1219),
    superficie         = Color(0xFF1C2029),
    superficieVariante = Color(0xFF21252F),
    cardAlpha    = 0.06f,
    borderAlpha  = 0.07f,
    textoPrimario    = Color(0xFFF5F5F5),
    textoSecundario  = Color(0xFFD1D5DB),
    textoTerciario   = Color(0xFF6B7280),
    textoDesactivado = Color(0xFF4B5563),
)

val Abismo = NivelOscuridad(
    nombre = "Abismo",
    fondoTop     = Color(0xFF090B10),
    fondoMid     = Color(0xFF0C0F15),
    fondoBottom  = Color(0xFF06080C),
    superficie         = Color(0xFF111419),
    superficieVariante = Color(0xFF15181E),
    cardAlpha    = 0.05f,
    borderAlpha  = 0.05f,
    textoPrimario    = Color(0xFFE5E7EB),
    textoSecundario  = Color(0xFFD1D5DB),
    textoTerciario   = Color(0xFF6B7280),
    textoDesactivado = Color(0xFF374151),
)
