package com.simcsv.bulksender

import android.app.Application
import android.content.SharedPreferences
import com.simcsv.bulksender.data.AppSettings

class BulkSenderApp : Application() {

    lateinit var prefs: SharedPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("simcsv_prefs", MODE_PRIVATE)
        instance = this
    }

    fun loadSettings(): AppSettings = AppSettings(
        delaySeconds = prefs.getInt("delay_seconds", 10),
        randomizeDelay = prefs.getBoolean("randomize_delay", false),
        randomDelayMinSeconds = prefs.getInt("random_delay_min", 5),
        randomDelayMaxSeconds = prefs.getInt("random_delay_max", 30),
        maxRetryCount = prefs.getInt("max_retry", 3),
        dailySendCap = prefs.getInt("daily_cap", 500),
        selectedSimSlot = prefs.getInt("sim_slot", 0),
        notifyOnComplete = prefs.getBoolean("notify_complete", true)
    )

    fun saveSettings(settings: AppSettings) {
        prefs.edit().apply {
            putInt("delay_seconds", settings.delaySeconds)
            putBoolean("randomize_delay", settings.randomizeDelay)
            putInt("random_delay_min", settings.randomDelayMinSeconds)
            putInt("random_delay_max", settings.randomDelayMaxSeconds)
            putInt("max_retry", settings.maxRetryCount)
            putInt("daily_cap", settings.dailySendCap)
            putInt("sim_slot", settings.selectedSimSlot)
            putBoolean("notify_complete", settings.notifyOnComplete)
            apply()
        }
    }

    companion object {
        lateinit var instance: BulkSenderApp
            private set
    }
}
