package com.personal.tube.util

import android.content.Context
import android.os.Environment
import com.personal.tube.data.local.entities.DownloadEntity
import com.personal.tube.data.model.VideoItem
import com.personal.tube.data.model.VideoStreamFormat
import com.personal.tube.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object DownloadHelper {

    private val client = OkHttpClient()

    suspend fun downloadVideo(
        context: Context,
        video: VideoItem,
        format: VideoStreamFormat,
        repository: VideoRepository,
        onProgress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val extension = if (format.isAudioOnly) "m4a" else "mp4"
            val filename = "${FormatUtils.sanitizeFilename(video.title)}_${video.id}.$extension"
            val dir = context.getExternalFilesDir(if (format.isAudioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)
                ?: context.filesDir
            val destFile = File(dir, filename)

            val request = Request.Builder().url(format.url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesCopied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (totalBytes > 0) {
                                val progress = ((bytesCopied * 100) / totalBytes).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            // Save download entity in Room DB
            val entity = DownloadEntity(
                videoId = video.id,
                title = video.title,
                channelTitle = video.channelTitle,
                localFilePath = destFile.absolutePath,
                thumbnailUrl = video.thumbnailUrl,
                durationFormatted = video.durationFormatted,
                fileSizeFormatted = FormatUtils.formatBytes(destFile.length()),
                isAudioOnly = format.isAudioOnly
            )
            repository.saveDownload(entity)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
