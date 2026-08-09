package cl.storeflow.warehouse.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val ColorLinternaApagada = Color(0xFFF0921E)
private val ColorLinternaEncendida = Color(0xFFFFD500)

@Composable
fun BarcodeScannerDialog(
    onBarcodeDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var linternaActiva by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    lifecycleOwner = lifecycleOwner,
                    onBarcodeDetected = { value ->
                        onBarcodeDetected(value)
                        onDismiss()
                    },
                    onCameraReady = { camera = it }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Se necesita permiso de cámara para escanear",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cerrar scanner",
                        tint = Color.White
                    )
                }
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (linternaActiva) ColorLinternaEncendida else ColorLinternaApagada)
                            .clickable {
                                linternaActiva = !linternaActiva
                                camera?.cameraControl?.enableTorch(linternaActiva)
                            }
                    ) {
                        Icon(
                            imageVector = if (linternaActiva) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = if (linternaActiva) "Apagar linterna" else "Encender linterna",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Text(
                    text = "Apunta al código de barras o QR",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onBarcodeDetected: (String) -> Unit,
    onCameraReady: (Camera) -> Unit
) {
    val context = LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    // AtomicBoolean porque el analizador corre en un hilo separado
    val detectedOnce = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor, BarcodeAnalyzer(barcodeScanner, detectedOnce, onBarcodeDetected))
                    }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                    onCameraReady(camera)
                } catch (e: Exception) {
                    Timber.e(e, "SCAN: error al iniciar cámara")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private class BarcodeAnalyzer(
    private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    private val detectedOnce: AtomicBoolean,
    private val onDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        if (detectedOnce.get()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull()?.rawValue
                if (value != null && detectedOnce.compareAndSet(false, true)) {
                    onDetected(value)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
