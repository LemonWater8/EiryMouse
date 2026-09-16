package com.example.diazymouse.bhid.hid

import com.example.diazymouse.bhid.logging.HidLogger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Foreground owner for the independently implemented HID session. */
class HidSessionService : Service() {
    companion object {
        private const val CHANNEL_ID = "hid_v15_session"
        private const val NOTIFICATION_ID = 9001
    }

    inner class LocalBinder : Binder() {
        fun service(): HidSessionService = this@HidSessionService
    }

    private val binder = LocalBinder()
    lateinit var logger: HidLogger
        private set
    lateinit var session: HidSessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("EiryMouse HID")
                .setContentText("Bluetooth HID session is active")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
        logger = HidLogger(this)
        session = HidSessionManager(this, logger)
        logger.log("v15.0 SERVICE created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (::session.isInitialized) session.close()
        if (::logger.isInitialized) {
            logger.log("v15.0 SERVICE destroyed")
            logger.close()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "EiryMouse HID session",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
