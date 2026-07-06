package com.example.chat.core.security

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object FingerprintUtils {
    /**
     * Generates a 32-character hardware fingerprint hash (Visitor ID).
     * In a production environment, this should incorporate more hardware signals.
     */
    fun getVisitorId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val buildInfo = android.os.Build.BOARD + android.os.Build.BRAND + android.os.Build.DEVICE +
                android.os.Build.DISPLAY + android.os.Build.FINGERPRINT + android.os.Build.HOST +
                android.os.Build.ID + android.os.Build.MANUFACTURER + android.os.Build.MODEL +
                android.os.Build.PRODUCT + android.os.Build.TAGS + android.os.Build.TYPE +
                android.os.Build.USER
        
        val rawId = androidId + buildInfo
        return rawId.sha256().take(32)
    }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
