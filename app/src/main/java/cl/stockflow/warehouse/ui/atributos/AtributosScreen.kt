package cl.stockflow.warehouse.ui.atributos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.stockflow.warehouse.domain.model.AtributoTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtributosScreen(
    onVolver: () -> Unit,
    viewModel: AtributoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarDialogCrear by remember { mutableStateOf(false) }
    var templateAEliminar by remember { mutableStateOf<AtributoTemplate?>(null) }

    LaunchedEffect(Unit) {
        viewModel.mensaje.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Atributos de productos") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if ((uiState as? AtributosUiState.Listo)?.esAdmin == true) {
                FloatingActionButton(onClick = { mostrarDialogCrear = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo atributo")
                }
            }
        }
    ) { padding ->
        when (val state = uiState) {
            is AtributosUiState.Cargando -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AtributosUiState.Listo -> {
                if (state.templates.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Sin atributos configurados",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.esAdmin) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Agrega campos personalizados para todos los productos de tu empresa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(contentPadding = padding) {
                        items(state.templates, key = { it.id }) { template ->
                            AtributoItem(
                                template = template,
                                esAdmin = state.esAdmin,
                                onEliminar = { templateAEliminar = template }
                            )
                        }
                    }
                }
            }
            is AtributosUiState.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.mensaje, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (mostrarDialogCrear) {
        DialogCrearAtributo(
            onConfirmar = { clave, etiqueta, obligatorio ->
                viewModel.crear(clave, etiqueta, obligatorio)
                mostrarDialogCrear = false
            },
            onCancelar = { mostrarDialogCrear = false }
        )
    }

    templateAEliminar?.let { template ->
        AlertDialog(
            onDismissRequest = { templateAEliminar = null },
            title = { Text("Eliminar atributo") },
            text = {
                Text("¿Eliminar \"${template.etiqueta}\"? Se borrarán los valores de este campo en todos los productos.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.eliminar(template)
                        templateAEliminar = null
                    }
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { templateAEliminar = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun AtributoItem(
    template: AtributoTemplate,
    esAdmin: Boolean,
    onEliminar: () -> Unit
) {
    ListItem(
        headlineContent = { Text(template.etiqueta) },
        supportingContent = {
            Text(
                text = buildString {
                    append("clave: ${template.clave}")
                    if (template.obligatorio) append(" · obligatorio")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (esAdmin) {
                IconButton(onClick = onEliminar) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar atributo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
    HorizontalDivider()
}

@Composable
private fun DialogCrearAtributo(
    onConfirmar: (clave: String, etiqueta: String, obligatorio: Boolean) -> Unit,
    onCancelar: () -> Unit
) {
    var clave by remember { mutableStateOf("") }
    var etiqueta by remember { mutableStateOf("") }
    var obligatorio by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Nuevo atributo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = etiqueta,
                    onValueChange = { etiqueta = it },
                    label = { Text("Etiqueta *") },
                    placeholder = { Text("ej: Principio activo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it.lowercase().replace(" ", "_") },
                    label = { Text("Clave interna *") },
                    placeholder = { Text("ej: principio_activo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { obligatorio = !obligatorio }
                ) {
                    Checkbox(
                        checked = obligatorio,
                        onCheckedChange = { obligatorio = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Obligatorio", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmar(clave, etiqueta, obligatorio) },
                enabled = clave.isNotBlank() && etiqueta.isNotBlank()
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}
