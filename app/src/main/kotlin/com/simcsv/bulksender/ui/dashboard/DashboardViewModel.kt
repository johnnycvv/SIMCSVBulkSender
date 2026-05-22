package com.simcsv.bulksender.ui.dashboard

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simcsv.bulksender.data.AppDatabase
import kotlinx.coroutines.launch

data class DashboardStats(
    val totalSent: Int = 0,
    val totalFailed: Int = 0,
    val todaySent: Int = 0
)

class DashboardViewModel : ViewModel() {

    val stats = MutableLiveData(DashboardStats())

    fun loadStats(context: Context) {
        viewModelScope.launch {
            try {
                val dao = AppDatabase.getInstance(context).smsLogDao()
                val todaySent = dao.getTodaySentCount()
                stats.postValue(DashboardStats(todaySent = todaySent))
            } catch (_: Exception) {}
        }
    }
}
