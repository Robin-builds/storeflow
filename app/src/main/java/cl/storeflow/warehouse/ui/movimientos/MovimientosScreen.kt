package cl.storeflow.warehouse.ui.movimientos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.storeflow.warehouse.data.local.entity.MovimientoEntity
import cl.storeflow.warehouse.data.local.entity.TipoMovimiento
import cl.storeflow.warehouse.domain.model.Producto
import cl.storeflow.warehouse.ui.components.BackButton
import cl.storeflow.warehouse.ui.theme.Verde400
import cl.storeflow.warehouse.ui.theme.Verde700
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val ColorEntrada = Color(0xFF2E7D32)
private val ColorSalida = Color(0xFFC62828)
private val ColorAjuste = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimientosScreen(
    onVolver: () -> Unit,
    viewModel: MovimientoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tipoSeleccionado by remember { mutableStateOf<TipoMovimiento?>(null) }

    LaunchedEffect(formState) {
        if (formState is MovFormState.Guardado) {
            val mensaje = (formState as MovFormState.Guardado).mensaje
            tipoSeleccionado = null
            viewModel.limpiarFormState()
            scope.launch { snackbarHostState.showSnackbar(mensaje) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val nombre = (uiState as? MovimientosUiState.Listo)?.producto?.nombre ?: "Movimientos"
                    Text(nombre)
                },
                navigationIcon = { BackButton(onClick = onVolver) }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is MovimientosUiState.Cargando -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MovimientosUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.mensaje, color = MaterialTheme.colorScheme.error)
                }
            }
            is MovimientosUiState.Listo -> {
                ContenidoMovimientos(
                    producto = state.producto,
                    movimientos = state.movimientos,
                    tipoActivo = tipoSeleccionado,
                    onRegistrar = { tipo -> tipoSeleccionado = tipo },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    val stockActual = (uiState as? MovimientosUiState.Listo)?.producto?.stockActual ?: 0
    tipoSeleccionado?.let { tipo ->
        MovimientoDialog(
            tipo = tipo,
            stockActual = stockActual,
            formState = formState,
            onRegistrar = { cantidad, nota ->
                when (tipo) {
                    TipoMovimiento.ENTRADA -> viewModel.registrarEntrada(cantidad, nota)
                    TipoMovimiento.SALIDA -> viewModel.registrarSalida(cantidad, nota)
                    TipoMovimiento.AJUSTE -> viewModel.registrarAjuste(cantidad, nota)
                }
            },
            onDismiss = {
                tipoSeleccionado = null
                viewModel.limpiarFormState()
            }
        )
    }
}

@Composable
private fun ContenidoMovimientos(
    producto: Producto,
    movimientos: List<MovimientoEntity>,
    tipoActivo: TipoMovimiento?,
    onRegistrar: (TipoMovimiento) -> Unit,
    modifier: Modifier = Modifier
) {
    val stockBajo = producto.esBajoStock()

    Column(modifier = modifier.fillMaxSize()) {
        // Tarjeta de stock
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (stockBajo)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stockBajo) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    Text(
                        text = "Stock actual: ${producto.stockActual}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (producto.stockMinimo > 0) {
                        Text(
                            text = "Mínimo: ${producto.stockMinimo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Botones de acción
        val tipos = listOf(TipoMovimiento.ENTRADA, TipoMovimiento.SALIDA, TipoMovimiento.AJUSTE)
        val etiquetas = listOf("Entrada", "Salida", "Ajuste")
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            tipos.forEachIndexed { index, tipo ->
                SegmentedButton(
                    selected = tipoActivo == tipo,
                    onClick = { onRegistrar(tipo) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tipos.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Verde700,
                        activeContentColor = Color.White,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = Verde700,
                        activeBorderColor = Verde700,
                        inactiveBorderColor = Verde400
                    )
                ) {
                    Text(etiquetas[index])
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        // Historial
        if (movimientos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Sin movimientos registrados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(movimientos, key = { it.id }) { movimiento ->
                    Box(modifier = Modifier.animateItem()) {
                        MovimientoItem(movimiento)
                    }
                }
            }
        }
    }
}

@Composable
private fun MovimientoItem(movimiento: MovimientoEntity) {
    val (etiqueta, color) = when (movimiento.tipo) {
        TipoMovimiento.ENTRADA -> "ENTRADA" to ColorEntrada
        TipoMovimiento.SALIDA -> "SALIDA" to ColorSalida
        TipoMovimiento.AJUSTE -> "AJUSTE" to ColorAjuste
    }
    val cantidadTexto = if (movimiento.cantidad >= 0) "+${movimiento.cantidad}" else "${movimiento.cantidad}"
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    ListItem(
        overlineContent = { Text(etiqueta, color = color, style = MaterialTheme.typography.labelSmall) },
        headlineContent = {
            Text(cantidadTexto, fontWeight = FontWeight.Bold, color = color)
        },
        supportingContent = {
            val partes = buildList {
                if (!movimiento.nota.isNullOrBlank()) add(movimiento.nota)
                add(fmt.format(movimiento.created_at))
            }
            Text(partes.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)
        }
    )
    HorizontalDivider()
}

@Composable
private fun MovimientoDialog(
    tipo: TipoMovimiento,
    stockActual: Int,
    formState: MovFormState,
    onRegistrar: (cantidad: Int, nota: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val titulo = when (tipo) {
        TipoMovimiento.ENTRADA -> "Registrar entrada"
        TipoMovimiento.SALIDA -> "Registrar salida"
        TipoMovimiento.AJUSTE -> "Ajustar stock"
    }
    val campoLabel = when (tipo) {
        TipoMovimiento.ENTRADA -> "Cantidad a ingresar"
        TipoMovimiento.SALIDA -> "Cantidad a retirar"
        TipoMovimiento.AJUSTE -> "Nuevo stock total"
    }
    val ayuda = when (tipo) {
        TipoMovimiento.SALIDA -> "Disponible: $stockActual"
        TipoMovimiento.AJUSTE -> "Stock actual: $stockActual"
        else -> null
    }

    var cantidad by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }

    val cargando = formState is MovFormState.Cargando
    val errorMensaje = (formState as? MovFormState.Error)?.mensaje

    val cantidadInt = cantidad.toIntOrNull()
    val notaValida = nota.isNotBlank()
    val cantidadValida = when (tipo) {
        TipoMovimiento.ENTRADA -> cantidadInt != null && cantidadInt > 0
        TipoMovimiento.SALIDA -> cantidadInt != null && cantidadInt > 0 && cantidadInt <= stockActual
        TipoMovimiento.AJUSTE -> cantidadInt != null && cantidadInt >= 0
    }
    val cantidadError = when {
        tipo == TipoMovimiento.SALIDA && cantidadInt != null && cantidadInt > stockActual ->
            "No puede superar el stock disponible ($stockActual)"
        else -> null
    }
    val puedeGuardar = cantidadValida && notaValida

    AlertDialog(
        onDismissRequest = { if (!cargando) onDismiss() },
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { cantidad = it },
                    label = { Text(campoLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !cargando,
                    isError = cantidadError != null,
                    supportingText = when {
                        cantidadError != null -> { { Text(cantidadError) } }
                        ayuda != null -> { { Text(ayuda, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = {
                        Text(
                            when (tipo) {
                                TipoMovimiento.ENTRADA -> "Razón de la entrada *"
                                TipoMovimiento.SALIDA -> "Razón de la salida *"
                                TipoMovimiento.AJUSTE -> "Razón del ajuste *"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            when (tipo) {
                                TipoMovimiento.ENTRADA -> "Ej: compra a proveedor, devolución..."
                                TipoMovimiento.SALIDA -> "Ej: venta, despacho, consumo interno..."
                                TipoMovimiento.AJUSTE -> "Ej: conteo físico, merma, hurto..."
                            }
                        )
                    },
                    singleLine = true,
                    enabled = !cargando,
                    isError = nota.isNotEmpty() && nota.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMensaje != null) {
                    Text(
                        text = errorMensaje,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (cargando) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(
                    onClick = { onRegistrar(cantidadInt ?: 0, nota.trim().ifBlank { null }) },
                    enabled = puedeGuardar
                ) { Text("Guardar") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !cargando) { Text("Cancelar") }
        }
    )

}
