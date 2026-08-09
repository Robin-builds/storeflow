package cl.storeflow.warehouse.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.storeflow.warehouse.domain.model.ProductoConStockYBodega
import cl.storeflow.warehouse.ui.components.BarcodeScannerDialog
import cl.storeflow.warehouse.ui.theme.Rojo600
import cl.storeflow.warehouse.ui.theme.StoreFlowTheme
import cl.storeflow.warehouse.ui.theme.Verde400

@Composable
fun BusquedaProductoCard(
    busqueda: String,
    resultados: List<ProductoConStockYBodega>,
    onBusquedaChange: (String) -> Unit,
    onVerEnProductos: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colores = StoreFlowTheme.coloresExtendidos
    var mostrarScanner by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        border = BorderStroke(1.dp, colores.cardBorde)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(colores.cardGradienteTop, colores.cardGradienteBottom)))
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = onBusquedaChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buscar producto por nombre o SKU...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (busqueda.isNotEmpty()) {
                            IconButton(onClick = { onBusquedaChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                IconButton(onClick = { mostrarScanner = true }) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = "Escanear código",
                        tint = colores.paleta.primario
                    )
                }
            }

            if (mostrarScanner) {
                BarcodeScannerDialog(
                    onBarcodeDetected = { valor -> onBusquedaChange(valor) },
                    onDismiss = { mostrarScanner = false }
                )
            }

            if (busqueda.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                if (resultados.isEmpty()) {
                    Text(
                        text = "Sin resultados",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.oscuridad.textoTerciario
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        resultados.forEach { producto ->
                            ResultadoBusquedaItem(producto)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { onVerEnProductos(busqueda) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ver en Productos")
                }
            }
        }
    }
}

@Composable
private fun ResultadoBusquedaItem(producto: ProductoConStockYBodega) {
    val colores = StoreFlowTheme.coloresExtendidos
    val colorStock = if (producto.stock_actual == 0) Rojo600 else Verde400

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = colorStock, shape = CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = producto.nombre,
                style = MaterialTheme.typography.bodyMedium,
                color = colores.oscuridad.textoPrimario,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(producto.bodega_nombre)
                    if (producto.sku != null) append("  ·  SKU: ${producto.sku}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colores.oscuridad.textoTerciario,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$${producto.precio}",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.oscuridad.textoPrimario
            )
            Text(
                text = "Stock: ${producto.stock_actual}",
                style = MaterialTheme.typography.bodySmall,
                color = colores.oscuridad.textoTerciario
            )
        }
    }
}
