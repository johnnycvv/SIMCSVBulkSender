package com.simcsv.bulksender.data

data class AppSettings(
    val delaySeconds: Int = 10,
    val randomizeDelay: Boolean = false,
    val randomDelayMinSeconds: Int = 5,
    val randomDelayMaxSeconds: Int = 30,
    val maxRetryCount: Int = 3,
    val dailySendCap: Int = 500,
    val selectedSimSlot: Int = 0,
    val notifyOnComplete: Boolean = true
)
