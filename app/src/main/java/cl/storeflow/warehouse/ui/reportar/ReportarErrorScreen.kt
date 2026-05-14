package cl.storeflow.warehouse.ui.reportar

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportarErrorScreen(
    onVolver: () -> Unit,
    viewModel: ReportarErrorViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val imagenes by viewModel.imagenes.collectAsState()
    val descripcion by viewModel.descripcion.collectAsState()

    var tienePermiso by remember { mutableStateOf(verificarPermisoGaleria(context)) }
    var abrirGaleriaPendiente by remember { mutableStateOf(false) }
    var mensajeError by remember { mutableStateOf<String?>(null) }

    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { otorgado ->
        tienePermiso = otorgado
        if (!otorgado) mensajeError = "Permiso denegado. Actívalo en Ajustes del sistema."
    }

    val galeriaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            mensajeError = null
            viewModel.agregarImagenes(uris)
        }
    }

    LaunchedEffect(tienePermiso) {
        if (tienePermiso && abrirGaleriaPendiente) {
            abrirGaleriaPendiente = false
            galeriaLauncher.launch("image/*")
        }
    }

    fun onSeleccionarImagenes() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10+: el selector del sistema no necesita permiso
                galeriaLauncher.launch("image/*")
            }
            tienePermiso -> galeriaLauncher.launch("image/*")
            else -> {
                abrirGaleriaPendiente = true
                val permiso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.READ_MEDIA_IMAGES
                else
                    Manifest.permission.READ_EXTERNAL_STORAGE
                permisoLauncher.launch(permiso)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportar problema") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "¿Qué estabas haciendo cuando ocurrió el problema?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = descripcion,
                onValueChange = viewModel::setDescripcion,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 120.dp),
                placeholder = { Text("Describe el error con el mayor detalle posible...") },
                maxLines = 8
            )

            HorizontalDivider()

            Text(
                text = "Capturas de pantalla",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedCard(
                onClick = { onSeleccionarImagenes() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (imagenes.isEmpty()) "Seleccionar imágenes de la galería"
                        else "Agregar más imágenes (${imagenes.size} seleccionada${if (imagenes.size > 1) "s" else ""})",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (mensajeError != null) {
                Text(
                    text = mensajeError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (imagenes.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(imagenes) { uri ->
                        MiniaturaImagen(
                            uri = uri,
                            onEliminar = { viewModel.eliminarImagen(uri) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { compartir(context, imagenes, descripcion) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Compartir reporte")
            }

            Text(
                text = "Selecciona WhatsApp en el menú y envía a +56 9 5759 6588.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniaturaImagen(uri: Uri, onEliminar: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) { null }
        }
    }

    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        IconButton(
            onClick = onEliminar,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .padding(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Eliminar imagen",
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun verificarPermisoGaleria(context: Context): Boolean {
    val permiso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, permiso) == PackageManager.PERMISSION_GRANTED
}

private fun compartir(context: Context, imagenes: List<Uri>, descripcion: String) {
    val texto = buildString {
        appendLine("*Reporte de problema — StoreFlow*")
        appendLine()
        if (descripcion.isNotBlank()) {
            appendLine("*¿Qué estaba pasando?*")
            appendLine(descripcion.trim())
            appendLine()
        }
        append("_StoreFlow v1.0 · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}_")
    }

    val intent: Intent = if (imagenes.isNotEmpty()) {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imagenes))
            putExtra(Intent.EXTRA_TEXT, texto)
            clipData = ClipData.newUri(context.contentResolver, null, imagenes.first()).also { cd ->
                imagenes.drop(1).forEach { uri -> cd.addItem(ClipData.Item(uri)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Enviar reporte"))
    } catch (_: ActivityNotFoundException) { }
}
