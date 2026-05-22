package com.simcsv.bulksender.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra("jobId", -1L)
        if (jobId != -1L) {
            SmsQueue.markDelivered(jobId)
        }
    }
}
