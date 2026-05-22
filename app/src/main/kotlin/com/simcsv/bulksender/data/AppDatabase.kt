package com.simcsv.bulksender.data

import android.content.Context
import androidx.room.*

@Database(entities = [SmsLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsLogDao(): SmsLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "simcsv_bulksender.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
