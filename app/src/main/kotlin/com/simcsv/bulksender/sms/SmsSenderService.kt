package com.simcsv.bulksender.sms

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import com.simcsv.bulksender.MainActivity
import com.simcsv.bulksender.data.AppDatabase
import com.simcsv.bulksender.data.AppSettings
import com.simcsv.bulksender.data.SmsLog
import com.simcsv.bulksender.logger.SmsLogger
import kotlinx.coroutines.*
import java.util.Calendar
import kotlin.random.Random

class SmsSenderService : Service() {

    companion object {
        const val ACTION_START   = "ACTION_START"
        const val ACTION_PAUSE   = "ACTION_PAUSE"
        const val ACTION_RESUME  = "ACTION_RESUME"
        const val ACTION_STOP    = "ACTION_STOP"
        const val EXTRA_SETTINGS = "EXTRA_SETTINGS"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "SMS_SENDER_CHANNEL"

        const val ACTION_SMS_SENT      = "com.simcsv.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.simcsv.SMS_DELIVERED"
        const val EXTRA_JOB_ID         = "jobId"
        const val EXTRA_SESSION_ID     = "sessionId"
        const val EXTRA_PHONE          = "phone"
        const val EXTRA_NAME           = "name"
        const val EXTRA_MESSAGE        = "message"
        const val EXTRA_SIM_SLOT       = "simSlot"

        var isRunning         = false
        var isPaused          = false
        var isDailyCapReached = false
        var isBatchCooling    = false
        var currentRecipient  = ""
        var sentCount         = 0
        var failedCount       = 0
        var deliveredCount    = 0
        var totalCount        = 0
        var sessionBatchSentCount = 0
        var cooldownEndsAt    = 0L
        var progressListener: ProgressListener? = null

        var settings: AppSettings = AppSettings()
            private set

        interface ProgressListener {
            fun onProgress(sent: Int, failed: Int, delivered: Int, total: Int, current: String)
            fun onDailyCapReached(cap: Int)
            fun onBatchCooldown(resumeAt: Long)
            fun onComplete()
            fun onStopped()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var sessionId = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                @Suppress("DEPRECATION")
                settings  = intent.getSerializableExtra(EXTRA_SETTINGS) as? AppSettings ?: AppSettings()
                sessionId = System.currentTimeMillis().toString()
                startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
                startSending()
            }
            ACTION_PAUSE  -> { isPaused = true;  updateNotification("Paused") }
            ACTION_RESUME -> { isPaused = false; updateNotification("Resuming...") }
            ACTION_STOP   -> stopSending()
        }
        return START_NOT_STICKY
    }

    private fun startSending() {
        isRunning             = true
        isPaused              = false
        isDailyCapReached     = false
        isBatchCooling        = false
        sentCount             = 0
        failedCount           = 0
        deliveredCount        = 0
        sessionBatchSentCount = 0
        totalCount            = SmsQueue.totalCount

        serviceScope.launch {
            if (settings.scheduledStartEnabled) {
                val waitMs = computeScheduledDelay()
                if (waitMs > 0) {
                    val resumeTime
