package com.example.data.model

data class ExtractionResult(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val website: String = "",
    val address: String = "",
    val notes: String = "",
    val confidence: Int = 85,
    val rawResponse: String = "",
    val success: Boolean = true,
    val errorMessage: String? = null
)
