package com.simcsv.bulksender.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SmsLog): Long

    @Update
    suspend fun update(log: SmsLog)

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SmsLog>>

    @Query("SELECT * FROM sms_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getLogsBySession(sessionId: String): List<SmsLog>

    @Query("SELECT COUNT(*) FROM sms_logs WHERE sessionId = :sessionId AND status = 'SENT'")
    suspend fun getSentCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE sessionId = :sessionId AND status = 'FAILED'")
    suspend fun getFailedCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE sessionId = :sessionId AND status = 'DELIVERED'")
    suspend fun getDeliveredCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE date(timestamp/1000,'unixepoch') = date('now') AND status = 'SENT'")
    suspend fun getTodaySentCount(): Int

    @Query("DELETE FROM sms_logs")
    suspend fun clearAll()

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<SmsLog>>
}
