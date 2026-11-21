package cl.storeflow.warehouse.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    onVolver: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi plan") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (val estado = uiState) {
                        PlanUiState.SinPlan -> {
                            Text("Plan no disponible", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Conéctate a internet para consultar tu plan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is PlanUiState.Activo -> {
                            Text(nombrePlan(estado.tipo), style = MaterialTheme.typography.titleLarge)
                            Text(
                                nombreEstado(estado.estado),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            estado.diasRestantes?.let { dias ->
                                Text(
                                    textoDias(dias),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            (uiState as? PlanUiState.Activo)?.let { estado ->
                if (estado.retenidosPropios.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Trabajo retenido en este dispositivo", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Está guardado y se enviará cuando el plan lo permita.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            estado.retenidosPropios.forEach {
                                Text("${nombreEntidad(it.entidad_tipo)}: ${it.cantidad}")
                            }
                        }
                    }
                }
                if (estado.retenidosPorUsuario.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Trabajo retenido por usuario", style = MaterialTheme.typography.titleMedium)
                            estado.retenidosPorUsuario.forEach {
                                Text("${it.nombre}: ${it.cantidad}")
                                Text(
                                    "Actualizado: ${fechaCorta(it.actualizadoAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun nombreEntidad(tipo: String) = when (tipo) {
    "movimientos" -> "Movimientos"
    "productos" -> "Productos"
    "bodegas" -> "Bodegas"
    "lotes" -> "Lotes"
    "producto_atributos" -> "Atributos de productos"
    "atributo_templates" -> "Plantillas de atributos"
    else -> tipo
}

private fun fechaCorta(valor: String): String = try {
    OffsetDateTime.parse(valor)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
} catch (e: DateTimeParseException) {
    valor
}

private fun nombrePlan(tipo: String) = when (tipo) {
    "FREE" -> "Plan Free"
    "STANDARD" -> "Plan Standard"
    "PREMIUM" -> "Plan Premium"
    else -> "Plan $tipo"
}

private fun nombreEstado(estado: String) = when (estado) {
    "TRIAL" -> "Período de prueba"
    "PAGADO" -> "Suscripción activa"
    "TESTER" -> "Cuenta de pruebas"
    "BLOQUEADO" -> "Acceso bloqueado"
    "FREE" -> "Plan gratuito"
    else -> estado
}

private fun textoDias(dias: Int) = when (dias) {
    0 -> "El período de prueba terminó"
    1 -> "Queda 1 día de prueba"
    else -> "Quedan $dias días de prueba"
}
