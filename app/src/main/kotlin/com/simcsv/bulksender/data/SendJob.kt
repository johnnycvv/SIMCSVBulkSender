package com.simcsv.bulksender.data

enum class SendStatus {
    PENDING,
    SENDING,
    SENT,
    DELIVERED,
    FAILED,
    RETRYING
}

data class SendJob(
    val id: Long,
    val contact: Contact,
    var status: SendStatus = SendStatus.PENDING,
    var retryCount: Int = 0,
    var errorMessage: String = "",
    var sentAt: Long = 0L,
    var deliveredAt: Long = 0L
)
