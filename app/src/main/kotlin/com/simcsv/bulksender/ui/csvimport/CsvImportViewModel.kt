package com.simcsv.bulksender.ui.csvimport

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.simcsv.bulksender.csv.CsvParser
import com.simcsv.bulksender.csv.ParseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CsvImportViewModel(app: Application) : AndroidViewModel(app) {

    val parseResult  = MutableLiveData<ParseResult?>()
    val isLoading    = MutableLiveData(false)
    val error        = MutableLiveData<String?>()
    val debugStatus  = MutableLiveData("idle")

    fun parseCsv(uri: Uri) {
        viewModelScope.launch {
            isLoading.value   = true
            error.value       = null
            debugStatus.value = "STEP 1: parseCsv called, uri=$uri"

            try {
                debugStatus.value = "STEP 2: calling CsvParser.parse..."
                val result = withContext(Dispatchers.IO) {
                    CsvParser.parse(getApplication(), uri)
                }
                debugStatus.value = "STEP 3: parse returned — rows=${result.totalRows}"
                parseResult.value  = result
                debugStatus.value  = "STEP 4: parseResult set"
            } catch (e: CancellationException) {
                debugStatus.value = "CANCELLED: ${e.message}"
                throw e
            } catch (e: Exception) {
                debugStatus.value = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
                error.value = "Failed to read CSV: ${e.message}"
            } finally {
                debugStatus.value = "FINALLY: isLoading → false"
                isLoading.value   = false
            }
        }
    }
}
