package com.simcsv.bulksender.ui.csvimport

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.simcsv.bulksender.csv.ParseResult

class CsvImportViewModel(app: Application) : AndroidViewModel(app) {

    val parseResult = MutableLiveData<ParseResult?>()
    val isLoading   = MutableLiveData(false)
    val error       = MutableLiveData<String?>()
    val debugStatus = MutableLiveData("idle")

    fun setResult(result: ParseResult) {
        parseResult.value = result
        isLoading.value   = false
    }

    fun setError(msg: String) {
        error.value     = msg
        isLoading.value = false
    }

    fun setLoading() {
        isLoading.value   = true
        debugStatus.value = "Parsing..."
    }
}
