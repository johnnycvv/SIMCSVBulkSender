package com.simcsv.bulksender.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra("jobId", -1L)
        if (jobId == -1L) return
        when (resultCode) {
            Activity.RESULT_OK -> SmsQueue.markSent(jobId)
            else -> SmsQueue.markFailed(jobId, "Send result code: $resultCode", 3)
        }
    }
}
