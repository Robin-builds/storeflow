package cl.stockflow.warehouse.ui.alertas

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.stockflow.warehouse.domain.model.Producto
import cl.stockflow.warehouse.ui.components.BackButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertasScreen(
    onVolver: () -> Unit,
    onVerMovimientos: (productoId: String) -> Unit,
    viewModel: AlertasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val bodegaNombre by viewModel.bodegaNombre.collectAsState()
    val context = LocalContext.current

    val alertas = (uiState as? AlertasUiState.Listo)?.alertas ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertas de stock") },
                navigationIcon = { BackButton(onClick = onVolver) },
                actions = {
                    if (alertas.isNotEmpty()) {
                        IconButton(onClick = {
                            val texto = generarTextoAlertas(alertas, bodegaNombre)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, texto)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir vía"))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartir alertas")
                        }
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

private fun generarTextoAlertas(alertas: List<Producto>, bodegaNombre: String): String {
    val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    return buildString {
        appendLine("⚠️ *Alertas de stock bajo — StockFlow*")
        if (bodegaNombre.isNotBlank()) appendLine("Bodega: $bodegaNombre")
        appendLine(fecha)
        appendLine()
        alertas.forEach { p ->
            appendLine("• ${p.nombre}: ${p.stockActual} / ${p.stockMinimo} (actual / mínimo)")
        }
    }.trimEnd()
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
