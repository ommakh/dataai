package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "extracted_leads")
data class ExtractedLead(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val website: String = "",
    val address: String = "",
    val notes: String = "",
    val source: String = "WEBSITE", // "WEBSITE" or "CARD_SCAN"
    val sourceDetail: String = "",  // e.g. https://domain.com or "Business Card"
    val confidence: Int = 85,       // Percentage 0..100
    val syncStatus: String = "PENDING", // "PENDING", "SYNCED", "FAILED"
    val syncError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null
)
