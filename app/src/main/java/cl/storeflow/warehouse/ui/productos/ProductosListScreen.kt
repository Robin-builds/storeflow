package cl.storeflow.warehouse.ui.productos

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.storeflow.warehouse.domain.model.AtributoTemplate
import cl.storeflow.warehouse.domain.model.Producto
import cl.storeflow.warehouse.ui.components.BackButton
import cl.storeflow.warehouse.ui.components.BarcodeScannerDialog
import cl.storeflow.warehouse.ui.theme.Ambar500
import cl.storeflow.warehouse.ui.theme.Rojo600
import cl.storeflow.warehouse.ui.theme.Verde400
import cl.storeflow.warehouse.ui.theme.StoreFlowTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val productosVisibles by viewModel.productosVisibles.collectAsState()
    val hayMas by viewModel.hayMas.collectAsState()
    val tamanioPagina by viewModel.tamanioPagina.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val productoEditando by viewModel.productoEditando.collectAsState()
    val seleccionados by viewModel.seleccionados.collectAsState()
    val modoSeleccion by viewModel.modoSeleccion.collectAsState()
    val bodegas by viewModel.bodegas.collectAsState()
    val primario = StoreFlowTheme.coloresExtendidos.paleta.primario

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var mostrarFormCrear by remember { mutableStateOf(false) }
    var mostrarConfirmarEliminarMasivo by remember { mutableStateOf(false) }
    var mostrarTransferirDialog by remember { mutableStateOf(false) }
    var mostrarScannerBusqueda by remember { mutableStateOf(false) }

    LaunchedEffect(formState) {
        if (formState is FormUiState.Guardado) {
            val mensaje = (formState as FormUiState.Guardado).mensaje
            mostrarFormCrear = false
            viewModel.limpiarEdicion()
            viewModel.limpiarFormState()
            scope.launch { snackbarHostState.showSnackbar(mensaje) }
        }
    }

    val bodegasDestino = remember(bodegas) { bodegas.filter { !it.esActiva } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (modoSeleccion) {
                TopAppBar(
                    title = {
                        Text("${seleccionados.size} seleccionado${if (seleccionados.size != 1) "s" else ""}")
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Productos") },
                    navigationIcon = { BackButton(onClick = onVolver) },
                    actions = {
                        if (productosFiltrados.isNotEmpty()) {
                            val bodegaNombre = remember(bodegas) {
                                bodegas.firstOrNull { it.esActiva }?.nombre ?: ""
                            }
                            IconButton(onClick = {
                                val texto = generarTextoInventario(productosFiltrados, bodegaNombre)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, texto)
                                }
                                context.startActivity(Intent.createChooser(intent, "Compartir vía"))
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Compartir inventario")
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!modoSeleccion) {
                FloatingActionButton(
                    onClick = { mostrarFormCrear = true },
                    containerColor = primario,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Nuevo producto")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { mostrarScannerBusqueda = true }) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = "Escanear código",
                        tint = primario
                    )
                }
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

            if (mostrarScannerBusqueda) {
                BarcodeScannerDialog(
                    onBarcodeDetected = { valor -> viewModel.setBusqueda(valor) },
                    onDismiss = { mostrarScannerBusqueda = false }
                )
            }

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
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = if (modoSeleccion) 160.dp else 8.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(productosVisibles, key = { it.id }) { producto ->
                                ProductoItem(
                                    producto = producto,
                                    seleccionado = producto.id in seleccionados,
                                    modoSeleccion = modoSeleccion,
                                    onEditar = { viewModel.seleccionarParaEditar(producto.id) },
                                    onVerMovimientos = { onVerMovimientos(producto.id) },
                                    onLongPress = { viewModel.toggleSeleccion(producto.id) },
                                    onToggleSeleccion = { viewModel.toggleSeleccion(producto.id) },
                                    modifier = Modifier.animateItem()
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
                                            Text("Cargar más (${productosVisibles.size} de ${productosFiltrados.size})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Card de acciones flotante sobre la lista
        if (modoSeleccion) {
            val todosSeleccionados = productosVisibles.isNotEmpty() &&
                    seleccionados.size == productosVisibles.size
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.limpiarSeleccion() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancelar") }
                        OutlinedButton(
                            onClick = {
                                if (todosSeleccionados) viewModel.limpiarSeleccion()
                                else viewModel.seleccionarTodos()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (todosSeleccionados) "Ninguno" else "Todos") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { mostrarTransferirDialog = true },
                            enabled = seleccionados.isNotEmpty() && bodegasDestino.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Transferir") }
                        Button(
                            onClick = { mostrarConfirmarEliminarMasivo = true },
                            enabled = seleccionados.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) { Text("Eliminar") }
                    }
                }
            }
        }
        }
    }

    if (mostrarConfirmarEliminarMasivo) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarEliminarMasivo = false },
            title = { Text("¿Eliminar ${seleccionados.size} producto${if (seleccionados.size != 1) "s" else ""}?") },
            text = { Text("Se eliminarán los productos y todos sus movimientos. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmarEliminarMasivo = false
                        viewModel.eliminarSeleccionados()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmarEliminarMasivo = false }) { Text("Cancelar") }
            }
        )
    }

    if (mostrarTransferirDialog) {
        AlertDialog(
            onDismissRequest = { mostrarTransferirDialog = false },
            title = { Text("Transferir a bodega") },
            text = {
                Column {
                    bodegasDestino.forEach { bodega ->
                        TextButton(
                            onClick = {
                                mostrarTransferirDialog = false
                                viewModel.transferirSeleccionados(bodega.id, bodega.nombre)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = bodega.nombre,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { mostrarTransferirDialog = false }) { Text("Cancelar") }
            }
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductoItem(
    producto: Producto,
    seleccionado: Boolean,
    modoSeleccion: Boolean,
    onEditar: () -> Unit,
    onVerMovimientos: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSeleccion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        producto.stockActual == 0 -> Rojo600
        producto.esBajoStock() -> Ambar500
        else -> Verde400
    }
    val primario = StoreFlowTheme.coloresExtendidos.paleta.primario
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (seleccionado) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (seleccionado)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .then(if (modoSeleccion) Modifier.clickable { onToggleSeleccion() } else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // En modo normal: tap = editar, long-press = iniciar selección
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(IntrinsicSize.Min)
                    .then(if (!modoSeleccion) Modifier.combinedClickable(
                        onClick = onEditar,
                        onLongClick = onLongPress
                    ) else Modifier),
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
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = producto.nombre,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!producto.descripcion.isNullOrBlank()) {
                        Text(
                            text = producto.descripcion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
            }
            // Botón derecho: checkbox en selección, flecha verde para movimientos en modo normal
            if (modoSeleccion) {
                Checkbox(
                    checked = seleccionado,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 12.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(32.dp)
                        .background(color = primario, shape = CircleShape)
                        .clickable { onVerMovimientos() },
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
    var mostrarScanner by remember { mutableStateOf(false) }

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

    if (mostrarScanner) {
        BarcodeScannerDialog(
            onBarcodeDetected = { valor -> sku = valor },
            onDismiss = { mostrarScanner = false }
        )
    }

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
                    minLines = 1,
                    maxLines = 3,
                    enabled = !cargando,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU") },
                        singleLine = true,
                        enabled = !cargando,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { mostrarScanner = true },
                        enabled = !cargando
                    ) {
                        Text("Escanear")
                    }
                }
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
                } else {
                    OutlinedTextField(
                        value = (productoInicial?.stockActual ?: 0).toString(),
                        onValueChange = {},
                        label = { Text("Stock actual") },
                        singleLine = true,
                        enabled = false,
                        supportingText = { Text("Registra un movimiento para modificarlo") },
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

private fun generarTextoInventario(productos: List<Producto>, bodegaNombre: String): String {
    val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    return buildString {
        appendLine("📦 *Inventario completo — StoreFlow*")
        if (bodegaNombre.isNotBlank()) appendLine("Bodega: $bodegaNombre")
        appendLine(fecha)
        appendLine()
        productos.forEach { p ->
            val alerta = if (p.esBajoStock() || p.stockActual == 0) " ⚠️" else ""
            appendLine("• ${p.nombre}: ${p.stockActual} uds.$alerta")
        }
    }.trimEnd()
}
