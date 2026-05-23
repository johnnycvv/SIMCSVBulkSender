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

    private val UK_REGEX = Regex("^\\+447[0-9]{9}$")
    private val US_REGEX = Regex("^\\+1[2-9][0-9]{2}[2-9][0-9]{6}$")
    private val AU_REGEX = Regex("^\\+614[0-9]{8}$")

    fun parseBytes(bytes: ByteArray): ParseResult {
        val validContacts   = mutableListOf<Contact>()
        val invalidContacts = mutableListOf<Contact>()
        var rowIndex        = 0
        var headerInfo      = "Reading phone numbers."

        BufferedReader(InputStreamReader(ByteArrayInputStream(bytes)), 262144).use { reader ->
            var line = reader.readLine()
            while (line != null && line.isBlank()) line = reader.readLine()
            if (line == null) return ParseResult(emptyList(), emptyList(), 0, "File is empty")

            val firstCell = firstColumn(line)
            val isHeader  = !looksLikePhone(firstCell)
            headerInfo    = if (isHeader) "Header skipped." else "No header — reading all rows."

            if (!isHeader) {
                rowIndex++
                processLine(firstCell, rowIndex, validContacts, invalidContacts)
            }

            line = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    rowIndex++
                    processLine(firstColumn(line), rowIndex, validContacts, invalidContacts)
                }
                line = reader.readLine()
            }
        }

        return ParseResult(validContacts, invalidContacts, rowIndex, headerInfo)
    }

    private fun firstColumn(line: String): String {
        val comma = line.indexOf(',')
        return if (comma == -1) line.trim() else line.substring(0, comma).trim()
    }

    private fun looksLikePhone(value: String): Boolean {
        if (value.isEmpty()) return false
        for (c in value) {
            if (c != '+' && c != '-' && c != '(' && c != ')' && c != '.' && c != ' '
                && (c < '0' || c > '9')) return false
        }
        return true
    }

    private fun processLine(
        raw: String, rowIndex: Int,
        valid: MutableList<Contact>, invalid: MutableList<Contact>
    ) {
        val phone = normalizePhone(raw)
        val (isValid, errorMsg) = validatePhone(phone)
        if (isValid) valid.add(Contact(rowIndex, phone, "", "", true, ""))
        else         invalid.add(Contact(rowIndex, phone, "", "", false, errorMsg))
    }

    private fun normalizePhone(raw: String): String {
        val buf = StringBuilder(raw.length)
        for (c in raw) {
            if (c != ' ' && c != '-' && c != '(' && c != ')' && c != '.') buf.append(c)
        }
        var phone = buf.toString()

        if (!phone.startsWith("+")) {
            phone = when {
                phone.startsWith("0044")                      -> "+44" + phone.substring(4)
                phone.startsWith("0061")                      -> "+61" + phone.substring(4)
                phone.startsWith("044") && phone.length == 13 -> "+44" + phone.substring(3)
                phone.startsWith("061") && phone.length == 12 -> "+61" + phone.substring(3)
                phone.startsWith("07")  && phone.length == 11 -> "+44" + phone.substring(1)
                phone.startsWith("04")  && phone.length == 10 -> "+61" + phone.substring(1)
                phone.startsWith("1")   && phone.length == 11 -> "+$phone"
                else -> phone
            }
        }
        return phone
    }

    private fun validatePhone(phone: String): Pair<Boolean, String> {
        if (phone.isBlank()) return Pair(false, "Empty phone number")

        val digits = StringBuilder(phone.length)
        for (c in phone) {
            if (c == '+' || c in '0'..'9') digits.append(c)
        }
        val d = digits.toString()

        val len = d.length
        if (len < 10 || len > 15) {
            return Pair(false, "Invalid length ($len digits): $phone")
        }

        val isValid = UK_REGEX.matches(d) || US_REGEX.matches(d) || AU_REGEX.matches(d)
        return if (isValid) Pair(true, "")
               else         Pair(false, "Not a valid UK/US/AU number: $phone")
    }
}
