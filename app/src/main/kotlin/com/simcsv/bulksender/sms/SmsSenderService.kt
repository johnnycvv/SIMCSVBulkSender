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
import com.simcsv.bulksender.R
import com.simcsv.bulksender.data.AppSettings
import com.simcsv.bulksender.data.SmsLog
import com.simcsv.bulksender.logger.SmsLogger
import kotlinx.coroutines.*
import kotlin.random.Random

class SmsSenderService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_RESUME"
        const val ACTION_RESUME = "ACTION_PAUSE"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SETTINGS = "EXTRA_SETTINGS"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "SMS_SENDER_CHANNEL"
        const val SMS_SENT_ACTION = "com.simcsv.SMS_SENT"
        const val SMS_DELIVERED_ACTION = "com.simcsv.SMS_DELIVERED"

        var isRunning = false
        var isPaused = false
        var currentRecipient = ""
        var sentCount = 0
        var failedCount = 0
        var totalCount = 0
        var progressListener: ProgressListener? = null

        interface ProgressListener {
            fun onProgress(sent: Int, failed: Int, total: Int, current: String)
            fun onComplete()
            fun onStopped()
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var settings = AppSettings()
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
                settings = intent.getSerializableExtra(EXTRA_SETTINGS) as? AppSettings ?: AppSettings()
                sessionId = System.currentTimeMillis().toString()
                startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
                startSending()
            }
            ACTION_PAUSE -> isPaused = true
            ACTION_RESUME -> isPaused = false
            ACTION_STOP -> stopSending()
        }
        return START_NOT_STICKY
    }

    private fun startSending() {
        isRunning = true
        isPaused = false
        sentCount = 0
        failedCount = 0
        totalCount = SmsQueue.totalCount

        serviceScope.launch {
            while (isRunning && SmsQueue.hasPending()) {
                if (isPaused) {
                    delay(500)
                    continue
                }

                val job = SmsQueue.poll() ?: break
                currentRecipient = job.contact.phoneNumber

                updateNotification("Sending to ${job.contact.phoneNumber} ($sentCount/$totalCount)")

                val subscriptionId = getSubscriptionId()
                val success = sendSms(job.contact.phoneNumber, job.contact.message, subscriptionId)

                if (success) {
                    SmsQueue.markSent(job.id)
                    sentCount++
                    SmsLogger.log(
                        applicationContext,
                        SmsLog(
                            sessionId = sessionId,
                            phoneNumber = job.contact.phoneNumber,
                            name = job.contact.name,
                            message = job.contact.message,
                            status = "SENT",
                            simSlot = settings.selectedSimSlot
                        )
                    )
                } else {
                    SmsQueue.markFailed(job.id, "Send failed", settings.maxRetryCount)
                    if (job.retryCount >= settings.maxRetryCount) {
                        failedCount++
                        SmsLogger.log(
                            applicationContext,
                            SmsLog(
                                sessionId = sessionId,
                                phoneNumber = job.contact.phoneNumber,
                                name = job.contact.name,
                                message = job.contact.message,
                                status = "FAILED",
                                errorMessage = "Max retries exceeded",
                                simSlot = settings.selectedSimSlot
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    progressListener?.onProgress(sentCount, failedCount, totalCount, currentRecipient)
                }

                val delayMs = computeDelay()
                delay(delayMs)
            }

            withContext(Dispatchers.Main) {
                progressListener?.onComplete()
            }
            updateNotification("Sending complete: $sentCount sent, $failedCount failed")
            stopSelf()
        }
    }

    private fun sendSms(phone: String, message: String, subscriptionId: Int): Boolean {
        return try {
            val smsManager = if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
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

            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phone, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun computeDelay(): Long {
        return if (settings.randomizeDelay) {
            val min = settings.randomDelayMinSeconds.coerceAtLeast(5)
            val max = settings.randomDelayMaxSeconds.coerceAtLeast(min + 1)
            Random.nextLong(min * 1000L, max * 1000L)
        } else {
            settings.delaySeconds.coerceIn(5, 600) * 1000L
        }
    }

    private fun getSubscriptionId(): Int {
        return try {
            val subscriptionManager =
                getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subs = subscriptionManager.activeSubscriptionInfoList
            val slot = settings.selectedSimSlot
            subs?.getOrNull(slot)?.subscriptionId ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        } catch (e: SecurityException) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    private fun stopSending() {
        isRunning = false
        isPaused = false
        serviceScope.coroutineContext.cancelChildren()
        CoroutineScope(Dispatchers.Main).launch {
            progressListener?.onStopped()
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bulk SMS Sender",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows SMS sending progress"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, SmsSenderService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = Intent(this, SmsSenderService::class.java).apply { action = ACTION_PAUSE }
        val pendingPause = PendingIntent.getService(
            this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bulk SMS sending in progress")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pendingPause)
            .addAction(android.R.drawable.ic_delete, "Stop", pendingStop)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SIMCSVBulkSender::SendLock")
        wakeLock?.acquire(10 * 60 * 60 * 1000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
