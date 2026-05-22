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
                    val resumeTime = System.currentTimeMillis() + waitMs
                    updateNotification("Scheduled start at ${formatTime(settings.scheduledStartHour, settings.scheduledStartMinute)}")
                    withContext(Dispatchers.Main) { progressListener?.onBatchCooldown(resumeTime) }
                    delay(waitMs)
                }
            }

            while (isRunning && SmsQueue.hasPending()) {
                if (isPaused) { delay(500); continue }

                val todaySent = getTodaySentCount()
                if (settings.dailySendCap > 0 && todaySent >= settings.dailySendCap) {
                    isDailyCapReached = true
                    isRunning = false
                    updateNotification("Daily cap of ${settings.dailySendCap} reached.")
                    withContext(Dispatchers.Main) { progressListener?.onDailyCapReached(settings.dailySendCap) }
                    stopSelf()
                    return@launch
                }

                if (settings.sessionBatchLimit > 0 && sessionBatchSentCount >= settings.sessionBatchLimit) {
                    isRunning = false
                    updateNotification("Session limit of ${settings.sessionBatchLimit} reached.")
                    withContext(Dispatchers.Main) { progressListener?.onComplete() }
                    stopSelf()
                    return@launch
                }

                if (settings.batchSize > 0 && sessionBatchSentCount > 0
                    && sessionBatchSentCount % settings.batchSize == 0) {
                    val cooldownMs = settings.batchCooldownMinutes * 60_000L
                    cooldownEndsAt = System.currentTimeMillis() + cooldownMs
                    isBatchCooling = true
                    updateNotification("Batch cooldown: ${settings.batchCooldownMinutes}m")
                    withContext(Dispatchers.Main) { progressListener?.onBatchCooldown(cooldownEndsAt) }
                    delay(cooldownMs)
                    isBatchCooling = false
                }

                if (!isRunning) break

                val job = SmsQueue.poll() ?: break
                currentRecipient = job.contact.phoneNumber
                updateNotification("Sending to ${job.contact.phoneNumber} ($sentCount/$totalCount)")

                val subscriptionId = getSubscriptionId()
                sendSmsWithCallbacks(
                    phone          = job.contact.phoneNumber,
                    message        = job.contact.message,
                    name           = job.contact.name,
                    jobId          = job.id,
                    subscriptionId = subscriptionId
                )

                sessionBatchSentCount++

                withContext(Dispatchers.Main) {
                    progressListener?.onProgress(sentCount, failedCount, deliveredCount, totalCount, currentRecipient)
                }

                if (SmsQueue.hasPending()) delay(computeDelay())
            }

            withContext(Dispatchers.Main) { progressListener?.onComplete() }
            updateNotification("Complete: $sentCount sent, $deliveredCount delivered, $failedCount failed")
            stopSelf()
        }
    }

    private fun sendSmsWithCallbacks(
        phone: String,
        message: String,
        name: String,
        jobId: Long,
        subscriptionId: Int
    ) {
        if (message.isBlank()) {
            SmsQueue.markFailed(jobId, "Message is empty", settings.maxRetryCount)
            failedCount++
            SmsLogger.log(applicationContext, SmsLog(
                sessionId    = sessionId,
                phoneNumber  = phone,
                name         = name,
                message      = "",
                status       = "FAILED",
                errorMessage = "Message is empty — type a blast message before sending",
                simSlot      = settings.selectedSimSlot
            ))
            return
        }

        try {
            val smsManager = resolveSmsManager(subscriptionId)

            val baseIntent = Intent().apply {
                putExtra(EXTRA_JOB_ID,     jobId)
                putExtra(EXTRA_SESSION_ID,  sessionId)
                putExtra(EXTRA_PHONE,      phone)
                putExtra(EXTRA_NAME,       name)
                putExtra(EXTRA_MESSAGE,    message)
                putExtra(EXTRA_SIM_SLOT,   settings.selectedSimSlot)
            }

            val sentPI = PendingIntent.getBroadcast(
                this,
                jobId.toInt(),
                Intent(ACTION_SMS_SENT, null, this, SmsSentReceiver::class.java)
                    .also { it.putExtras(baseIntent) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deliveredPI = PendingIntent.getBroadcast(
                this,
                (jobId + 1_000_000).toInt(),
                Intent(ACTION_SMS_DELIVERED, null, this, SmsDeliveryReceiver::class.java)
                    .also { it.putExtras(baseIntent) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phone, null, message, sentPI, deliveredPI)
            } else {
                val sentPIs      = ArrayList(List(parts.size) { sentPI })
                val deliveredPIs = ArrayList(List(parts.size) { deliveredPI })
                smsManager.sendMultipartTextMessage(phone, null, parts, sentPIs, deliveredPIs)
            }

            SmsQueue.markSending(jobId)

        } catch (e: Exception) {
            SmsQueue.markFailed(jobId, e.message ?: "Exception", settings.maxRetryCount)
            failedCount++
            SmsLogger.log(applicationContext, SmsLog(
                sessionId    = sessionId,
                phoneNumber  = phone,
                name         = name,
                message      = message,
                status       = "FAILED",
                errorMessage = e.message ?: "Exception during send",
                simSlot      = settings.selectedSimSlot
            ))
        }
    }

    private fun resolveSmsManager(subscriptionId: Int): SmsManager {
        return if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                applicationContext.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                applicationContext.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }

    private suspend fun getTodaySentCount(): Int {
        return try {
            AppDatabase.getInstance(applicationContext).smsLogDao().getTodaySentCount()
        } catch (e: Exception) {
            0
        }
    }

    private fun computeDelay(): Long {
        return if (settings.randomizeDelay) {
            val min = settings.randomDelayMinSeconds.coerceAtLeast(1)
            val max = settings.randomDelayMaxSeconds.coerceAtLeast(min + 1)
            Random.nextLong(min * 1000L, max * 1000L)
        } else {
            settings.delaySeconds.coerceIn(1, 600) * 1000L
        }
    }

    private fun computeScheduledDelay(): Long {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.scheduledStartHour)
            set(Calendar.MINUTE,      settings.scheduledStartMinute)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "AM" else "PM"
        val h    = if (hour % 12 == 0) 12 else hour % 12
        return "%d:%02d %s".format(h, minute, ampm)
    }

    private fun getSubscriptionId(): Int {
        return try {
            val sm   = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subs = sm.activeSubscriptionInfoList
            subs?.getOrNull(settings.selectedSimSlot)?.subscriptionId
                ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        } catch (e: SecurityException) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    private fun stopSending() {
        isRunning = false
        isPaused  = false
        serviceScope.coroutineContext.cancelChildren()
        CoroutineScope(Dispatchers.Main).launch { progressListener?.onStopped() }
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Bulk SMS Sender", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Shows SMS sending progress" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openPI = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopPI = PendingIntent.getService(
            this, 1,
            Intent(this, SmsSenderService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val toggleAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val toggleLabel  = if (isPaused) "Resume" else "Pause"
        val togglePI = PendingIntent.getService(
            this, 2,
            Intent(this, SmsSenderService::class.java).apply { action = toggleAction },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bulk SMS sending in progress")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(openPI)
            .addAction(android.R.drawable.ic_media_pause, toggleLabel, togglePI)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPI)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SIMCSVBulkSender::SendLock")
            .also { it.acquire(10 * 60 * 60 * 1000L) }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
