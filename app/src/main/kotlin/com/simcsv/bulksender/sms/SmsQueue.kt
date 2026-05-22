package com.simcsv.bulksender.sms

import com.simcsv.bulksender.data.AppSettings
import com.simcsv.bulksender.data.Contact
import com.simcsv.bulksender.data.SendJob
import com.simcsv.bulksender.data.SendStatus
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

object SmsQueue {

    private val queue     = ConcurrentLinkedQueue<SendJob>()
    private val allJobs   = mutableListOf<SendJob>()
    private val idCounter = AtomicLong(0)

    var settings: AppSettings = AppSettings()
        private set

    val pendingCount:   Int get() = allJobs.count { it.status == SendStatus.PENDING }
    val totalCount:     Int get() = allJobs.size
    val sentCount:      Int get() = allJobs.count { it.status == SendStatus.SENT || it.status == SendStatus.DELIVERED }
    val failedCount:    Int get() = allJobs.count { it.status == SendStatus.FAILED }
    val deliveredCount: Int get() = allJobs.count { it.status == SendStatus.DELIVERED }

    @Synchronized
    fun load(contacts: List<Contact>, appSettings: AppSettings = AppSettings()) {
        queue.clear()
        allJobs.clear()
        idCounter.set(0)
        settings = appSettings
        contacts.forEach { contact ->
            val job = SendJob(id = idCounter.incrementAndGet(), contact = contact)
            queue.add(job)
            allJobs.add(job)
        }
    }

    @Synchronized
    fun poll(): SendJob? =
        allJobs.firstOrNull { it.status == SendStatus.PENDING }
            ?.also { it.status = SendStatus.SENDING }

    fun markSending(jobId: Long) {
        findJob(jobId)?.status = SendStatus.SENDING
    }

    fun markSent(jobId: Long) {
        findJob(jobId)?.apply {
            status = SendStatus.SENT
            sentAt = System.currentTimeMillis()
        }
    }

    fun markDelivered(jobId: Long) {
        findJob(jobId)?.apply {
            status      = SendStatus.DELIVERED
            deliveredAt = System.currentTimeMillis()
        }
    }

    fun markFailed(jobId: Long, error: String, maxRetry: Int) {
        findJob(jobId)?.apply {
            retryCount++
            errorMessage = error
            status = if (retryCount < maxRetry) SendStatus.PENDING else SendStatus.FAILED
        }
    }

    fun getAllJobs(): List<SendJob> = allJobs.toList()

    fun clear() {
        queue.clear()
        allJobs.clear()
        idCounter.set(0)
    }

    fun hasPending(): Boolean = allJobs.any { it.status == SendStatus.PENDING }

    private fun findJob(id: Long): SendJob? = allJobs.find { it.id == id }
}
