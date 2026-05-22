package com.simcsv.bulksender.ui.dashboard

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simcsv.bulksender.data.AppDatabase
import kotlinx.coroutines.launch

data class DashboardStats(
    val todaySent:       Int = 0,
    val todayDelivered:  Int = 0,
    val todayFailed:     Int = 0,
    val allTimeSent:     Int = 0,
    val allTimeDelivered: Int = 0,
    val allTimeFailed:   Int = 0
)

class DashboardViewModel : ViewModel() {

    val stats = MutableLiveData(DashboardStats())

    fun loadStats(context: Context) {
        viewModelScope.launch {
            try {
                val dao = AppDatabase.getInstance(context).smsLogDao()
                stats.postValue(DashboardStats(
                    todaySent        = dao.getTodaySentCount(),
                    todayDelivered   = dao.getTodayDeliveredCount(),
                    todayFailed      = dao.getTodayFailedCount(),
                    allTimeSent      = dao.getAllTimeSentCount(),
                    allTimeDelivered = dao.getAllTimeDeliveredCount(),
                    allTimeFailed    = dao.getAllTimeFailedCount()
                ))
            } catch (_: Exception) {}
        }
    }
}
