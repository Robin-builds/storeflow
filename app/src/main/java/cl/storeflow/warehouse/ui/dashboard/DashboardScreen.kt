package cl.storeflow.warehouse.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inventory
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.storeflow.warehouse.domain.model.Producto
import cl.storeflow.warehouse.domain.model.Rol
import cl.storeflow.warehouse.ui.alertas.AlertasUiState
import cl.storeflow.warehouse.ui.alertas.AlertasViewModel
import cl.storeflow.warehouse.ui.atributos.AtributosUiState
import cl.storeflow.warehouse.ui.atributos.AtributoViewModel
import cl.storeflow.warehouse.ui.bodegas.BodegaViewModel
import cl.storeflow.warehouse.ui.bodegas.BodegasUiState
import cl.storeflow.warehouse.ui.productos.ProductoViewModel
import cl.storeflow.warehouse.ui.productos.ProductosUiState
import cl.storeflow.warehouse.ui.theme.StoreFlowColoresExtendidos
import cl.storeflow.warehouse.ui.theme.StoreFlowTheme
import cl.storeflow.warehouse.ui.usuarios.UsuariosUiState
import cl.storeflow.warehouse.ui.usuarios.UsuariosViewModel

@Composable
fun DashboardScreen(
    onIrAConfiguracion: () -> Unit,
    onIrAProductos: () -> Unit,
    onIrAAlerta: () -> Unit,
    onIrABodegas: () -> Unit,
    onIrAAtributos: () -> Unit,
    onIrAUsuarios: () -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colores.fondoGradiente))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STOREFLOW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp
                        ),
                        color = colores.oscuridad.textoDesactivado
                    )
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
            Spacer(Modifier.height(24.dp))

            AnimatedVisibility(
                visible = countAlertas > 0,
                enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(300))
            ) {
                Column {
                    Card(
                        onClick = onIrAAlerta,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colores.paleta.alerta.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, colores.paleta.alerta.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = colores.paleta.alerta)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "$countAlertas producto${if (countAlertas > 1) "s" else ""} bajo stock mínimo",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colores.oscuridad.textoPrimario
                                )
                                Text(
                                    "Toca para ver el detalle",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colores.oscuridad.textoSecundario
                                )
                            }
                        }
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
                    onClick = onIrAProductos
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
                    onClick = onIrABodegas
                )
            }

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
                            title = "Especificaciones",
                            icon = Icons.Outlined.Tune,
                            lines = listOf(
                                if (totalEspecificaciones > 0) "$totalEspecificaciones definidas"
                                else "Sin definir",
                                "Características"
                            ),
                            warningText = null,
                            onClick = onIrAAtributos
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
                            onClick = onIrAUsuarios
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
                            onClick = onIrAProductos
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
                            onClick = onIrAProductos
                        )
                    }
                }
            }
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
    modifier: Modifier = Modifier
) {
    val colores = StoreFlowTheme.coloresExtendidos
    val acento = colores.paleta.primario
    val sombra = colores.sombraPrimario

    Card(
        onClick = onClick,
        modifier = modifier,
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
    modifier: Modifier = Modifier
) {
    val colores = StoreFlowTheme.coloresExtendidos

    Card(
        onClick = onClick,
        modifier = modifier,
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
                Text(title, style = MaterialTheme.typography.titleSmall, color = colores.oscuridad.textoPrimario)
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
}
