package com.example.chat.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.chat.core.security.CryptoManager
import com.example.chat.data.local.ChatDatabase
import com.example.chat.data.remote.DriveBackupService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: ChatDatabase,
    private val driveService: DriveBackupService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val context = applicationContext
            val dbFile = context.getDatabasePath(ChatDatabase.DATABASE_NAME)
            
            if (!dbFile.exists()) return@withContext Result.failure()

            // 1. Checkpoint the database to ensure all WAL data is committed to the main .db file
            database.query("PRAGMA checkpoint(FULL)", null).close()
            
            // 2. Encrypt the file
            val cryptoManager = CryptoManager()
            val encryptedFile = File(context.cacheDir, "chat_backup.enc")
            cryptoManager.encryptFile(dbFile, encryptedFile)

            // 3. Upload to Google Drive
            driveService.uploadBackup(encryptedFile)

            // 4. Cleanup
            encryptedFile.delete()

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val BACKUP_WORK_NAME = "periodic_chat_backup"

        fun enqueuePeriodicBackup(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                BACKUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun triggerImmediateBackup(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "immediate_backup",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
