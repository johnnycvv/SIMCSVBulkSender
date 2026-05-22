package com.simcsv.bulksender.ui.logs

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simcsv.bulksender.data.AppDatabase
import com.simcsv.bulksender.data.SmsLog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogsViewModel : ViewModel() {

    val logs = MutableLiveData<List<SmsLog>>(emptyList())
    private var allLogs: List<SmsLog> = emptyList()

    fun loadLogs(context: Context) {
        viewModelScope.launch {
            AppDatabase.getInstance(context).smsLogDao().getAllLogs().collectLatest { list ->
                allLogs = list
                logs.postValue(list)
            }
        }
    }

    fun filterLogs(status: String?, context: Context) {
        if (status == null) {
            logs.value = allLogs
        } else {
            logs.value = allLogs.filter { it.status == status }
        }
    }

    fun clearLogs(context: Context) {
        viewModelScope.launch {
            AppDatabase.getInstance(context).smsLogDao().clearAll()
        }
    }
}
