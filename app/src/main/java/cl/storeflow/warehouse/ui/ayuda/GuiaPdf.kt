package cl.storeflow.warehouse.ui.ayuda

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import cl.storeflow.warehouse.R
import java.io.File

fun abrirGuiaPdf(context: Context) {
    try {
        val archivo = File(context.cacheDir, "StoreFlow_Guia_Rapida.pdf")
        context.resources.openRawResource(R.raw.guia_storeflow).use { input ->
            archivo.outputStream().use { output -> input.copyTo(output) }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            archivo
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No hay un visor de PDF instalado", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error al abrir la guía", Toast.LENGTH_SHORT).show()
    }
}
