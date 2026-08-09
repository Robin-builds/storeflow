package cl.storeflow.warehouse.ui.alertas

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.storeflow.warehouse.domain.model.LoteProximoAVencer
import cl.storeflow.warehouse.domain.model.Producto
import cl.storeflow.warehouse.ui.components.BackButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val ColorVencimiento = Color(0xFFEF6C00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertasScreen(
    onVolver: () -> Unit,
    onVerMovimientos: (productoId: String) -> Unit,
    viewModel: AlertasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val bodegaNombre by viewModel.bodegaNombre.collectAsState()
    val proximosAVencer by viewModel.proximosAVencer.collectAsState()
    val context = LocalContext.current

    val alertas = (uiState as? AlertasUiState.Listo)?.alertas ?: emptyList()
    val hayAlgo = alertas.isNotEmpty() || proximosAVencer.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertas") },
                navigationIcon = { BackButton(onClick = onVolver) },
                actions = {
                    if (hayAlgo) {
                        IconButton(onClick = {
                            val texto = generarTextoAlertas(alertas, proximosAVencer, bodegaNombre)
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
                if (!hayAlgo) {
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
                                text = "Sin stock bajo mínimo ni productos próximos a vencer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(contentPadding = padding) {
                        if (proximosAVencer.isNotEmpty()) {
                            item { SeccionHeader("Próximos a vencer") }
                            items(proximosAVencer, key = { it.id }) { lote ->
                                Box(modifier = Modifier.animateItem()) {
                                    LoteVencimientoItem(lote)
                                }
                            }
                        }
                        if (state.alertas.isNotEmpty()) {
                            item { SeccionHeader("Bajo stock mínimo") }
                            items(state.alertas, key = { it.id }) { producto ->
                                Box(modifier = Modifier.animateItem()) {
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
    }
}

@Composable
private fun SeccionHeader(titulo: String) {
    Text(
        text = titulo,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private fun generarTextoAlertas(
    alertas: List<Producto>,
    proximosAVencer: List<LoteProximoAVencer>,
    bodegaNombre: String
): String {
    val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    return buildString {
        appendLine("⚠️ *Alertas — StoreFlow*")
        if (bodegaNombre.isNotBlank()) appendLine("Bodega: $bodegaNombre")
        appendLine(fecha)
        if (proximosAVencer.isNotEmpty()) {
            appendLine()
            appendLine("Próximos a vencer:")
            proximosAVencer.forEach { l ->
                appendLine("• ${l.producto_nombre}: ${l.stock_actual} uds — ${textoVencimiento(l.fecha_caducidad)}")
            }
        }
        if (alertas.isNotEmpty()) {
            appendLine()
            appendLine("Bajo stock mínimo:")
            alertas.forEach { p ->
                appendLine("• ${p.nombre}: ${p.stockActual} / ${p.stockMinimo} (actual / mínimo)")
            }
        }
    }.trimEnd()
}

private fun textoVencimiento(fechaCaducidad: Date): String {
    val diasRestantes = TimeUnit.MILLISECONDS.toDays(fechaCaducidad.time - System.currentTimeMillis())
    return when {
        diasRestantes < 0 -> "vencido hace ${-diasRestantes} día${if (diasRestantes != -1L) "s" else ""}"
        diasRestantes == 0L -> "vence hoy"
        else -> "vence en $diasRestantes día${if (diasRestantes != 1L) "s" else ""}"
    }
}

@Composable
private fun LoteVencimientoItem(lote: LoteProximoAVencer) {
    ListItem(
        headlineContent = {
            Text(lote.producto_nombre, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(
                text = buildString {
                    append("Stock: ${lote.stock_actual}")
                    if (!lote.numero_lote.isNullOrBlank()) append("  ·  Lote: ${lote.numero_lote}")
                    append("  ·  ${textoVencimiento(lote.fecha_caducidad)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = ColorVencimiento
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = ColorVencimiento
            )
        }
    )
    HorizontalDivider()
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
