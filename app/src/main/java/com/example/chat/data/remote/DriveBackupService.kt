package com.example.chat.data.remote

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File as JavaFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveBackupService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = NetHttpTransport()

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account
        
        return Drive.Builder(transport, jsonFactory, credential)
            .setApplicationName("ChatApp")
            .build()
    }

    suspend fun uploadBackup(encryptedFile: JavaFile) = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: throw Exception("Not signed in to Google")
        val driveService = getDriveService(account)

        // 1. Check if backup already exists in appDataFolder
        val existingFiles = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = 'chat_backup.enc'")
            .execute()

        val fileMetadata = File().apply {
            name = "chat_backup.enc"
            parents = listOf("appDataFolder")
        }
        val mediaContent = FileContent("application/octet-stream", encryptedFile)

        if (existingFiles.files.isNotEmpty()) {
            // Update existing
            val existingFileId = existingFiles.files[0].id
            driveService.files().update(existingFileId, null, mediaContent).execute()
        } else {
            // Create new
            driveService.files().create(fileMetadata, mediaContent).execute()
        }
    }
}
