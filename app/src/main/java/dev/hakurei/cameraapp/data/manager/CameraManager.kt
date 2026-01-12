package dev.hakurei.cameraapp.data.manager

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dev.hakurei.cameraapp.ui.screens.camera.FlashMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class CameraManager(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var camera: Camera? = null

    var currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        private set

    suspend fun initCamera(): ProcessCameraProvider {
        return withContext(Dispatchers.Main) {
            if (cameraProvider == null) {
                cameraProvider = ProcessCameraProvider.Companion.getInstance(context).await()
            }
            cameraProvider!!
        }
    }

    suspend fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        val provider = initCamera()

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val qualitySelector = QualitySelector.from(
            Quality.FHD,
            FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST)
        )

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            provider.unbindAll()

            try {
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    currentCameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )
                Log.d("CameraRepo", "Bind Success: All use cases")
            } catch (e: Exception) {
                Log.e("CameraRepo", "Bind Failed (All). Trying Photo only...", e)

                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    currentCameraSelector,
                    preview,
                    imageCapture
                )
                Log.w("CameraRepo", "Bind Partial: VIDEO DISABLED due to hardware limits")
            }

        } catch (e: Exception) {
            Log.e("CameraRepo", "CRITICAL: Camera bind failed completely", e)
        }
    }

    fun setFlashMode(flashMode: FlashMode, isVideoMode: Boolean) {
        val cam = camera ?: return
        if (isVideoMode) {
            val enableTorch = flashMode == FlashMode.ON
            cam.cameraControl.enableTorch(enableTorch)
        } else {
            cam.cameraControl.enableTorch(false)
            val mode = when (flashMode) {
                FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
            imageCapture?.flashMode = mode
        }
    }

    fun setZoomRatio(ratio: Float) { camera?.cameraControl?.setZoomRatio(ratio) }

    fun startFocus(meteringPoint: MeteringPoint) {
        val action = FocusMeteringAction.Builder(meteringPoint).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()
            val preview = Preview.Builder().build().apply { setSurfaceProvider(surfaceProvider) }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                currentCameraSelector,
                preview,
                imageCapture,
                videoCapture
            )
        } catch (e: Exception) {
            Log.e("CameraRepo", "Switch camera failed", e)
            try {
                camera = provider.bindToLifecycle(lifecycleOwner, currentCameraSelector, Preview.Builder().build().apply { setSurfaceProvider(surfaceProvider) }, imageCapture)
            } catch (e2: Exception) { e2.printStackTrace() }
        }
    }

    fun takePhoto(onSuccess: (Uri) -> Unit, onError: (Exception) -> Unit) {
        val capture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DemoCamera")
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "DemoCamera")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val file = File(appDir, "$name.jpg")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { onSuccess(it) }
                }
                override fun onError(exc: ImageCaptureException) {
                    onError(exc)
                }
            }
        )
    }

    fun startRecording(onVideoSaved: (Uri) -> Unit) {
        val capture = videoCapture ?: run {
            Log.e("CameraRepo", "Recording failed: VideoCapture is null")
            return
        }
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DemoCamera")
            } else {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val appDir = File(moviesDir, "DemoCamera")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val file = File(appDir, "$name.mp4")
                put(MediaStore.Video.Media.DATA, file.absolutePath)
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        var pendingRecording = capture.output
            .prepareRecording(context, mediaStoreOutput)
            .asPersistentRecording()

        val audioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )

        if (audioPermission == PackageManager.PERMISSION_GRANTED) {
            pendingRecording = pendingRecording.withAudioEnabled()
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (!event.hasError()) {
                    onVideoSaved(event.outputResults.outputUri)
                } else {
                    activeRecording = null
                }
            }
        }
    }

    fun pauseRecording() { activeRecording?.pause() }
    fun resumeRecording() { activeRecording?.resume() }
    fun stopRecording() { activeRecording?.stop(); activeRecording = null; camera?.cameraControl?.enableTorch(false) }
}