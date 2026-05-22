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

    suspend fun parse(context: Context, uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        val validContacts = mutableListOf<Contact>()
        val invalidContacts = mutableListOf<Contact>()
        var rowIndex = 0
        var phoneCol = -1
        var messageCol = -1
        var nameCol = -1
        var headerInfo = ""

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            var isHeader = true

            reader.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEachLine

                val columns = parseCsvLine(line)

                if (isHeader) {
                    isHeader = false
                    columns.forEachIndexed { idx, col ->
                        val lower = col.lowercase().trim()
                        when {
                            lower.contains("phone") || lower.contains("mobile") || lower.contains("number") -> phoneCol = idx
                            lower.contains("message") || lower.contains("msg") || lower.contains("text") -> messageCol = idx
                            lower.contains("name") -> nameCol = idx
                        }
                    }
                    if (phoneCol == -1) phoneCol = 0
                    if (messageCol == -1) messageCol = 1
                    headerInfo = "Phone col: ${columns.getOrElse(phoneCol) { "?" }}, Message col: ${columns.getOrElse(messageCol) { "?" }}"
                    return@forEachLine
                }

                rowIndex++
                val phone = normalizePhone(columns.getOrElse(phoneCol) { "" }.trim())
                val message = columns.getOrElse(messageCol) { "" }.trim()
                val name = if (nameCol >= 0) columns.getOrElse(nameCol) { "" }.trim() else ""

                val (isValid, error) = validateContact(phone, message)
                val contact = Contact(
                    rowIndex = rowIndex,
                    phoneNumber = phone,
                    message = message,
                    name = name,
                    isValid = isValid,
                    validationError = error
                )

                if (isValid) validContacts.add(contact) else invalidContacts.add(contact)
            }
        }

        ParseResult(validContacts, invalidContacts, rowIndex, headerInfo)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (i in line.indices) {
            val c = line[i]
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString())
        return result
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

    private fun validateContact(phone: String, message: String): Pair<Boolean, String> {
        if (phone.isBlank()) return Pair(false, "Phone number is empty")
        if (message.isBlank()) return Pair(false, "Message is empty")
        if (message.length > 160 * 8) return Pair(false, "Message exceeds maximum length")

        val digits = phone.replace(Regex("[^0-9+]"), "")
        val isValid = UK_REGEX.matches(digits) || US_REGEX.matches(digits) || AU_REGEX.matches(digits)
        return if (isValid) Pair(true, "")
        else Pair(false, "Invalid UK/US/AU number format: $phone")
    }
}
