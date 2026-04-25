package cl.stockflow.warehouse.ui.productos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.stockflow.warehouse.domain.model.ProductoConStock
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductosListScreen(
    onVolver: () -> Unit,
    onVerMovimientos: (productoId: String) -> Unit,
    viewModel: ProductoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val busqueda by viewModel.busqueda.collectAsState()
    val productosFiltrados by viewModel.productosFiltrados.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var mostrarFormCrear by remember { mutableStateOf(false) }
    var productoAEditar by remember { mutableStateOf<ProductoConStock?>(null) }

    LaunchedEffect(formState) {
        if (formState is FormUiState.Guardado) {
            val mensaje = (formState as FormUiState.Guardado).mensaje
            mostrarFormCrear = false
            productoAEditar = null
            viewModel.limpiarFormState()
            scope.launch { snackbarHostState.showSnackbar(mensaje) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Productos") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarFormCrear = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo producto")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = busqueda,
                onValueChange = viewModel::setBusqueda,
                placeholder = { Text("Buscar producto...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when (val state = uiState) {
                is ProductosUiState.Cargando -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ProductosUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.mensaje, color = MaterialTheme.colorScheme.error)
                    }
                }
                is ProductosUiState.Listo -> {
                    if (productosFiltrados.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (busqueda.isBlank())
                                    "No hay productos. Toca + para crear uno."
                                else
                                    "Sin resultados para \"$busqueda\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn {
                            items(productosFiltrados, key = { it.id }) { producto ->
                                ProductoItem(
                                    producto = producto,
                                    onClick = { productoAEditar = producto },
                                    onVerMovimientos = { onVerMovimientos(producto.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarFormCrear) {
        ProductoFormDialog(
            titulo = "Nuevo producto",
            productoInicial = null,
            formState = formState,
            onGuardar = { nombre, desc, sku, precio, stockMin, stockInicial ->
                viewModel.crear(nombre, desc, sku, precio, stockMin, stockInicial)
            },
            onEliminar = null,
            onDismiss = {
                mostrarFormCrear = false
                viewModel.limpiarFormState()
            }
        )
    }

    productoAEditar?.let { producto ->
        ProductoFormDialog(
            titulo = "Editar producto",
            productoInicial = producto,
            formState = formState,
            onGuardar = { nombre, desc, sku, precio, stockMin, _ ->
                viewModel.actualizar(producto, nombre, desc, sku, precio, stockMin)
            },
            onEliminar = { viewModel.eliminar(producto.id) },
            onDismiss = {
                productoAEditar = null
                viewModel.limpiarFormState()
            }
        )
    }
}

@Composable
private fun ProductoItem(
    producto: ProductoConStock,
    onClick: () -> Unit,
    onVerMovimientos: () -> Unit
) {
    val stockBajo = producto.stock_actual < producto.stock_minimo
    ListItem(
        headlineContent = { Text(producto.nombre) },
        supportingContent = {
            Text(
                buildString {
                    append("Stock: ${producto.stock_actual}")
                    if (producto.stock_minimo > 0) append("  ·  Mín: ${producto.stock_minimo}")
                    if (producto.sku != null) append("  ·  SKU: ${producto.sku}")
                },
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stockBajo) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "Stock bajo",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onVerMovimientos) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Ver movimientos"
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}

@Composable
private fun ProductoFormDialog(
    titulo: String,
    productoInicial: ProductoConStock?,
    formState: FormUiState,
    onGuardar: (nombre: String, descripcion: String?, sku: String?, precio: Double, stockMin: Int, stockInicial: Int) -> Unit,
    onEliminar: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val modoCrear = productoInicial == null
    var nombre by remember { mutableStateOf(productoInicial?.nombre ?: "") }
    var descripcion by remember { mutableStateOf(productoInicial?.descripcion ?: "") }
    var sku by remember { mutableStateOf(productoInicial?.sku ?: "") }
    var precio by remember { mutableStateOf(productoInicial?.precio?.toString() ?: "0") }
    var stock_minimo by remember { mutableStateOf(productoInicial?.stock_minimo?.toString() ?: "0") }
    var stock_inicial by remember { mutableStateOf("0") }
    var mostrarConfirmarEliminar by remember { mutableStateOf(false) }

    val cargando = formState is FormUiState.Cargando
    val errorMensaje = (formState as? FormUiState.Error)?.mensaje

    val precioValido = precio.toDoubleOrNull()?.let { it >= 0 } ?: false
    val stockMinimoValido = stock_minimo.toIntOrNull()?.let { it >= 0 } ?: false
    val stockInicialValido = stock_inicial.toIntOrNull()?.let { it >= 0 } ?: false
    val stockInicialInt = stock_inicial.toIntOrNull() ?: 0
    val stockMinimoInt = stock_minimo.toIntOrNull() ?: 0
    val stockInicialMenorQueMinimo = modoCrear && stockInicialInt > 0 && stockInicialInt < stockMinimoInt
    val formularioValido = nombre.isNotBlank() && precioValido && stockMinimoValido &&
            stockInicialValido && !stockInicialMenorQueMinimo

    if (mostrarConfirmarEliminar) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarEliminar = false },
            title = { Text("¿Eliminar producto?") },
            text = { Text("Esta acción eliminará el producto y todos sus movimientos. No se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = { onEliminar?.invoke() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmarEliminar = false }) { Text("Cancelar") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!cargando) onDismiss() },
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre *") },
                    singleLine = true,
                    enabled = !cargando,
                    isError = nombre.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción") },
                    singleLine = true,
                    enabled = !cargando,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("SKU") },
                    singleLine = true,
                    enabled = !cargando,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = precio,
                        onValueChange = { precio = it },
                        label = { Text("Precio") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !cargando,
                        isError = !precioValido,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stock_minimo,
                        onValueChange = { stock_minimo = it },
                        label = { Text("Stock mín.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !cargando,
                        isError = !stockMinimoValido,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (modoCrear) {
                    OutlinedTextField(
                        value = stock_inicial,
                        onValueChange = { stock_inicial = it },
                        label = { Text("Stock inicial") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !cargando,
                        isError = stockInicialMenorQueMinimo || !stockInicialValido,
                        supportingText = when {
                            stockInicialMenorQueMinimo -> {{ Text("No puede ser menor al stock mínimo ($stockMinimoInt)") }}
                            else -> null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorMensaje != null) {
                    Text(
                        text = errorMensaje,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (onEliminar != null) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    TextButton(
                        onClick = { mostrarConfirmarEliminar = true },
                        enabled = !cargando,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Eliminar producto") }
                }
            }
        },
        confirmButton = {
            if (cargando) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(
                    onClick = {
                        onGuardar(
                            nombre.trim(),
                            descripcion.trim().ifBlank { null },
                            sku.trim().ifBlank { null },
                            precio.toDoubleOrNull() ?: 0.0,
                            stock_minimo.toIntOrNull() ?: 0,
                            if (modoCrear) stock_inicial.toIntOrNull() ?: 0 else 0
                        )
                    },
                    enabled = formularioValido
                ) { Text("Guardar") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !cargando) { Text("Cancelar") }
        }
    )
}
