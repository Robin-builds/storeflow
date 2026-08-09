package cl.storeflow.warehouse.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.storeflow.warehouse.R
import cl.storeflow.warehouse.domain.model.Producto
import cl.storeflow.warehouse.domain.model.Rol
import cl.storeflow.warehouse.ui.alertas.AlertasUiState
import cl.storeflow.warehouse.ui.alertas.AlertasViewModel
import cl.storeflow.warehouse.ui.atributos.AtributosUiState
import cl.storeflow.warehouse.ui.atributos.AtributoViewModel
import cl.storeflow.warehouse.ui.ayuda.OnboardingDialog
import cl.storeflow.warehouse.ui.bodegas.BodegaViewModel
import cl.storeflow.warehouse.ui.bodegas.BodegasUiState
import cl.storeflow.warehouse.ui.components.BotonAyuda
import cl.storeflow.warehouse.ui.productos.ProductoViewModel
import cl.storeflow.warehouse.ui.productos.ProductosUiState
import cl.storeflow.warehouse.ui.theme.StoreFlowColoresExtendidos
import cl.storeflow.warehouse.ui.theme.StoreFlowTheme
import cl.storeflow.warehouse.ui.usuarios.UsuariosUiState
import cl.storeflow.warehouse.ui.usuarios.UsuariosViewModel

private val ColorAcentoCian = Color(0xFF2EC6DA)
private val ColorAcentoNaranja = Color(0xFFF0921E)

