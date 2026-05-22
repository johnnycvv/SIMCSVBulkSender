package com.simcsv.bulksender.logger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.simcsv.bulksender.data.AppDatabase
import com.simcsv.bulksender.data.SmsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LogExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    suspend fun exportToCsv(context: Context, sessionId: String? = null): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val logs: List<SmsLog> = if (sessionId != null) {
                    db.smsLogDao().getLogsBySession(sessionId)
                } else {
                    kotlinx.coroutines.flow.first(db.smsLogDao().getAllLogs())
                }

                val fileName = "sms_log_${System.currentTimeMillis()}.csv"
                val file = File(context.cacheDir, fileName)

                file.bufferedWriter().use { writer ->
                    writer.write("ID,Session,Phone,Name,Message,Status,Error,Timestamp,RetryCount,SimSlot\n")
                    logs.forEach { log ->
                        writer.write(
                            "${log.id}," +
                                    "${escapeCsv(log.sessionId)}," +
                                    "${escapeCsv(log.phoneNumber)}," +
                                    "${escapeCsv(log.name)}," +
                                    "${escapeCsv(log.message)}," +
                                    "${log.status}," +
                                    "${escapeCsv(log.errorMessage)}," +
                                    "${dateFormat.format(Date(log.timestamp))}," +
                                    "${log.retryCount}," +
                                    "${log.simSlot}\n"
                        )
                    }
                }

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } catch (e: Exception) {
                null
            }
        }

    fun buildShareIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.first(): T {
    var result: T? = null
    collect { result = it; return@collect }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
