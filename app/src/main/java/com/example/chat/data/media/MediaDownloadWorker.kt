package com.example.chat.data.media

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.chat.data.local.dao.MessageDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.storage.Storage
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class MediaDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val storage: Storage,
    private val messageDao: MessageDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val remoteUrl = inputData.getString("remoteUrl") ?: return@withContext Result.failure()
        val messageId = inputData.getString("messageId") ?: return@withContext Result.failure()

        try {
            val bucket = storage.from("chat_media")
            val bytes = bucket.downloadAuthenticated(remoteUrl)
            
            val localFile = File(applicationContext.filesDir, "media_$messageId")
            FileOutputStream(localFile).use { output ->
                output.write(bytes)
            }
            
            // Update local DB
            messageDao.updateMessageMediaUrl(messageId, localFile.absolutePath)
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
