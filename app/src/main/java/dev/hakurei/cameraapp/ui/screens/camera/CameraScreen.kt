package dev.hakurei.cameraapp.ui.screens.camera

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        list
    }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = permissionsToRequest
    )

    if (!permissionsState.allPermissionsGranted) {
        if (permissionsState.permissions.first { it.permission == Manifest.permission.CAMERA }.status.isGranted) {
            CameraContent(viewModel, onGalleryClick)
        } else {
            NoPermissionScreen(onRequestPermission = { permissionsState.launchMultiplePermissionRequest() })
        }
    } else {
        CameraContent(viewModel, onGalleryClick)
    }
}

@Composable
fun CameraContent(
    viewModel: CameraViewModel,
    onGalleryClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val scope = rememberCoroutineScope()

    var previewView: PreviewView? by remember { mutableStateOf(null) }

    var focusOffset by remember { mutableStateOf<Offset?>(null) }
    val focusAlpha = remember { Animatable(0f) }

    LaunchedEffect(previewView) {
        previewView?.let { view ->
            viewModel.startCamera(view.surfaceProvider, lifecycleOwner)
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }.also { previewView = it }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        viewModel.onZoom(zoom)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        focusOffset = offset

                        scope.launch {
                            focusAlpha.snapTo(1f)
                            delay(1000)
                            focusAlpha.animateTo(0f, tween(500))
                        }

                        previewView?.let { view ->
                            val factory = view.meteringPointFactory
                            val point = factory.createPoint(offset.x, offset.y)
                            viewModel.onFocus(point)
                        }
                    }
                }
        )

        if (focusOffset != null && focusAlpha.value > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val size = 60.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = focusAlpha.value),
                    radius = size / 2,
                    center = focusOffset!!,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        if (uiState.showFlashAnimation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.onFlashClick() }) {
                Icon(
                    imageVector = getFlashIcon(uiState.flashMode, uiState.cameraMode),
                    contentDescription = "Flash",
                    tint = if (uiState.flashMode == FlashMode.OFF) Color.White else Color.Yellow
                )
            }

            if (uiState.isRecording) {
                Text(
                    text = uiState.recordingDuration,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                    )
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (!uiState.isRecording) {
                ModeSelector(
                    currentMode = uiState.cameraMode,
                    onModeSelected = { viewModel.setCameraMode(it) }
                )
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isRecording) {
                    Spacer(modifier = Modifier.size(50.dp))
                } else {
                    GalleryButton(
                        lastUri = uiState.lastCapturedUri,
                        onClick = onGalleryClick
                    )
                }

                ShutterButton(
                    mode = uiState.cameraMode,
                    isRecording = uiState.isRecording,
                    onClick = { viewModel.onCaptureClick() }
                )

                IconButton(
                    onClick = {
                        previewView?.let {
                            viewModel.onSwitchCameraClick(it.surfaceProvider, lifecycleOwner)
                        }
                    },
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModeSelector(currentMode: CameraMode, onModeSelected: (CameraMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "ФОТО",
            color = if (currentMode == CameraMode.PHOTO) Color.White else Color.Gray,
            fontWeight = if (currentMode == CameraMode.PHOTO) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
            modifier = Modifier
                .clickable { onModeSelected(CameraMode.PHOTO) }
                .padding(8.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "ВИДЕО",
            color = if (currentMode == CameraMode.VIDEO) Color.White else Color.Gray,
            fontWeight = if (currentMode == CameraMode.VIDEO) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
            modifier = Modifier
                .clickable { onModeSelected(CameraMode.VIDEO) }
                .padding(8.dp)
        )
    }
}

@Composable
fun ShutterButton(mode: CameraMode, isRecording: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .border(4.dp, Color.White, CircleShape))
        val color = if (mode == CameraMode.VIDEO) Color.Red else Color.White
        val shape = if (isRecording) RoundedCornerShape(12.dp) else CircleShape
        val size = if (isRecording) 30.dp else 60.dp
        Box(modifier = Modifier
            .size(size)
            .clip(shape)
            .background(color))
    }
}

@Composable
fun GalleryButton(lastUri: android.net.Uri?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Color.DarkGray)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (lastUri != null) {
            AsyncImage(
                model = lastUri,
                contentDescription = "Gallery",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Gallery",
                tint = Color.White
            )
        }
    }
}

@Composable
fun NoPermissionScreen(onRequestPermission: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Videocam,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Камере нужны разрешения",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = "Без камеры и работать не будет.",
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(24.dp))

        Button(onClick = onRequestPermission) {
            Text("Дать права")
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }) {
            Text(
                "Ничего не происходит? Открыть настройки",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun getFlashIcon(flashMode: FlashMode, cameraMode: CameraMode): ImageVector {
    if (cameraMode == CameraMode.VIDEO) return if (flashMode == FlashMode.ON) Icons.Default.FlashOn else Icons.Default.FlashOff
    return when (flashMode) {
        FlashMode.OFF -> Icons.Default.FlashOff
        FlashMode.ON -> Icons.Default.FlashOn
        FlashMode.AUTO -> Icons.Default.FlashAuto
    }
}