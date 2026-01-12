package dev.hakurei.cameraapp.domain.model

import android.net.Uri

enum class MediaType {
    IMAGE, VIDEO
}

data class MediaModel(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val type: MediaType
)