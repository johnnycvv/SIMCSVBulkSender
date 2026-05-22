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

    private fun looksLikePhone(value: String): Boolean {
        val stripped = value.trim()
        return stripped.matches(Regex("[+0-9()\\s\\-.]+"))
    }

    suspend fun parse(context: Context, uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        val validContacts = mutableListOf<Contact>()
        val invalidContacts = mutableListOf<Contact>()
        var rowIndex = 0
        var skipFirst = false

        val lines = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty()) lines.add(line)
            }
        }

        if (lines.isEmpty()) {
            return@withContext ParseResult(emptyList(), emptyList(), 0, "File is empty")
        }

        val firstCell = lines[0].split(",")[0].trim()
        if (!looksLikePhone(firstCell)) {
            skipFirst = true
        }

        val startIndex = if (skipFirst) 1 else 0
        val headerInfo = if (skipFirst) "Header row skipped. Reading phone numbers." else "No header detected. Reading all rows."

        for (i in startIndex until lines.size) {
            val line = lines[i]
            rowIndex++
            val raw = line.split(",")[0].trim()
            val phone = normalizePhone(raw)
            val (isValid, error) = validatePhone(phone)
            val contact = Contact(
                rowIndex = rowIndex,
                phoneNumber = phone,
                message = "",
                name = "",
                isValid = isValid,
                validationError = error
            )
            if (isValid) validContacts.add(contact) else invalidContacts.add(contact)
        }

        ParseResult(validContacts, invalidContacts, rowIndex, headerInfo)
    }

    private fun normalizePhone(raw: String): String {
        var phone = raw.replace(Regex("[\\s\\-().]+"), "")
        if (!phone.startsWith("+")) {
            when {
                phone.startsWith("0044") -> phone = "+44" + phone.substring(4)
                phone.startsWith("0061") -> phone = "+61" + phone.substring(4)
                phone.startsWith("044") && phone.length == 13 -> phone = "+44" + phone.substring(3)
                phone.startsWith("061") && phone.length == 12 -> phone = "+61" + phone.substring(3)
                phone.startsWith("07") && phone.length == 11 -> phone = "+44" + phone.substring(1)
                phone.startsWith("04") && phone.length == 10 -> phone = "+61" + phone.substring(1)
                phone.startsWith("1") && phone.length == 11 -> phone = "+$phone"
            }
        }
        return phone
    }

    private fun validatePhone(phone: String): Pair<Boolean, String> {
        if (phone.isBlank()) return Pair(false, "Phone number is empty")
        val digits = phone.replace(Regex("[^0-9+]"), "")
        val isValid = UK_REGEX.matches(digits) || US_REGEX.matches(digits) || AU_REGEX.matches(digits)
        return if (isValid) Pair(true, "")
        else Pair(false, "Invalid UK/US/AU number: $phone")
    }
}
