package dev.hakurei.cameraapp.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dev.hakurei.cameraapp.domain.model.MediaModel
import dev.hakurei.cameraapp.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    suspend fun getMediaList(): List<MediaModel> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaModel>()

        mediaList.addAll(queryMediaStore(MediaType.IMAGE))
        mediaList.addAll(queryMediaStore(MediaType.VIDEO))

        mediaList.sortedByDescending { it.dateAdded }
    }

    private fun queryMediaStore(type: MediaType): List<MediaModel> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (type == MediaType.VIDEO)
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            if (type == MediaType.VIDEO)
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )

        val selection = "${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("DemoCamera")

        val result = mutableListOf<MediaModel>()

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val date = cursor.getLong(dateCol)
                val contentUri = ContentUris.withAppendedId(collection, id)

                result.add(MediaModel(id, contentUri, name, date, type))
            }
        }
        return result
    }

    suspend fun deleteMedia(uri: Uri): IntentSender? = withContext(Dispatchers.IO) {
        try {
            val rowsDeleted = context.contentResolver.delete(uri, null, null)

            if (rowsDeleted > 0) return@withContext null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return@withContext MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri)
                ).intentSender
            }

            return@withContext null

        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return@withContext MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri)
                ).intentSender
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverable = e as? android.app.RecoverableSecurityException
                if (recoverable != null) {
                    return@withContext recoverable.userAction.actionIntent.intentSender
                }
            }
            return@withContext null
        }
    }
}