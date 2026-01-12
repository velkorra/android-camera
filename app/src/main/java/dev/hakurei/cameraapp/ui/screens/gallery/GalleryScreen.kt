package dev.hakurei.cameraapp.ui.screens.gallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dev.hakurei.cameraapp.domain.model.MediaModel
import dev.hakurei.cameraapp.domain.model.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onImageClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) {
            viewModel.loadMedia()
        }
    }

    Scaffold(
        topBar = { GalleryTopBar(onBackClick = onBackClick) }
    ) { paddingValues ->
        if (permissionState.allPermissionsGranted) {
            GalleryContent(
                viewModel = viewModel,
                onImageClick = onImageClick,
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            NoGalleryPermissionScreen(
                permissionState = permissionState,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
fun GalleryContent(
    viewModel: GalleryViewModel,
    onImageClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeleteConfirmed()
        } else {
            viewModel.dismissDeleteDialog()
        }
    }

    LaunchedEffect(uiState.deleteIntentSender) {
        uiState.deleteIntentSender?.let { sender ->
            val request = IntentSenderRequest.Builder(sender).build()
            deleteLauncher.launch(request)
        }
    }

    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.mediaList.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет фото и видео", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(uiState.mediaList, key = { it.id }) { media ->
                MediaGridItem(
                    media = media, onClick = { onImageClick(media.id) },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun MediaGridItem(
    media: MediaModel,
    viewModel: GalleryViewModel,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.LightGray)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = media.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (media.type == MediaType.VIDEO) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
            )
        }
        val dateText = remember(media.dateAdded) {
            val date = Date(media.dateAdded * 1000)
            SimpleDateFormat("dd.MM", Locale.getDefault()).format(date)
        }
        Text(
            text = dateText,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryTopBar(onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text("Галерея") },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NoGalleryPermissionScreen(
    permissionState: MultiplePermissionsState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PermMedia,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "Требуется доступ к Галерее",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        Text(
            text = "Без этого мы не сможем показать ваши фото и видео.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
            color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))

        Button(onClick = {
            permissionState.launchMultiplePermissionRequest()
        }) {
            Text("Дать права")
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        }) {
            Text("Не работает? Открыть настройки")
        }
    }
}