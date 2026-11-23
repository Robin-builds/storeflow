package cl.storeflow.warehouse.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Cubre la app para un usuario excedente. No muestra datos ni borra nada del dispositivo (FR-029). */
@Composable
fun BloqueoPlanScreen(
    hayTrabajoSinSincronizar: Boolean,
    onReintentar: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    BackHandler(enabled = true) { }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Text(
                "Acceso bloqueado",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                "Tu usuario no tiene acceso con el plan actual de la empresa. " +
                    "Habla con el administrador de tu empresa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (hayTrabajoSinSincronizar) {
                Text(
                    "Tu trabajo sin sincronizar está guardado en este dispositivo y no se perderá.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            Button(onClick = onReintentar, modifier = Modifier.fillMaxWidth()) {
                Text("Volver a comprobar")
            }
            if (!hayTrabajoSinSincronizar) {
                TextButton(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth()) {
                    Text("Cerrar sesión")
                }
            }
        }
    }
}
