package com.simcsv.bulksender.data

data class Contact(
    val rowIndex: Int,
    val phoneNumber: String,
    val message: String,
    val name: String = "",
    val isValid: Boolean = true,
    val validationError: String = ""
)
