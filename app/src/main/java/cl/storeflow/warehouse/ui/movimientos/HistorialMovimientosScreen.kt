package cl.storeflow.warehouse.ui.movimientos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.storeflow.warehouse.data.local.entity.TipoMovimiento
import cl.storeflow.warehouse.domain.model.MovimientoConProducto
import cl.storeflow.warehouse.ui.components.BackButton
import java.text.SimpleDateFormat
import java.util.Locale

private val ColorEntrada = Color(0xFF2E7D32)
private val ColorSalida = Color(0xFFC62828)
private val ColorAjuste = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialMovimientosScreen(
    onVolver: () -> Unit,
    onVerProducto: (String) -> Unit,
    viewModel: HistorialMovimientosViewModel = hiltViewModel()
) {
    val busqueda by viewModel.busqueda.collectAsState()
    val movimientosVisibles by viewModel.movimientosVisibles.collectAsState()
    val movimientosFiltrados by viewModel.movimientosFiltrados.collectAsState()
    val hayMas by viewModel.hayMas.collectAsState()
    val tamanioPagina by viewModel.tamanioPagina.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de movimientos") },
                navigationIcon = { BackButton(onClick = onVolver) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = viewModel::setBusqueda,
                    placeholder = { Text("Buscar por nombre o SKU...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (busqueda.isNotBlank()) {
                        { IconButton(onClick = { viewModel.setBusqueda("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda")
                        }}
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var expandedTamanio by remember { mutableStateOf(false) }
                Text(
                    "Mostrar:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    TextButton(onClick = { expandedTamanio = true }) {
                        Text("$tamanioPagina")
                    }
                    DropdownMenu(
                        expanded = expandedTamanio,
                        onDismissRequest = { expandedTamanio = false }
                    ) {
                        listOf(25, 50, 100).forEach { opcion ->
                            DropdownMenuItem(
                                text = { Text("$opcion") },
                                onClick = {
                                    expandedTamanio = false
                                    viewModel.setTamanioPagina(opcion)
                                }
                            )
                        }
                    }
                }
            }

            if (movimientosFiltrados.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (busqueda.isBlank())
                            "Sin movimientos registrados"
                        else
                            "Sin resultados para \"$busqueda\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(movimientosVisibles, key = { it.id }) { movimiento ->
                        HistorialMovimientoItem(
                            movimiento = movimiento,
                            onClick = { onVerProducto(movimiento.producto_id) }
                        )
                    }
                    if (hayMas) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(onClick = { viewModel.cargarMas() }) {
                                    Text("Cargar más (${movimientosVisibles.size} de ${movimientosFiltrados.size})")
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
private fun HistorialMovimientoItem(
    movimiento: MovimientoConProducto,
    onClick: () -> Unit
) {
    val (etiqueta, color) = when (movimiento.tipo) {
        TipoMovimiento.ENTRADA -> "ENTRADA" to ColorEntrada
        TipoMovimiento.SALIDA -> "SALIDA" to ColorSalida
        TipoMovimiento.AJUSTE -> "AJUSTE" to ColorAjuste
    }
    val cantidadTexto = if (movimiento.cantidad >= 0) "+${movimiento.cantidad}" else "${movimiento.cantidad}"
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        overlineContent = { Text(etiqueta, color = color, style = MaterialTheme.typography.labelSmall) },
        headlineContent = {
            Row {
                Text(movimiento.producto_nombre, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(cantidadTexto, fontWeight = FontWeight.Bold, color = color)
            }
        },
        supportingContent = {
            val partes = buildList {
                movimiento.producto_sku?.let { add("SKU: $it") }
                if (!movimiento.nota.isNullOrBlank()) add(movimiento.nota)
                add(fmt.format(movimiento.created_at))
            }
            Text(partes.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)
        }
    )
    HorizontalDivider()
}