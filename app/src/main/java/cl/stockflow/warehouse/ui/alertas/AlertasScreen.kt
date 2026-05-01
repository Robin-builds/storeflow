package cl.stockflow.warehouse.ui.alertas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.stockflow.warehouse.domain.model.Producto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertasScreen(
    onVolver: () -> Unit,
    onVerMovimientos: (productoId: String) -> Unit,
    viewModel: AlertasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertas de stock") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is AlertasUiState.Cargando -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AlertasUiState.Listo -> {
                if (state.alertas.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Todo en orden",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "No hay productos bajo stock mínimo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(contentPadding = padding) {
                        items(state.alertas, key = { it.id }) { producto ->
                            AlertaItem(
                                producto = producto,
                                onVerMovimientos = { onVerMovimientos(producto.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertaItem(
    producto: Producto,
    onVerMovimientos: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(producto.nombre, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(
                text = "Stock: ${producto.stockActual}  ·  Mínimo: ${producto.stockMinimo}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        trailingContent = {
            IconButton(onClick = onVerMovimientos) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ver movimientos")
            }
        }
    )
    HorizontalDivider()
}
