package com.simcsv.bulksender.sms

import com.simcsv.bulksender.data.Contact
import com.simcsv.bulksender.data.SendJob
import com.simcsv.bulksender.data.SendStatus
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

object SmsQueue {

    private val queue = ConcurrentLinkedQueue<SendJob>()
    private val idCounter = AtomicLong(0)
    private val allJobs = mutableListOf<SendJob>()

    val pendingCount: Int get() = queue.count { it.status == SendStatus.PENDING }
    val totalCount: Int get() = allJobs.size
    val sentCount: Int get() = allJobs.count { it.status == SendStatus.SENT || it.status == SendStatus.DELIVERED }
    val failedCount: Int get() = allJobs.count { it.status == SendStatus.FAILED }
    val deliveredCount: Int get() = allJobs.count { it.status == SendStatus.DELIVERED }

    fun load(contacts: List<Contact>) {
        queue.clear()
        allJobs.clear()
        contacts.forEach { contact ->
            val job = SendJob(id = idCounter.incrementAndGet(), contact = contact)
            queue.add(job)
            allJobs.add(job)
        }
    }

    fun poll(): SendJob? = queue.firstOrNull { it.status == SendStatus.PENDING }
        ?.also { it.status = SendStatus.SENDING }

    fun markSent(jobId: Long) {
        findJob(jobId)?.apply {
            status = SendStatus.SENT
            sentAt = System.currentTimeMillis()
        }
    }

    fun markDelivered(jobId: Long) {
        findJob(jobId)?.apply {
            status = SendStatus.DELIVERED
            deliveredAt = System.currentTimeMillis()
        }
    }

    fun markFailed(jobId: Long, error: String, maxRetry: Int) {
        findJob(jobId)?.apply {
            retryCount++
            errorMessage = error
            status = if (retryCount < maxRetry) {
                SendStatus.PENDING
            } else {
                SendStatus.FAILED
            }
        }
    }

    fun getAllJobs(): List<SendJob> = allJobs.toList()

    fun clear() {
        queue.clear()
        allJobs.clear()
        idCounter.set(0)
    }

    fun isEmpty(): Boolean = queue.none { it.status == SendStatus.PENDING }

    fun hasPending(): Boolean = queue.any { it.status == SendStatus.PENDING }

    private fun findJob(id: Long): SendJob? = allJobs.find { it.id == id }
}
