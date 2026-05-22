package com.simcsv.bulksender.logger

import android.content.Context
import com.simcsv.bulksender.data.AppDatabase
import com.simcsv.bulksender.data.SmsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SmsLogger {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun log(context: Context, smsLog: SmsLog) {
        scope.launch {
            try {
                AppDatabase.getInstance(context).smsLogDao().insert(smsLog)
            } catch (_: Exception) {}
        }
    }
}
