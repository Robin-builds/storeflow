package cl.storeflow.warehouse.ui.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.storeflow.warehouse.domain.model.MomentosAviso

private data class TextoAviso(val titulo: String, val detalle: String)

private const val PARA_RECUPERAR = "Para recuperar tu plan, contacta a StoreFlow."

// Informa el estado del plan, sin precios, enlaces ni llamados a comprar (FR-027).
private fun textoAviso(momento: String): TextoAviso? = when (momento) {
    MomentosAviso.DIA_0 -> TextoAviso(
        "Comenzó tu período de prueba",
        "Puedes revisar su estado en Configuración, en Mi plan."
    )
    MomentosAviso.MENOS_7 -> TextoAviso(
        "Tu período de prueba termina en 7 días",
        "Al terminar, lo que exceda el plan gratuito quedará en solo lectura. No se borrará nada."
    )
    MomentosAviso.MENOS_1 -> TextoAviso(
        "Tu período de prueba termina mañana",
        "Lo que exceda el plan gratuito quedará en solo lectura. No se borrará nada."
    )
    MomentosAviso.TERMINO -> TextoAviso(
        "Terminó tu período de prueba",
        "Tu empresa pasó al plan gratuito y tus datos siguen guardados. $PARA_RECUPERAR"
    )
    MomentosAviso.MAS_2 -> TextoAviso(
        "Tu empresa está en el plan gratuito",
        "Lo que excede el plan sigue guardado en solo lectura. $PARA_RECUPERAR"
    )
    else -> null
}

@Composable
fun AvisoPlanBanner(
    momento: String,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val texto = textoAviso(momento) ?: return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp)
            ) {
                Text(texto.titulo, style = MaterialTheme.typography.titleSmall)
                Text(texto.detalle, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onCerrar) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar aviso")
            }
        }
    }
}
