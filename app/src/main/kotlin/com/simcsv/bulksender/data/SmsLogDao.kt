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

    @Query("""
        SELECT COUNT(*) FROM sms_logs
        WHERE status = 'SENT'
        AND date(timestamp/1000,'unixepoch','localtime') = date('now','localtime')
    """)
    suspend fun getTodaySentCount(): Int

    @Query("""
        SELECT COUNT(*) FROM sms_logs
        WHERE status = 'DELIVERED'
        AND date(timestamp/1000,'unixepoch','localtime') = date('now','localtime')
    """)
    suspend fun getTodayDeliveredCount(): Int

    @Query("""
        SELECT COUNT(*) FROM sms_logs
        WHERE status = 'FAILED'
        AND date(timestamp/1000,'unixepoch','localtime') = date('now','localtime')
    """)
    suspend fun getTodayFailedCount(): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'SENT' OR status = 'DELIVERED'")
    suspend fun getAllTimeSentCount(): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'DELIVERED'")
    suspend fun getAllTimeDeliveredCount(): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE status = 'FAILED'")
    suspend fun getAllTimeFailedCount(): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE sessionId = :sessionId AND status IN ('SENT','DELIVERED')")
    suspend fun getSessionSentCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE sessionId = :sessionId AND status = 'DELIVERED'")
    suspend fun getSessionDeliveredCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM sms_logs WHERE sessionId = :sessionId AND status = 'FAILED'")
    suspend fun getSessionFailedCount(sessionId: String): Int

    @Query("DELETE FROM sms_logs")
    suspend fun clearAll()

    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 200): Flow<List<SmsLog>>
}
