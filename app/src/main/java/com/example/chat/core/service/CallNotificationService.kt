package com.example.chat.core.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.chat.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CallNotificationService : Service() {

    @Inject
    lateinit var signalingManager: com.example.chat.data.calling.CallSignalingManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "com.example.chat.ACTION_DECLINE_CALL") {
            val activeCall = signalingManager.activeCall.value
            if (activeCall != null) {
                val caller = activeCall.senderId
                serviceScope.launch {
                    signalingManager.setActiveCall(null)
                    signalingManager.sendSignaling(caller, "HANGUP")
                }
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val callerName = intent?.getStringExtra("callerName") ?: "Incoming Call"
        val channelId = "call_channel"

        val acceptIntent = Intent(this, MainActivity::class.java).apply {
            this.action = "com.example.chat.ACTION_ACCEPT_CALL"
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this, 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            this.action = "com.example.chat.ACTION_INCOMING_CALL"
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 2, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, CallNotificationService::class.java).apply {
            this.action = "com.example.chat.ACTION_DECLINE_CALL"
        }
        val declinePendingIntent = PendingIntent.getService(
            this, 1, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling you...")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Decline", declinePendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "call_channel",
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads up notifications for incoming audio/video calls"
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
