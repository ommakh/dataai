package com.example.service

import com.example.data.local.ExtractedLead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GoogleSheetsSyncService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun syncLead(lead: ExtractedLead, webhookUrl: String): Result<String> = withContext(Dispatchers.IO) {
        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(Exception("Google Sheets Webhook URL is not configured. Please set it up in DBA Settings."))
        }

        try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lead.createdAt))

            val leadObj = JSONObject().apply {
                put("id", lead.id)
                put("name", lead.name)
                put("email", lead.email)
                put("phone", lead.phone)
                put("company", lead.company)
                put("jobTitle", lead.jobTitle)
                put("website", lead.website)
                put("address", lead.address)
                put("notes", lead.notes)
                put("source", lead.source)
                put("sourceDetail", lead.sourceDetail)
                put("confidence", lead.confidence)
                put("timestamp", dateStr)
            }

            val payload = JSONObject().apply {
                put("action", "appendLead")
                put("lead", leadObj)
            }

            val request = Request.Builder()
                .url(webhookUrl.trim())
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful || response.code in 200..302) {
                Result.success("Synced to Google Sheets row (HTTP ${response.code})")
            } else {
                Result.failure(Exception("Sheets Webhook error (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(webhookUrl: String): Result<String> = withContext(Dispatchers.IO) {
        if (webhookUrl.isBlank()) {
            return@withContext Result.failure(Exception("Please enter a Google Sheets Webhook URL first."))
        }

        try {
            val payload = JSONObject().apply {
                put("action", "ping")
                put("source", "DataExtract AI Agent")
                put("timestamp", System.currentTimeMillis())
            }

            val request = Request.Builder()
                .url(webhookUrl.trim())
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful || response.code in 200..302) {
                Result.success("Connection Successful! Google Sheets DBA responded: ${body.take(120)}")
            } else {
                Result.failure(Exception("Failed with HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection error: ${e.localizedMessage ?: e.message}"))
        }
    }

    fun generateAppsScriptSnippet(): String {
        return """
// ==========================================
// Google Sheets DBA Webhook for DataExtract AI
// Paste this in Google Sheets: Extensions -> Apps Script
// Then click: Deploy -> New Deployment -> Web App
// Execute as: Me, Who has access: Anyone
// ==========================================

function doPost(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getActiveSheet();
    
    // Auto-create standard column headers on first run
    if (sheet.getLastRow() === 0) {
      sheet.appendRow([
        "Timestamp",
        "Full Name",
        "Email Address",
        "Phone Number",
        "Company / Organization",
        "Job Title",
        "Website",
        "Physical Address",
        "Source Type",
        "Confidence",
        "Agent Notes"
      ]);
      sheet.getRange(1, 1, 1, 11).setFontWeight("bold").setBackground("#EEF2FF");
    }
    
    if (!e || !e.postData || !e.postData.contents) {
      return ContentService.createTextOutput(JSON.stringify({status: "ok", message: "Webhook active"}))
        .setMimeType(ContentService.MimeType.JSON);
    }
    
    var data = JSON.parse(e.postData.contents);
    
    // Handshake Ping
    if (data.action === "ping") {
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        message: "Google Sheets DBA Connected Successfully!",
        sheetName: sheet.getName()
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    // Append Extracted Lead
    var lead = data.lead || {};
    sheet.appendRow([
      lead.timestamp || new Date().toISOString(),
      lead.name || "",
      lead.email || "",
      lead.phone || "",
      lead.company || "",
      lead.jobTitle || "",
      lead.website || "",
      lead.address || "",
      lead.source || "",
      (lead.confidence ? lead.confidence + "%" : ""),
      lead.notes || ""
    ]);
    
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      appendedRow: sheet.getLastRow()
    })).setMimeType(ContentService.MimeType.JSON);
    
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}
        """.trimIndent()
    }

    fun exportToCsv(leads: List<ExtractedLead>): String {
        val sb = StringBuilder()
        sb.appendLine("Timestamp,Name,Email,Phone,Company,Job Title,Website,Address,Source,Confidence,Sync Status,Notes")
        for (lead in leads) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lead.createdAt))
            sb.appendLine(
                listOf(
                    escapeCsv(dateStr),
                    escapeCsv(lead.name),
                    escapeCsv(lead.email),
                    escapeCsv(lead.phone),
                    escapeCsv(lead.company),
                    escapeCsv(lead.jobTitle),
                    escapeCsv(lead.website),
                    escapeCsv(lead.address),
                    escapeCsv(lead.source),
                    "${lead.confidence}%",
                    lead.syncStatus,
                    escapeCsv(lead.notes)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
