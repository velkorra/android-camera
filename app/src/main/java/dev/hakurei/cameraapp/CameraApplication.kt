package dev.hakurei.cameraapp

import android.app.Application
import android.content.Context
import dev.hakurei.cameraapp.data.manager.ThumbnailManager
import dev.hakurei.cameraapp.data.repository.CameraRepository
import dev.hakurei.cameraapp.data.repository.MediaRepository

class AppContainer(context: Context) {
    val mediaRepository = MediaRepository(context)
    val cameraRepository = CameraRepository(context)
    val thumbnailManager = ThumbnailManager(context)
}

class CameraApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}