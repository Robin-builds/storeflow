package cl.storeflow.warehouse.ui.configuracion

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.storeflow.warehouse.ui.theme.NivelOscuridad
import cl.storeflow.warehouse.ui.theme.OscuridadId
import cl.storeflow.warehouse.ui.theme.PaletaAcento
import cl.storeflow.warehouse.ui.theme.PaletaId

private const val DASHBOARD_URL = "https://stockflow-web-eight.vercel.app"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(
    paletaSeleccionada: PaletaId,
    oscuridadSeleccionada: OscuridadId,
    onSetPaleta: (PaletaId) -> Unit,
    onSetOscuridad: (OscuridadId) -> Unit,
    onVolver: () -> Unit,
    onLogout: () -> Unit,
    onIrAReportarError: () -> Unit,
    viewModel: ConfiguracionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarDialogCambiarPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.mensaje.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Cuenta ---
            SeccionLabel("Cuenta")
            Card(modifier = Modifier.fillMaxWidth()) {
                ConfigFilaItem(
                    icon = Icons.Filled.Lock,
                    titulo = "Cambiar contraseña",
                    subtitulo = "Actualiza tu contraseña de acceso",
                    onClick = { mostrarDialogCambiarPassword = true }
                )
            }

            // --- Soporte ---
            SeccionLabel("Soporte")
            Card(modifier = Modifier.fillMaxWidth()) {
                ConfigFilaItem(
                    icon = Icons.Filled.BugReport,
                    titulo = "Reportar problema",
                    subtitulo = "Enviar capturas y descripción al soporte",
                    onClick = onIrAReportarError
                )
            }

            // --- Dashboard web ---
            SeccionLabel("Dashboard web")
            Card(modifier = Modifier.fillMaxWidth()) {
                ConfigFilaItem(
                    icon = Icons.Filled.Share,
                    titulo = "Compartir Dashboard",
                    subtitulo = "Enviar el enlace del panel web por WhatsApp, email u otro",
                    onClick = {
                        val texto = "Accede al panel de inventario de StoreFlow desde tu PC:\n$DASHBOARD_URL"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, texto)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir Dashboard"))
                    }
                )
            }

            // --- Apariencia ---
            SeccionLabel("Apariencia")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SelectorPaleta(
                        seleccionada = paletaSeleccionada,
                        onSeleccionar = onSetPaleta
                    )
                    SelectorOscuridad(
                        seleccionada = oscuridadSeleccionada,
                        onSeleccionar = onSetOscuridad
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // --- Cerrar sesión ---
            Card(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error
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
                        tint = Color.White
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Cerrar sesión",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (mostrarDialogCambiarPassword) {
        DialogCambiarPassword(
            operando = uiState is ConfiguracionUiState.Operando,
            onConfirmar = { actual, nueva ->
                viewModel.cambiarPassword(actual, nueva)
                mostrarDialogCambiarPassword = false
            },
            onCancelar = { mostrarDialogCambiarPassword = false }
        )
    }
}

@Composable
private fun SeccionLabel(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun ConfigFilaItem(
    icon: ImageVector,
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = titulo, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectorPaleta(
    seleccionada: PaletaId,
    onSeleccionar: (PaletaId) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        PaletaId.entries.forEach { paletaId ->
            PaletaCard(
                paleta = paletaId.paleta,
                seleccionada = seleccionada == paletaId,
                onClick = { onSeleccionar(paletaId) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PaletaCard(
    paleta: PaletaAcento,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bordeColor = if (seleccionada) paleta.primarioClaro
                      else MaterialTheme.colorScheme.outlineVariant

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (seleccionada) paleta.primario.copy(alpha = 0.12f)
                              else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(width = if (seleccionada) 2.dp else 1.dp, color = bordeColor)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(paleta.primario))
                Box(Modifier.size(16.dp).clip(CircleShape).background(paleta.neutro))
                Box(Modifier.size(16.dp).clip(CircleShape).background(paleta.alerta))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = paleta.nombre,
                style = MaterialTheme.typography.labelMedium,
                color = if (seleccionada) paleta.primarioClaro
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectorOscuridad(
    seleccionada: OscuridadId,
    onSeleccionar: (OscuridadId) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OscuridadId.entries.forEach { oscuridadId ->
            OscuridadCard(
                oscuridad = oscuridadId.oscuridad,
                seleccionada = seleccionada == oscuridadId,
                onClick = { onSeleccionar(oscuridadId) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OscuridadCard(
    oscuridad: NivelOscuridad,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bordeColor = if (seleccionada) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(width = if (seleccionada) 2.dp else 1.dp, color = bordeColor)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(oscuridad.fondoTop, oscuridad.fondoMid, oscuridad.fondoBottom)
                        )
                    )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = oscuridad.nombre,
                style = MaterialTheme.typography.labelMedium,
                color = if (seleccionada) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DialogCambiarPassword(
    operando: Boolean,
    onConfirmar: (actual: String, nueva: String) -> Unit,
    onCancelar: () -> Unit
) {
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }

    val nuevaValida = nueva.length >= 8
    val coincide = nueva == confirmar
    val puedeConfirmar = actual.isNotBlank() && nuevaValida && coincide && nueva != actual && !operando

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Cambiar contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = actual,
                    onValueChange = { actual = it },
                    label = { Text("Contraseña actual *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nueva,
                    onValueChange = { nueva = it },
                    label = { Text("Nueva contraseña *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = nueva.isNotEmpty() && !nuevaValida,
                    supportingText = {
                        if (nueva.isNotEmpty() && !nuevaValida) Text("Mínimo 8 caracteres")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmar,
                    onValueChange = { confirmar = it },
                    label = { Text("Confirmar nueva contraseña *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = confirmar.isNotEmpty() && !coincide,
                    supportingText = {
                        if (confirmar.isNotEmpty() && !coincide) Text("No coincide")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(actual, nueva) },
                enabled = puedeConfirmar
            ) { Text("Cambiar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}
