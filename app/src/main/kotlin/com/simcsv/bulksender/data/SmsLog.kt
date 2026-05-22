package com.simcsv.bulksender.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogStatus { SENT, FAILED, DELIVERED, PENDING }

@Entity(tableName = "sms_logs")
data class SmsLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val phoneNumber: String,
    val name: String,
    val message: String,
    val status: String,
    val errorMessage: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val simSlot: Int = 0
)
