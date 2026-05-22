package com.simcsv.bulksender.ui.csvimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simcsv.bulksender.csv.CsvParser
import com.simcsv.bulksender.csv.ParseResult
import kotlinx.coroutines.launch

class CsvImportViewModel : ViewModel() {
    val parseResult = MutableLiveData<ParseResult?>()
    val isLoading = MutableLiveData(false)
    val error = MutableLiveData<String?>()

    fun parseCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            try {
                val result = CsvParser.parse(context, uri)
                parseResult.value = result
            } catch (e: Exception) {
                error.value = "Failed to parse CSV: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }
}
