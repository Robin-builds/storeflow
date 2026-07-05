package cl.storeflow.warehouse.ui.ayuda

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OnboardingDialog(ayudaViewModel: AyudaViewModel = hiltViewModel()) {
    val onboardingVisto by ayudaViewModel.onboardingVisto.collectAsState()

    if (onboardingVisto) return

    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warehouse,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "¡Bienvenido a StoreFlow!",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                "Tu empresa y Bodega Principal ya están creadas. Empieza agregando tu primer producto.\n\n" +
                    "Los íconos \"?\" en cada sección te explican cómo funciona todo. También puedes descargar " +
                    "la guía completa en Configuración → Ayuda."
            )
        },
        confirmButton = {
            Button(onClick = { ayudaViewModel.marcarOnboardingVisto() }) {
                Text("Comenzar")
            }
        }
    )
}
