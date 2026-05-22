package com.simcsv.bulksender.ui.csvimport

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.simcsv.bulksender.csv.CsvParser
import com.simcsv.bulksender.csv.ParseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class CsvImportViewModel(app: Application) : AndroidViewModel(app) {

    val parseResult = MutableLiveData<ParseResult?>()
    val isLoading   = MutableLiveData(false)
    val error       = MutableLiveData<String?>()

    fun parseCsv(uri: Uri) {
        viewModelScope.launch {
            isLoading.value = true
            error.value     = null
            try {
                val result = CsvParser.parse(getApplication(), uri)
                parseResult.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "Failed to read CSV: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }
}
