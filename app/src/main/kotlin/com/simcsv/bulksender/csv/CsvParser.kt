package com.simcsv.bulksender.csv

import android.content.Context
import android.net.Uri
import com.simcsv.bulksender.data.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class ParseResult(
    val validContacts: List<Contact>,
    val invalidContacts: List<Contact>,
    val totalRows: Int,
    val headerInfo: String
)

object CsvParser {

    private val UK_REGEX = Regex("^(\\+44|0044|0)7[0-9]{9}$")
    private val US_REGEX = Regex("^(\\+1)?[2-9]\\d{2}[2-9]\\d{6}$")
    private val AU_REGEX = Regex("^(\\+61|0061|0)4[0-9]{8}$")

    private fun looksLikePhone(value: String): Boolean =
        value.trim().matches(Regex("[+0-9()\\s\\-.]+"))

    suspend fun parse(context: Context, uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        val validContacts   = mutableListOf<Contact>()
        val invalidContacts = mutableListOf<Contact>()
        var rowIndex   = 0
        var headerInfo = "Reading phone numbers."

        val stream = context.contentResolver.openInputStream(uri)
            ?: return@withContext ParseResult(emptyList(), emptyList(), 0, "Could not open file")

        stream.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream), 64 * 1024)

            var line = reader.readLine()
            while (line != null && line.trim().isEmpty()) line = reader.readLine()

            if (line == null) {
                return@use
            }

            val firstCell = line.trim().split(",")[0].trim()
            val isHeader = !looksLikePhone(firstCell)

            headerInfo = if (isHeader) "Header skipped. Reading phone numbers."
                         else          "No header detected. Reading all rows."

            if (!isHeader) {
                rowIndex++
                processLine(firstCell, rowIndex, validContacts, invalidContacts)
            }

            line = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    rowIndex++
                    val raw = trimmed.split(",")[0].trim()
                    processLine(raw, rowIndex, validContacts, invalidContacts)
                }
                line = reader.readLine()
            }
        }

        ParseResult(validContacts, invalidContacts, rowIndex, headerInfo)
    }

    private fun processLine(
        raw: String,
        rowIndex: Int,
        valid: MutableList<Contact>,
        invalid: MutableList<Contact>
    ) {
        val phone = normalizePhone(raw)
        val (isValid, error) = validatePhone(phone)
        val contact = Contact(
            rowIndex        = rowIndex,
            phoneNumber     = phone,
            message         = "",
            name            = "",
            isValid         = isValid,
            validationError = error
        )
        if (isValid) valid.add(contact) else invalid.add(contact)
    }

    private fun normalizePhone(raw: String): String {
        var phone = raw.replace(Regex("[\\s\\-().]+"), "")
        if (!phone.startsWith("+")) {
            when {
                phone.startsWith("0044") -> phone = "+44" + phone.substring(4)
                phone.startsWith("0061") -> phone = "+61" + phone.substring(4)
                phone.startsWith("044") && phone.length == 13 -> phone = "+44" + phone.substring(3)
                phone.startsWith("061") && phone.length == 12 -> phone = "+61" + phone.substring(3)
                phone.startsWith("07")  && phone.length == 11 -> phone = "+44" + phone.substring(1)
                phone.startsWith("04")  && phone.length == 10 -> phone = "+61" + phone.substring(1)
                phone.startsWith("1")   && phone.length == 11 -> phone = "+$phone"
            }
        }
        return phone
    }

    private fun validatePhone(phone: String): Pair<Boolean, String> {
        if (phone.isBlank()) return Pair(false, "Phone number is empty")
        val digits   = phone.replace(Regex("[^0-9+]"), "")
        val isValid  = UK_REGEX.matches(digits) || US_REGEX.matches(digits) || AU_REGEX.matches(digits)
        return if (isValid) Pair(true, "")
        else Pair(false, "Invalid UK/US/AU number: $phone")
    }
}
