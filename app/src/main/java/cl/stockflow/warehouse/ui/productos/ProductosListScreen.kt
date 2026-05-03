package cl.stockflow.warehouse.ui.productos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.stockflow.warehouse.domain.model.AtributoTemplate
import cl.stockflow.warehouse.domain.model.Producto
import cl.stockflow.warehouse.ui.components.BackButton
import cl.stockflow.warehouse.ui.theme.Ambar500
import cl.stockflow.warehouse.ui.theme.Rojo600
import cl.stockflow.warehouse.ui.theme.Verde400
import cl.stockflow.warehouse.ui.theme.Verde700
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
    val templates by viewModel.templates.collectAsState()
    val productoEditando by viewModel.productoEditando.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var mostrarFormCrear by remember { mutableStateOf(false) }

    LaunchedEffect(formState) {
        if (formState is FormUiState.Guardado) {
            val mensaje = (formState as FormUiState.Guardado).mensaje
            mostrarFormCrear = false
            viewModel.limpiarEdicion()
            viewModel.limpiarFormState()
            scope.launch { snackbarHostState.showSnackbar(mensaje) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Productos") },
                navigationIcon = { BackButton(onClick = onVolver) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarFormCrear = true },
                containerColor = Verde700,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                )
            ) {
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
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(productosFiltrados, key = { it.id }) { producto ->
                                ProductoItem(
                                    producto = producto,
                                    onClick = { onVerMovimientos(producto.id) }
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
            templates = templates,
            formState = formState,
            onGuardar = { nombre, desc, sku, precio, stockMin, stockInicial, atributos ->
                viewModel.crear(nombre, desc, sku, precio, stockMin, stockInicial, atributos)
            },
            onEliminar = null,
            onDismiss = {
                mostrarFormCrear = false
                viewModel.limpiarFormState()
            }
        )
    }

    productoEditando?.let { producto ->
        ProductoFormDialog(
            titulo = "Editar producto",
            productoInicial = producto,
            templates = templates,
            formState = formState,
            onGuardar = { nombre, desc, sku, precio, stockMin, _, atributos ->
                viewModel.actualizar(producto, nombre, desc, sku, precio, stockMin, atributos)
            },
            onEliminar = { viewModel.eliminar(producto.id) },
            onDismiss = {
                viewModel.limpiarEdicion()
                viewModel.limpiarFormState()
            }
        )
    }
}

@Composable
private fun ProductoItem(
    producto: Producto,
    onClick: () -> Unit
) {
    val borderColor = when {
        producto.stockActual == 0 -> Rojo600
        producto.esBajoStock() -> Ambar500
        else -> Verde400
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(color = borderColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    text = producto.nombre,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = buildString {
                        append("Stock: ${producto.stockActual}")
                        if (producto.stockMinimo > 0) append("  ·  Mín: ${producto.stockMinimo}")
                        if (producto.sku != null) append("  ·  SKU: ${producto.sku}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onClick,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color = Verde700, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Ver movimientos",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductoFormDialog(
    titulo: String,
    productoInicial: Producto?,
    templates: List<AtributoTemplate>,
    formState: FormUiState,
    onGuardar: (nombre: String, descripcion: String?, sku: String?, precio: Int, stockMin: Int, stockInicial: Int, atributos: Map<String, String>) -> Unit,
    onEliminar: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val modoCrear = productoInicial == null
    var nombre by remember { mutableStateOf(productoInicial?.nombre ?: "") }
    var descripcion by remember { mutableStateOf(productoInicial?.descripcion ?: "") }
    var sku by remember { mutableStateOf(productoInicial?.sku ?: "") }
    var precio by remember { mutableStateOf(productoInicial?.precio?.toString() ?: "0") }
    var stock_minimo by remember { mutableStateOf(productoInicial?.stockMinimo?.toString() ?: "0") }
    var stock_inicial by remember { mutableStateOf("0") }
    var mostrarConfirmarEliminar by remember { mutableStateOf(false) }

    // Map templateId → valor; pre-rellena desde producto.atributos (clave→valor) usando los templates
    val atributosState = remember(templates, productoInicial) {
        mutableStateMapOf<String, String>().also { map ->
            templates.forEach { t ->
                map[t.id] = productoInicial?.atributos?.get(t.clave) ?: ""
            }
        }
    }

    val cargando = formState is FormUiState.Cargando
    val errorMensaje = (formState as? FormUiState.Error)?.mensaje

    val precioValido = precio.toIntOrNull()?.let { it >= 0 } ?: false
    val stockMinimoValido = stock_minimo.toIntOrNull()?.let { it >= 0 } ?: false
    val stockInicialValido = stock_inicial.toIntOrNull()?.let { it >= 0 } ?: false
    val stockInicialInt = stock_inicial.toIntOrNull() ?: 0
    val stockMinimoInt = stock_minimo.toIntOrNull() ?: 0
    val stockInicialMenorQueMinimo = modoCrear && stockInicialInt > 0 && stockInicialInt < stockMinimoInt
    val atributosObligatoriosCubiertos = templates
        .filter { it.obligatorio }
        .all { t -> (atributosState[t.id] ?: "").isNotBlank() }
    val formularioValido = nombre.isNotBlank() && precioValido && stockMinimoValido &&
            stockInicialValido && !stockInicialMenorQueMinimo && atributosObligatoriosCubiertos

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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                if (templates.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    templates.forEach { template ->
                        OutlinedTextField(
                            value = atributosState[template.id] ?: "",
                            onValueChange = { atributosState[template.id] = it },
                            label = {
                                Text(if (template.obligatorio) "${template.etiqueta} *" else template.etiqueta)
                            },
                            singleLine = true,
                            enabled = !cargando,
                            isError = template.obligatorio && (atributosState[template.id] ?: "").isBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                            precio.toIntOrNull() ?: 0,
                            stock_minimo.toIntOrNull() ?: 0,
                            if (modoCrear) stock_inicial.toIntOrNull() ?: 0 else 0,
                            atributosState.filter { (_, v) -> v.isNotBlank() }
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
