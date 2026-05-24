package cl.storeflow.warehouse.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.storeflow.warehouse.domain.model.Producto
import cl.storeflow.warehouse.ui.alertas.AlertasUiState
import cl.storeflow.warehouse.ui.alertas.AlertasViewModel
import cl.storeflow.warehouse.ui.atributos.AtributosUiState
import cl.storeflow.warehouse.ui.atributos.AtributoViewModel
import cl.storeflow.warehouse.ui.bodegas.BodegaViewModel
import cl.storeflow.warehouse.ui.bodegas.BodegasUiState
import cl.storeflow.warehouse.ui.productos.ProductoViewModel
import cl.storeflow.warehouse.ui.productos.ProductosUiState
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
    val alertasState by alertasViewModel.uiState.collectAsState()
    val bodegasState by bodegaViewModel.uiState.collectAsState()
    val nombreUsuario by dashboardViewModel.nombreUsuario.collectAsState()
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 80.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("StoreFlow", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            if (nombreUsuario.isNotEmpty()) {
                Text(
                    text = "Hola, $nombreUsuario",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = bodegaActiva?.nombre ?: "Cargando...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "$countAlertas producto${if (countAlertas > 1) "s" else ""} bajo stock mínimo",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Toca para ver el detalle",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
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
                    icon = Icons.Filled.Inventory,
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
                    icon = Icons.Filled.Warehouse,
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
                            icon = Icons.Filled.Tune,
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
                            icon = Icons.Filled.Group,
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
                            icon = Icons.AutoMirrored.Filled.TrendingDown,
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
                            icon = Icons.Filled.HourglassEmpty,
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

        IconButton(
            onClick = onIrAConfiguracion,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Configuración",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (warningText != null) {
                Text(
                    text = warningText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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
    Card(
        onClick = onClick,
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(10.dp))
            if (items.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = itemSublabel(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
