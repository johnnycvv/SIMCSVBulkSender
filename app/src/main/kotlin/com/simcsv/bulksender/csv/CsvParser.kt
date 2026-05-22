package com.simcsv.bulksender.csv

import com.simcsv.bulksender.data.Contact
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

data class ParseResult(
    val validContacts: List<Contact>,
    val invalidContacts: List<Contact>,
    val totalRows: Int,
    val headerInfo: String
)

object CsvParser {

    private val UK_REGEX         = Regex("^(\\+44|0044|0)7[0-9]{9}$")
    private val US_REGEX         = Regex("^(\\+1)?[2-9]\\d{2}[2-9]\\d{6}$")
    private val AU_REGEX         = Regex("^(\\+61|0061|0)4[0-9]{8}$")
    private val STRIP_FORMATTING = Regex("[\\s\\-().]+")
    private val STRIP_NON_DIGITS = Regex("[^0-9+]")
    private val PHONE_CHARS_ONLY = Regex("[+0-9()\\s\\-.]+")

    private fun looksLikePhone(value: String): Boolean =
        value.trim().matches(PHONE_CHARS_ONLY)

    fun parseBytes(bytes: ByteArray): ParseResult {
        val validContacts   = mutableListOf<Contact>()
        val invalidContacts = mutableListOf<Contact>()
        var rowIndex        = 0
        var headerInfo      = "Reading phone numbers."

        BufferedReader(InputStreamReader(ByteArrayInputStream(bytes))).use { reader ->
            var line = reader.readLine()
            while (line != null && line.isBlank()) line = reader.readLine()
            if (line == null) return ParseResult(emptyList(), emptyList(), 0, "File is empty")

            val firstCell = line.substringBefore(',').trim()
            val isHeader  = !looksLikePhone(firstCell)
            headerInfo    = if (isHeader) "Header skipped." else "No header. Reading all rows."

            if (!isHeader) {
                rowIndex++
                processLine(firstCell, rowIndex, validContacts, invalidContacts)
            }

            line = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    rowIndex++
                    processLine(line.substringBefore(',').trim(), rowIndex, validContacts, invalidContacts)
                }
                line = reader.readLine()
            }
        }

        return ParseResult(validContacts, invalidContacts, rowIndex, headerInfo)
    }

    private fun processLine(raw: String, rowIndex: Int, valid: MutableList<Contact>, invalid: MutableList<Contact>) {
        val phone = normalizePhone(raw)
        val (isValid, errorMsg) = validatePhone(phone)
        if (isValid) valid.add(Contact(rowIndex, phone, "", "", true, ""))
        else         invalid.add(Contact(rowIndex, phone, "", "", false, errorMsg))
    }

    private fun normalizePhone(raw: String): String {
        var phone = raw.replace(STRIP_FORMATTING, "")
        if (!phone.startsWith("+")) {
            phone = when {
                phone.startsWith("0044")                       -> "+44" + phone.substring(4)
                phone.startsWith("0061")                       -> "+61" + phone.substring(4)
                phone.startsWith("044") && phone.length == 13  -> "+44" + phone.substring(3)
                phone.startsWith("061") && phone.length == 12  -> "+61" + phone.substring(3)
                phone.startsWith("07")  && phone.length == 11  -> "+44" + phone.substring(1)
                phone.startsWith("04")  && phone.length == 10  -> "+61" + phone.substring(1)
                phone.startsWith("1")   && phone.length == 11  -> "+$phone"
                else -> phone
            }
        }
        return phone
    }

    private fun validatePhone(phone: String): Pair<Boolean, String> {
        if (phone.isBlank()) return Pair(false, "Phone number is empty")
        val digits  = phone.replace(STRIP_NON_DIGITS, "")
        val isValid = UK_REGEX.matches(digits) || US_REGEX.matches(digits) || AU_REGEX.matches(digits)
        return if (isValid) Pair(true, "") else Pair(false, "Invalid UK/US/AU number: $phone")
    }
}
