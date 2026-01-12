package dev.hakurei.cameraapp.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hakurei.cameraapp.CameraApplication
import dev.hakurei.cameraapp.ui.screens.camera.CameraViewModel
import dev.hakurei.cameraapp.ui.screens.gallery.GalleryViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            CameraViewModel(
                cameraApplication().container.cameraManager
            )
        }

        initializer {
            GalleryViewModel(
                mediaRepository = cameraApplication().container.mediaRepository,
                thumbnailManager = cameraApplication().container.thumbnailManager
            )
        }
    }
}

fun CreationExtras.cameraApplication(): CameraApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CameraApplication)