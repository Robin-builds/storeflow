package cl.storeflow.warehouse.ui.bodegas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import cl.storeflow.warehouse.ui.components.BackButton
import cl.storeflow.warehouse.ui.theme.Verde700
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.storeflow.warehouse.domain.model.Bodega

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodegasScreen(
    onVolver: () -> Unit,
    onBodegaCambiada: () -> Unit,
    viewModel: BodegaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarDialogCrear by remember { mutableStateOf(false) }
    var bodegaAEliminar by remember { mutableStateOf<Bodega?>(null) }

    LaunchedEffect(Unit) {
        viewModel.navegarADashboard.collect { onBodegaCambiada() }
    }
    LaunchedEffect(Unit) {
        viewModel.mensaje.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bodegas") },
                navigationIcon = { BackButton(onClick = onVolver) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if ((uiState as? BodegasUiState.Listo)?.esAdmin == true) {
                FloatingActionButton(
                    onClick = { mostrarDialogCrear = true },
                    containerColor = Verde700,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva bodega")
                }
            }
        }
    ) { padding ->
        when (val state = uiState) {
            is BodegasUiState.Cargando -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is BodegasUiState.Listo -> {
                if (state.bodegas.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay bodegas registradas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(contentPadding = padding) {
                        items(state.bodegas, key = { it.id }) { bodega ->
                            val puedeEliminar = state.esAdmin &&
                                    state.bodegas.size > 1 &&
                                    !bodega.esActiva
                            Box(modifier = Modifier.animateItem()) {
                                BodegaItem(
                                    bodega = bodega,
                                    puedeEliminar = puedeEliminar,
                                    onSeleccionar = {
                                        if (!bodega.esActiva) viewModel.cambiarBodegaActiva(bodega.id)
                                    },
                                    onEliminar = { bodegaAEliminar = bodega }
                                )
                            }
                        }
                    }
                }
            }
            is BodegasUiState.Error -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.mensaje, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (mostrarDialogCrear) {
        DialogCrearBodega(
            onConfirmar = { nombre, ubicacion ->
                viewModel.crear(nombre, ubicacion)
                mostrarDialogCrear = false
            },
            onCancelar = { mostrarDialogCrear = false }
        )
    }

    bodegaAEliminar?.let { bodega ->
        AlertDialog(
            onDismissRequest = { bodegaAEliminar = null },
            title = { Text("Eliminar bodega") },
            text = { Text("¿Eliminar \"${bodega.nombre}\"? Los productos existentes serán trasladados automáticamente a la bodega más antigua de la empresa. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.eliminar(bodega.id)
                        bodegaAEliminar = null
                    }
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { bodegaAEliminar = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun BodegaItem(
    bodega: Bodega,
    puedeEliminar: Boolean,
    onSeleccionar: () -> Unit,
    onEliminar: () -> Unit
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bodega.nombre,
                    fontWeight = if (bodega.esActiva) FontWeight.SemiBold else FontWeight.Normal
                )
                if (bodega.esActiva) {
                    Spacer(Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Activa", style = MaterialTheme.typography.labelSmall) },
                        icon = {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        },
        supportingContent = bodega.ubicacion?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        trailingContent = {
            if (puedeEliminar) {
                IconButton(onClick = onEliminar) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar bodega",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = Modifier.clickable(enabled = !bodega.esActiva, onClick = onSeleccionar)
    )
    HorizontalDivider()
}

@Composable
private fun DialogCrearBodega(
    onConfirmar: (nombre: String, ubicacion: String?) -> Unit,
    onCancelar: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var ubicacion by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Nueva bodega") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ubicacion,
                    onValueChange = { ubicacion = it },
                    label = { Text("Ubicación (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(nombre, ubicacion.ifBlank { null }) },
                enabled = nombre.isNotBlank()
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}
