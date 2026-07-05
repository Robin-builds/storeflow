package cl.storeflow.warehouse.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

fun crearColorScheme(
    paleta: PaletaAcento,
    oscuridad: NivelOscuridad
): ColorScheme = darkColorScheme(
    primary = paleta.primario,
    onPrimary = Color.White,
    primaryContainer = paleta.primarioClaro,
    onPrimaryContainer = oscuridad.fondoBottom,

    secondary = paleta.neutro,
    onSecondary = Color.White,
    secondaryContainer = paleta.neutroOscuro,
    onSecondaryContainer = paleta.neutroClaro,

    tertiary = paleta.primarioSuave,
    onTertiary = oscuridad.fondoBottom,

    background = oscuridad.fondoTop,
    onBackground = oscuridad.textoPrimario,

    surface = oscuridad.superficie,
    onSurface = oscuridad.textoPrimario,
    surfaceVariant = oscuridad.superficieVariante,
    onSurfaceVariant = oscuridad.textoSecundario,

    error = paleta.alerta,
    onError = Color.White,
    errorContainer = paleta.alertaClaro,
    onErrorContainer = paleta.alertaSuave,

    outline = oscuridad.textoDesactivado,
    outlineVariant = Color.White.copy(alpha = oscuridad.borderAlpha),
)

data class StoreFlowColoresExtendidos(
    val paleta: PaletaAcento,
    val oscuridad: NivelOscuridad,

    val fondoGradiente: List<Color>,

    val cardGradienteTop: Color,
    val cardGradienteBottom: Color,
    val cardBorde: Color,

    val sombraPrimario: Color,
    val sombraNeutro: Color,
)

val LocalStoreFlowColors = staticCompositionLocalOf<StoreFlowColoresExtendidos> {
    error("StoreFlowColoresExtendidos no proporcionados. Envolver en StoreFlowTheme.")
}

object StoreFlowTheme {
    val coloresExtendidos: StoreFlowColoresExtendidos
        @Composable
        get() = LocalStoreFlowColors.current
}

fun crearColoresExtendidos(
    paleta: PaletaAcento,
    oscuridad: NivelOscuridad
): StoreFlowColoresExtendidos = StoreFlowColoresExtendidos(
    paleta = paleta,
    oscuridad = oscuridad,
    fondoGradiente = listOf(
        oscuridad.fondoTop,
        oscuridad.fondoMid,
        oscuridad.fondoBottom
    ),
    cardGradienteTop = Color.White.copy(alpha = oscuridad.cardAlpha),
    cardGradienteBottom = Color.White.copy(alpha = oscuridad.cardAlpha * 0.25f),
    cardBorde = Color.White.copy(alpha = oscuridad.borderAlpha),
    sombraPrimario = paleta.primario.copy(alpha = 0.3f),
    sombraNeutro = paleta.neutro.copy(alpha = 0.2f),
)

@Composable
fun StoreFlowTheme(
    paleta: PaletaAcento = PaletaBunker,
    oscuridad: NivelOscuridad = Nocturno,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(paleta, oscuridad) {
        crearColorScheme(paleta, oscuridad)
    }
    val coloresExtendidos = remember(paleta, oscuridad) {
        crearColoresExtendidos(paleta, oscuridad)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(LocalStoreFlowColors provides coloresExtendidos) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StoreFlowTypography,
            shapes = StoreFlowShapes,
            content = content
        )
    }
}
