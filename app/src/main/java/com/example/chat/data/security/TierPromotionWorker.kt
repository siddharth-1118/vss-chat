package com.example.chat.data.security

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.chat.core.data.PreferenceManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class TierPromotionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferenceManager: PreferenceManager,
    private val supabaseClient: SupabaseClient
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tier = preferenceManager.trustTier.first()
        if (tier >= 1) {
            return@withContext Result.success()
        }

        val registrationTime = preferenceManager.registrationTimestamp.first()
        if (registrationTime == 0L) {
            return@withContext Result.success()
        }

        // 48 hours interval in milliseconds
        val fortyEightHours = 48L * 60L * 60L * 1000L
        if (System.currentTimeMillis() - registrationTime > fortyEightHours) {
            try {
                val userId = supabaseClient.auth.currentUserOrNull()?.id
                if (userId != null) {
                    val safeUserId = userId.removePrefix("+")
                    supabaseClient.postgrest["profiles"].update(
                        mapOf("trust_tier" to 1)
                    ) {
                        filter {
                            eq("id", safeUserId)
                        }
                    }
                }
                
                // Update local configuration state
                preferenceManager.setTrustTier(1)
                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.retry()
            }
        } else {
            Result.success()
        }
    }
}
