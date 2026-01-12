package dev.hakurei.cameraapp

import android.app.Application
import android.content.Context
import dev.hakurei.cameraapp.data.manager.ThumbnailManager
import dev.hakurei.cameraapp.data.manager.CameraManager
import dev.hakurei.cameraapp.data.repository.MediaRepository

class AppContainer(context: Context) {
    val mediaRepository = MediaRepository(context)
    val cameraManager = CameraManager(context)
    val thumbnailManager = ThumbnailManager(context)
}

class CameraApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}