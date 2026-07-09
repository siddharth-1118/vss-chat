package com.example.chat.data.media

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.storage.Storage
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MediaUploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: Storage
) {
    /**
     * Uploads media file to private Supabase Storage bucket 'chat_media' under {user_id}/{message_id}.
     * Strips '+' from userId if present.
     */
    suspend fun uploadMedia(uri: Uri, userId: String, messageId: String): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open uri stream")
        val bytes = inputStream.use { it.readBytes() }
        
        val safeUserId = userId.removePrefix("+")
        val path = "$safeUserId/$messageId"
        
        val bucket = storage.from("chat_media")
        bucket.upload(path = path, data = bytes, upsert = true)
        
        return@withContext path
    }

    /**
     * Copies selected media to application cache dir for instant local lookup.
     */
    suspend fun cacheMediaLocally(uri: Uri, messageId: String): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open uri stream")
        
        val cacheFile = File(context.cacheDir, "media_$messageId")
        FileOutputStream(cacheFile).use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }
        return@withContext cacheFile.absolutePath
    }
}
