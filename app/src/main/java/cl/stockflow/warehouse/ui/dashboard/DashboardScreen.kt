package cl.stockflow.warehouse.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cl.stockflow.warehouse.ui.alertas.AlertasUiState
import cl.stockflow.warehouse.ui.alertas.AlertasViewModel
import cl.stockflow.warehouse.ui.bodegas.BodegaViewModel
import cl.stockflow.warehouse.ui.bodegas.BodegasUiState

@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onIrAProductos: () -> Unit,
    onIrAAlerta: () -> Unit,
    onIrABodegas: () -> Unit,
    alertasViewModel: AlertasViewModel = hiltViewModel(),
    bodegaViewModel: BodegaViewModel = hiltViewModel()
) {
    val alertasState by alertasViewModel.uiState.collectAsState()
    val bodegasState by bodegaViewModel.uiState.collectAsState()
    val countAlertas = (alertasState as? AlertasUiState.Listo)?.alertas?.size ?: 0
    val bodegaActiva = (bodegasState as? BodegasUiState.Listo)?.activa

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "StockFlow",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = bodegaActiva?.nombre ?: "Cargando...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (countAlertas > 0) {
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
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$countAlertas producto${if (countAlertas > 1) "s" else ""} bajo stock mínimo",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Toca para ver el detalle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = onIrAProductos,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Productos")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onIrABodegas,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Gestionar bodegas")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Cerrar sesión")
        }
    }
}
