package dev.hakurei.cameraapp.data.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ThumbnailManager(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "media_thumbnails").apply {
        if (!exists()) mkdirs()
    }

    suspend fun getThumbnail(uri: Uri, isVideo: Boolean, mediaId: Long): File {
        return withContext(Dispatchers.IO) {
            val fileName = "thumb_$mediaId.jpg"
            val file = File(cacheDir, fileName)

            if (file.exists()) {
                return@withContext file
            }

            val bitmap = if (isVideo) {
                createVideoThumbnail(uri)
            } else {
                createImageThumbnail(uri)
            }

            bitmap?.let { bmp ->
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
                }
            }

            return@withContext file
        }
    }

    private fun createVideoThumbnail(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val rawBitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null

            val rotationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val rotation = rotationString?.toFloatOrNull() ?: 0f

            return if (rotation != 0f) {
                rotateBitmap(rawBitmap, rotation)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun createImageThumbnail(uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val rawBitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return null
            inputStream?.close()

            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return rawBitmap

            val exifInterface = ExifInterface(inputStream)
            val orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            return if (rotation != 0f) {
                rotateBitmap(rawBitmap, rotation)
            } else {
                rawBitmap
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            inputStream?.close()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(
            bitmap,
            0, 0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    fun clearCache() {
        cacheDir.deleteRecursively()
    }
}