@Composable
fun DashboardScreen(
    onIrAConfiguracion: () -> Unit,
    onIrAProductos: () -> Unit,
    onIrAAlerta: () -> Unit,
    onIrABodegas: () -> Unit,
    onIrAAtributos: () -> Unit,
    onIrAUsuarios: () -> Unit,
    onIrAProductosConBusqueda: (String) -> Unit,
    onIrAHistorial: () -> Unit,
    alertasViewModel: AlertasViewModel = hiltViewModel(),
    bodegaViewModel: BodegaViewModel = hiltViewModel(),
    productoViewModel: ProductoViewModel = hiltViewModel(),
    atributoViewModel: AtributoViewModel = hiltViewModel(),
    usuariosViewModel: UsuariosViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val colores = StoreFlowTheme.coloresExtendidos

    val alertasState by alertasViewModel.uiState.collectAsState()
    val bodegasState by bodegaViewModel.uiState.collectAsState()
    val nombreUsuario by dashboardViewModel.nombreUsuario.collectAsState()
    val rolActual by dashboardViewModel.rolActual.collectAsState()
    val productosState by productoViewModel.uiState.collectAsState()
    val atributosState by atributoViewModel.uiState.collectAsState()
    val usuariosState by usuariosViewModel.uiState.collectAsState()
    val sinMovimientoReciente by dashboardViewModel.sinMovimientoReciente.collectAsState()
    val busquedaGlobal by dashboardViewModel.busquedaGlobal.collectAsState()
    val resultadosBusquedaGlobal by dashboardViewModel.resultadosBusquedaGlobal.collectAsState()
    val countProximosAVencer by dashboardViewModel.countProximosAVencer.collectAsState()

    val countAlertas = (alertasState as? AlertasUiState.Listo)?.alertas?.size ?: 0
    val bodegaActiva = (bodegasState as? BodegasUiState.Listo)?.activa
    val esAdmin = (bodegasState as? BodegasUiState.Listo)?.esAdmin ?: false
    val todasLasBodegas = (bodegasState as? BodegasUiState.Listo)?.bodegas ?: emptyList()

    val productos = (productosState as? ProductosUiState.Listo)?.productos ?: emptyList()
    val totalUnidades = productos.sumOf { it.stockActual }
    val productosMenorStock = productos.sortedBy { it.stockActual }.take(3)

    val totalEspecificaciones = (atributosState as? AtributosUiState.Listo)?.templates?.size ?: 0

    val usuarios = (usuariosState as? UsuariosUiState.Listo)?.usuarios ?: emptyList()
    val totalAdmins = usuarios.count { it.esAdmin() }

    OnboardingDialog()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colores.fondoGradiente))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Cabecera fija: no se desplaza con el scroll del resto del contenido
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        val densidad = LocalDensity.current
                        val estiloWordmark = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box {
                            // Contorno blanco delgado: mismo texto dibujado solo con trazo, detrás del relleno
                            Text(
                                text = "STOREFLOW",
                                style = estiloWordmark.copy(
                                    color = Color.White,
                                    drawStyle = Stroke(
                                        width = with(densidad) { 0.7.dp.toPx() },
                                        join = StrokeJoin.Round
                                    )
                                )
                            )
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = ColorAcentoCian)) { append("STORE") }
                                    withStyle(SpanStyle(color = ColorAcentoNaranja)) { append("FLOW") }
                                },
                                style = estiloWordmark
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = bodegaActiva?.nombre ?: "Cargando...",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = colores.oscuridad.textoPrimario
                    )
                    if (nombreUsuario.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$nombreUsuario · ${if (rolActual == Rol.ADMIN) "Administrador" else "Operador"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.paleta.primarioSuave
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    IconButton(onClick = onIrAConfiguracion) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Configuración",
                            tint = colores.oscuridad.textoSecundario
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(bottom = 24.dp)
            ) {
                BusquedaProductoCard(
                    busqueda = busquedaGlobal,
                    resultados = resultadosBusquedaGlobal,
                    onBusquedaChange = dashboardViewModel::buscarProductoGlobal,
                    onVerEnProductos = onIrAProductosConBusqueda
                )
                Spacer(Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = countAlertas > 0 || countProximosAVencer > 0,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max)
                        ) {
                            AlertaMiniCard(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                icon = Icons.Outlined.Warning,
                                count = countAlertas,
                                etiqueta = "bajo stock mínimo",
                                onClick = onIrAAlerta,
                                ayudaTitulo = "Alerta de stock bajo",
                                ayudaExplicacion = "Este banner aparece cuando uno o más productos tienen " +
                                    "stock por debajo de su stock mínimo configurado.\n\n" +
                                    "Toca el banner para ver la lista completa de productos afectados " +
                                    "con su stock actual vs su stock mínimo.",
                                ayudaEjemplo = "Si dice \"3 productos bajo stock mínimo\", significa que 3 de " +
                                    "tus productos necesitan reposición. Toca para ver cuáles son y " +
                                    "cuánto les falta."
                            )
                            Spacer(Modifier.width(12.dp))
                            AlertaMiniCard(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                icon = Icons.Outlined.Schedule,
                                count = countProximosAVencer,
                                etiqueta = "próximos a vencer",
                                onClick = onIrAAlerta,
                                ayudaTitulo = "Próximos a vencer",
                                ayudaExplicacion = "Muestra los lotes de productos perecederos cuya fecha " +
                                    "de caducidad está a 7 días o menos, o que ya vencieron.\n\n" +
                                    "Aplica solo a productos marcados como \"Es perecedero\", cuyas " +
                                    "entradas registran fecha de caducidad y número de lote.",
                                ayudaEjemplo = "Si tienes un lote de Yogurt que vence en 3 días, aparecerá " +
                                    "aquí para que lo prioricés en la salida (el sistema usa FEFO: primero " +
                                    "en vencer, primero en salir)."
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max)
                ) {
                    NavCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Productos",
                        icon = Icons.Outlined.Inventory,
                        lines = listOf(
                            "${productos.size} productos",
                            "$totalUnidades unidades"
                        ),
                        warningText = if (countAlertas > 0) "$countAlertas bajo stock" else null,
                        onClick = onIrAProductos,
                        ayudaTitulo = "Productos",
                        ayudaExplicacion = "Aquí ves todos los productos registrados en la bodega activa. " +
                            "El número grande es la cantidad total de productos diferentes (SKUs), y abajo " +
                            "ves la suma de todas las unidades en stock.\n\n" +
                            "El stock de cada producto se calcula automáticamente sumando todos sus " +
                            "movimientos de entrada y salida. No se ingresa manualmente.",
                        ayudaEjemplo = "Si registras una ENTRADA de 100 unidades de Harina y luego una " +
                            "SALIDA de 30, el stock que verás será 70 unidades. Toca esta tarjeta para ver " +
                            "la lista completa de productos."
                    )
                    Spacer(Modifier.width(12.dp))
                    NavCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Bodegas",
                        icon = Icons.Outlined.Warehouse,
                        lines = listOfNotNull(
                            "${todasLasBodegas.size} bodegas",
                            bodegaActiva?.let { "Activa: ${it.nombre}" }
                        ),
                        warningText = null,
                        onClick = onIrABodegas,
                        ayudaTitulo = "Bodegas",
                        ayudaExplicacion = "Una bodega es un almacén o punto de almacenamiento dentro de tu " +
                            "empresa. Cada bodega contiene sus propios productos y movimientos de forma " +
                            "independiente.\n\n" +
                            "La bodega 'Activa' es la que estás usando ahora mismo — todos los productos, " +
                            "movimientos y alertas que ves en el dashboard corresponden a esta bodega.",
                        ayudaEjemplo = "Un minimarket podría tener 'Bodega Tienda' (lo que está en la sala " +
                            "de ventas) y 'Bodega Trasera' (la reserva). Cada una lleva su propio " +
                            "inventario. Puedes cambiar de bodega activa desde esta tarjeta."
                    )
                }

                Spacer(Modifier.height(12.dp))
                NavCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Historial de movimientos",
                    icon = Icons.Outlined.History,
                    lines = listOf("Entradas, salidas y ajustes de toda la empresa"),
                    warningText = null,
                    onClick = onIrAHistorial,
                    ayudaTitulo = "Historial de movimientos",
                    ayudaExplicacion = "Lista todos los movimientos (entradas, salidas y ajustes) de todos " +
                        "los productos y bodegas de la empresa, ordenados del más reciente al más " +
                        "antiguo.\n\n" +
                        "Puedes buscar por nombre de producto. Los movimientos son inmutables: una vez " +
                        "registrados, no se pueden editar ni borrar.",
                    ayudaEjemplo = "Si necesitas saber quién sacó 20 unidades de un producto y cuándo, " +
                        "aquí queda el registro completo, sin importar en qué bodega haya ocurrido."
                )

                AnimatedVisibility(
                    visible = esAdmin,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max)
                        ) {
                            NavCard(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                title = "Configurar productos",
                                icon = Icons.Outlined.Tune,
                                lines = listOf(
                                    if (totalEspecificaciones > 0) "$totalEspecificaciones definidas"
                                    else "Sin definir",
                                    "Características"
                                ),
                                warningText = null,
                                onClick = onIrAAtributos,
                                ayudaTitulo = "Especificaciones",
                                ayudaExplicacion = "Las especificaciones son características " +
                                    "personalizadas que puedes agregar a tus productos. Son plantillas " +
                                    "que defines una vez y luego asignas a los productos que " +
                                    "correspondan.\n\n" +
                                    "Esto te permite agregar información extra sin que todos los productos " +
                                    "tengan los mismos campos. Cada especificación tiene un nombre, un tipo " +
                                    "(texto) y se aplica solo a los productos que la necesiten.",
                                ayudaEjemplo = "Si vendes ropa, puedes crear la especificación 'Talla' y " +
                                    "'Color'. Luego asignas 'Talla=M' y 'Color=Azul' solo a los productos " +
                                    "de ropa, sin que los productos de ferretería tengan esos campos vacíos."
                            )
                            Spacer(Modifier.width(12.dp))
                            NavCard(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                title = "Usuarios",
                                icon = Icons.Outlined.Group,
                                lines = if (usuarios.isNotEmpty()) listOf(
                                    "${usuarios.size} usuario${if (usuarios.size > 1) "s" else ""}",
                                    "$totalAdmins administrador${if (totalAdmins > 1) "es" else ""}"
                                ) else listOf("Sin usuarios"),
                                warningText = null,
                                onClick = onIrAUsuarios,
                                ayudaTitulo = "Usuarios",
                                ayudaExplicacion = "Aquí gestionas las personas que tienen acceso a la " +
                                    "empresa en StoreFlow. Cada usuario tiene un email, nombre y un rol que " +
                                    "define qué puede hacer.\n\n" +
                                    "• Administrador: acceso completo. Puede crear y eliminar productos, " +
                                    "bodegas, usuarios, configurar especificaciones y ver reportes.\n\n" +
                                    "• Operador: acceso limitado. Puede registrar movimientos de entrada y " +
                                    "salida, ver productos y stock, pero no puede crear ni eliminar " +
                                    "productos, ni gestionar usuarios.",
                                ayudaEjemplo = "El dueño del negocio usa el rol Administrador. El bodeguero " +
                                    "o cajero que solo necesita registrar entradas y salidas de mercadería " +
                                    "usa el rol Operador."
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = productos.isNotEmpty(),
                    enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
                ) {
                    Column {
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max)
                        ) {
                            AlertInfoCard(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                title = "Menor stock",
                                icon = Icons.AutoMirrored.Outlined.TrendingDown,
                                items = productosMenorStock,
                                itemLabel = { p -> p.nombre },
                                itemSublabel = { p -> "${p.stockActual} uds" },
                                emptyText = "Sin productos",
                                onClick = onIrAProductos,
                                ayudaTitulo = "Menor stock",
                                ayudaExplicacion = "Esta sección muestra los productos que tienen stock " +
                                    "por debajo de su stock mínimo configurado, o que están en 0 " +
                                    "unidades.\n\n" +
                                    "El stock mínimo se define al crear o editar cada producto. Cuando el " +
                                    "stock calculado (entradas - salidas) cae por debajo de ese valor, el " +
                                    "producto aparece aquí como alerta.",
                                ayudaEjemplo = "Si 'Harina' tiene stock mínimo = 5 kg y actualmente tiene " +
                                    "2 kg (calculado de sus movimientos), aparecerá aquí con '2 uds'."
                            )
                            Spacer(Modifier.width(12.dp))
                            AlertInfoCard(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                title = "Sin actividad — 7d",
                                icon = Icons.Outlined.HourglassEmpty,
                                items = sinMovimientoReciente,
                                itemLabel = { p -> p.nombre },
                                itemSublabel = { _ -> "Sin movimientos" },
                                emptyText = "Todo activo",
                                onClick = onIrAProductos,
                                ayudaTitulo = "Sin actividad — 7 días",
                                ayudaExplicacion = "Esta sección muestra productos que no han tenido ningún " +
                                    "movimiento (ni entrada, ni salida, ni ajuste) en los últimos 7 días.\n\n" +
                                    "Es útil para detectar productos olvidados en bodega sin rotación, con " +
                                    "stock desactualizado por falta de registro, o próximos a vencimiento " +
                                    "por no moverse.\n\n" +
                                    "No es una alerta de error — es un recordatorio para revisar si ese " +
                                    "producto necesita atención.",
                                ayudaEjemplo = "Si 'Adaptador Bluetooth' aparece aquí, puede significar que " +
                                    "nadie ha vendido ni recibido esas unidades en una semana. Quizás " +
                                    "necesitas hacer un conteo físico o promocionarlo."
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertaMiniCard(
    icon: ImageVector,
    count: Int,
    etiqueta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ayudaTitulo: String? = null,
    ayudaExplicacion: String = "",
    ayudaEjemplo: String = ""
) {
    val colores = StoreFlowTheme.coloresExtendidos
    val activa = count > 0

    Box(modifier = modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (activa) colores.paleta.alerta.copy(alpha = 0.12f) else Color.Transparent
            ),
            border = BorderStroke(1.dp, if (activa) colores.paleta.alerta.copy(alpha = 0.3f) else colores.cardBorde)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (activa) colores.paleta.alerta else colores.oscuridad.textoTerciario
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (activa) colores.oscuridad.textoPrimario else colores.oscuridad.textoTerciario
                )
                Text(
                    text = etiqueta,
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.oscuridad.textoSecundario
                )
            }
        }
        if (ayudaTitulo != null) {
            BotonAyuda(
                titulo = ayudaTitulo,
                explicacion = ayudaExplicacion,
                ejemplo = ayudaEjemplo,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun NavCard(
    title: String,
    icon: ImageVector,
    lines: List<String>,
    warningText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ayudaTitulo: String? = null,
    ayudaExplicacion: String = "",
    ayudaEjemplo: String = ""
) {
    val colores = StoreFlowTheme.coloresExtendidos
    val acento = colores.paleta.primario
    val sombra = colores.sombraPrimario

    Box(modifier = modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, colores.cardBorde)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(colores.cardGradienteTop, colores.cardGradienteBottom)))
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = acento,
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(elevation = 8.dp, ambientColor = sombra, spotColor = sombra)
                )
                Spacer(Modifier.height(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = colores.oscuridad.textoPrimario)
                Spacer(Modifier.height(6.dp))
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.oscuridad.textoTerciario
                    )
                }
                if (warningText != null) {
                    Text(
                        text = warningText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.paleta.alerta
                    )
                }
            }
        }
        if (ayudaTitulo != null) {
            BotonAyuda(
                titulo = ayudaTitulo,
                explicacion = ayudaExplicacion,
                ejemplo = ayudaEjemplo,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun <T> AlertInfoCard(
    title: String,
    icon: ImageVector,
    items: List<T>,
    itemLabel: (T) -> String,
    itemSublabel: (T) -> String,
    emptyText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ayudaTitulo: String? = null,
    ayudaExplicacion: String = "",
    ayudaEjemplo: String = ""
) {
    val colores = StoreFlowTheme.coloresExtendidos

    Box(modifier = modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, colores.cardBorde)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(colores.cardGradienteTop, colores.cardGradienteBottom)))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colores.paleta.primario,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colores.oscuridad.textoPrimario,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (items.isEmpty()) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.oscuridad.textoTerciario
                    )
                } else {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = itemLabel(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = colores.oscuridad.textoSecundario,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = itemSublabel(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = colores.oscuridad.textoTerciario
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
        if (ayudaTitulo != null) {
            BotonAyuda(
                titulo = ayudaTitulo,
                explicacion = ayudaExplicacion,
                ejemplo = ayudaEjemplo,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }
    }
}
