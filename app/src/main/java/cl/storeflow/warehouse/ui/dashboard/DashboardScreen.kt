package cl.storeflow.warehouse.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import cl.storeflow.warehouse.ui.alertas.AlertasUiState
import cl.storeflow.warehouse.ui.alertas.AlertasViewModel
import cl.storeflow.warehouse.ui.bodegas.BodegaViewModel
import cl.storeflow.warehouse.ui.bodegas.BodegasUiState
@Composable
fun DashboardScreen(
    onIrAConfiguracion: () -> Unit,
    onLogout: () -> Unit,
    onIrAProductos: () -> Unit,
    onIrAAlerta: () -> Unit,
    onIrABodegas: () -> Unit,
    onIrAAtributos: () -> Unit,
    onIrAUsuarios: () -> Unit,
    alertasViewModel: AlertasViewModel = hiltViewModel(),
    bodegaViewModel: BodegaViewModel = hiltViewModel()
) {
    val alertasState by alertasViewModel.uiState.collectAsState()
    val bodegasState by bodegaViewModel.uiState.collectAsState()
    val countAlertas = (alertasState as? AlertasUiState.Listo)?.alertas?.size ?: 0
    val bodegaActiva = (bodegasState as? BodegasUiState.Listo)?.activa
    val esAdmin = (bodegasState as? BodegasUiState.Listo)?.esAdmin ?: false

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "StoreFlow",
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text("Productos")
            }
            Spacer(modifier = Modifier.height(12.dp))
            ElevatedButton(
                onClick = onIrABodegas,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 3.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Gestionar bodegas")
            }
            if (esAdmin) {
                Spacer(modifier = Modifier.height(12.dp))
                ElevatedButton(
                    onClick = onIrAAtributos,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 3.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Configurar atributos")
                }
                Spacer(modifier = Modifier.height(12.dp))
                ElevatedButton(
                    onClick = onIrAUsuarios,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 3.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Gestionar usuarios")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
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
