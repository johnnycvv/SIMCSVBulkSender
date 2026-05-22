package com.simcsv.bulksender.data

import java.io.Serializable

data class AppSettings(
    val delaySeconds: Int = 10,
    val randomizeDelay: Boolean = false,
    val randomDelayMinSeconds: Int = 5,
    val randomDelayMaxSeconds: Int = 30,
    val maxRetryCount: Int = 3,

    val dailySendCap: Int = 500,
    val sessionBatchLimit: Int = 0,

    val batchSize: Int = 0,
    val batchCooldownMinutes: Int = 5,

    val scheduledStartEnabled: Boolean = false,
    val scheduledStartHour: Int = 9,
    val scheduledStartMinute: Int = 0,

    val selectedSimSlot: Int = 0,
    val notifyOnComplete: Boolean = true
) : Serializable
