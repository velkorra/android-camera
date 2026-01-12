package dev.hakurei.cameraapp.ui.screens.gallery

import android.app.Activity
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hakurei.cameraapp.data.manager.ThumbnailManager
import dev.hakurei.cameraapp.data.repository.MediaRepository
import dev.hakurei.cameraapp.domain.model.MediaModel
import dev.hakurei.cameraapp.domain.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class GalleryUiState(
    val mediaList: List<MediaModel> = emptyList(),
    val isLoading: Boolean = false,
    val deleteIntentSender: IntentSender? = null
)

class GalleryViewModel(
    private val mediaRepository: MediaRepository,
    private val thumbnailManager: ThumbnailManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val list = mediaRepository.getMediaList()
            _uiState.update { it.copy(mediaList = list, isLoading = false) }
        }
    }

    suspend fun getCachedThumbnail(media: MediaModel): File {
        return thumbnailManager.getThumbnail(
            uri = media.uri,
            isVideo = (media.type == MediaType.VIDEO),
            mediaId = media.id
        )
    }

    fun deleteMedia(media: MediaModel) {
        viewModelScope.launch {
            val intentSender = mediaRepository.deleteMedia(media.uri)
            if (intentSender != null) {
                _uiState.update { it.copy(deleteIntentSender = intentSender) }
            } else {
                loadMedia()
            }
        }
    }

    fun onDeleteConfirmed() {
        _uiState.update { it.copy(deleteIntentSender = null) }
        loadMedia()
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(deleteIntentSender = null) }
    }
}