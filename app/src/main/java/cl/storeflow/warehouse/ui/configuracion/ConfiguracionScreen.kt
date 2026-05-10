package cl.storeflow.warehouse.ui.configuracion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cl.storeflow.warehouse.ui.theme.TemaApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(
    tema: TemaApp,
    onSetTema: (TemaApp) -> Unit,
    onVolver: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apariencia") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Tema de la aplicación",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            TemaApp.entries.forEach { opcion ->
                TemaOpcionFila(
                    opcion = opcion,
                    seleccionada = opcion == tema,
                    onSeleccionar = { onSetTema(opcion) }
                )
                if (opcion != TemaApp.entries.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Cerrar sesión",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun TemaOpcionFila(
    opcion: TemaApp,
    seleccionada: Boolean,
    onSeleccionar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSeleccionar)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = seleccionada, onClick = onSeleccionar)
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = opcion.icono,
            contentDescription = null,
            tint = if (seleccionada) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = opcion.etiqueta,
                style = MaterialTheme.typography.bodyLarge,
                color = if (seleccionada) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = opcion.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val TemaApp.etiqueta: String get() = when (this) {
    TemaApp.CLARO       -> "Claro"
    TemaApp.OSCURO      -> "Oscuro"
    TemaApp.OSCURO_PLUS -> "Oscuro máximo"
    TemaApp.AUTO        -> "Automático"
}

private val TemaApp.descripcion: String get() = when (this) {
    TemaApp.CLARO       -> "Fondo blanco, texto oscuro"
    TemaApp.OSCURO      -> "Fondo oscuro, confort nocturno"
    TemaApp.OSCURO_PLUS -> "Alto contraste moderado"
    TemaApp.AUTO        -> "Sigue la configuración del sistema"
}

private val TemaApp.icono: ImageVector get() = when (this) {
    TemaApp.CLARO       -> Icons.Filled.WbSunny
    TemaApp.OSCURO      -> Icons.Filled.NightsStay
    TemaApp.OSCURO_PLUS -> Icons.Filled.DarkMode
    TemaApp.AUTO        -> Icons.Filled.AutoAwesome
}