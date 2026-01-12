package dev.hakurei.cameraapp.ui.screens.camera

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hakurei.cameraapp.data.repository.CameraRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CameraMode { PHOTO, VIDEO }
enum class FlashMode { OFF, ON, AUTO }

data class CameraUiState(
    val cameraMode: CameraMode = CameraMode.PHOTO,
    val flashMode: FlashMode = FlashMode.OFF,
    val isRecording: Boolean = false,
    val recordingDuration: String = "00:00",
    val showFlashAnimation: Boolean = false,
    val lastCapturedUri: Uri? = null,
    val error: String? = null
)

class CameraViewModel(
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var secondsRecorded = 0L

    private var currentZoomRatio = 1f

    fun startCamera(surfaceProvider: androidx.camera.core.Preview.SurfaceProvider, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        viewModelScope.launch {
            cameraRepository.bindCameraUseCases(lifecycleOwner, surfaceProvider)
        }
    }

    fun setCameraMode(mode: CameraMode) {
        if (_uiState.value.isRecording) return

        val resetFlash = FlashMode.OFF
        _uiState.update { it.copy(cameraMode = mode, flashMode = resetFlash) }

        cameraRepository.setFlashMode(resetFlash, mode == CameraMode.VIDEO)
    }

    fun onFlashClick() {
        val currentMode = _uiState.value.cameraMode
        val currentFlash = _uiState.value.flashMode

        val newFlashMode = if (currentMode == CameraMode.PHOTO) {
            when (currentFlash) {
                FlashMode.OFF -> FlashMode.ON
                FlashMode.ON -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.OFF
            }
        } else {
            when (currentFlash) {
                FlashMode.OFF -> FlashMode.ON
                else -> FlashMode.OFF
            }
        }

        _uiState.update { it.copy(flashMode = newFlashMode) }
        cameraRepository.setFlashMode(newFlashMode, currentMode == CameraMode.VIDEO)
    }

    fun onZoom(zoomDelta: Float) {
        currentZoomRatio = (currentZoomRatio * zoomDelta).coerceIn(1f, 10f)
        cameraRepository.setZoomRatio(currentZoomRatio)
    }

    fun onFocus(meteringPoint: androidx.camera.core.MeteringPoint) {
        cameraRepository.startFocus(meteringPoint)
    }

    fun onCaptureClick() {
        when (_uiState.value.cameraMode) {
            CameraMode.PHOTO -> takePhoto()
            CameraMode.VIDEO -> toggleRecording()
        }
    }

    private fun takePhoto() {
        triggerFlashAnimation()
        cameraRepository.takePhoto(
            onSuccess = { uri -> _uiState.update { it.copy(lastCapturedUri = uri) } },
            onError = { exc -> _uiState.update { it.copy(error = exc.localizedMessage) } }
        )
    }

    private fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        cameraRepository.startRecording { savedUri ->
            _uiState.update { it.copy(lastCapturedUri = savedUri) }
        }
        _uiState.update { it.copy(isRecording = true, recordingDuration = "00:00") }
        startTimer()
    }

    private fun stopRecording() {
        cameraRepository.stopRecording()
        stopTimer()

        _uiState.update {
            it.copy(isRecording = false, flashMode = FlashMode.OFF)
        }
    }

    fun onSwitchCameraClick(surfaceProvider: androidx.camera.core.Preview.SurfaceProvider, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        val wasRecording = _uiState.value.isRecording

        _uiState.update { it.copy(flashMode = FlashMode.OFF) }

        cameraRepository.setFlashMode(FlashMode.OFF, _uiState.value.cameraMode == CameraMode.VIDEO)

        if (wasRecording) {
            cameraRepository.pauseRecording()
        }

        cameraRepository.switchCamera(lifecycleOwner, surfaceProvider)

        if (wasRecording) {
            viewModelScope.launch {
                delay(200)
                cameraRepository.resumeRecording()
            }
        }
    }

    private fun startTimer() {
        secondsRecorded = 0
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                secondsRecorded++
                _uiState.update { it.copy(recordingDuration = formatSeconds(secondsRecorded)) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        secondsRecorded = 0
    }

    private fun formatSeconds(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "%02d:%02d".format(minutes, remainingSeconds)
    }

    private fun triggerFlashAnimation() {
        viewModelScope.launch {
            _uiState.update { it.copy(showFlashAnimation = true) }
            delay(100)
            _uiState.update { it.copy(showFlashAnimation = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}