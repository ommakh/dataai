package com.example.ui.screens

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import com.example.data.local.AppDatabase
import com.example.data.local.ExtractedLead
import com.example.data.model.AgentConfig
import com.example.data.model.ExtractionPreset
import com.example.data.model.ExtractionResult
import com.example.service.DocumentParserService
import com.example.service.DownloadResult
import com.example.service.ExcelExportService
import com.example.service.GeminiService
import com.example.service.GoogleSheetsSyncService
import com.example.service.ParsedDocument
import com.example.service.WebsiteScraperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab(val title: String) {
    EXTRACT("Convert to Excel"),
    LEADS("Leads DBA"),
    AGENT("OCR Settings"),
    SHEETS("Sheets DBA")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val websiteScraper = WebsiteScraperService()
    private val geminiService = GeminiService(application.applicationContext)
    private val sheetsSyncService = GoogleSheetsSyncService()
    private val excelExportService = ExcelExportService()
    private val documentParser = DocumentParserService()

    private val prefs = application.getSharedPreferences("data_agent_prefs", Context.MODE_PRIVATE)

    // No data is persisted in any database. In-memory list is only used for immediate Excel conversion.
    private val _sessionLeads = MutableStateFlow<List<ExtractedLead>>(emptyList())
    val allLeads: StateFlow<List<ExtractedLead>> = _sessionLeads.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _syncedCount = MutableStateFlow(0)
    val syncedCount: StateFlow<Int> = _syncedCount.asStateFlow()

    init {
        // Ensure zero data is ever stored in the local database
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getInstance(application).leadDao().clearAll()
            } catch (_: Exception) {
            }
        }
    }

    private fun updateSessionLeads(leads: List<ExtractedLead>) {
        // Intentionally do not persist leads to any database
        _sessionLeads.value = leads
        _totalCount.value = 0
        _syncedCount.value = 0
    }

    // Current Navigation Tab
    private val _currentTab = MutableStateFlow(AppTab.EXTRACT)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Agent / Offline OCR Configuration
    private val _agentConfig = MutableStateFlow(loadAgentConfig())
    val agentConfig: StateFlow<AgentConfig> = _agentConfig.asStateFlow()

    private val _tesseractStatus = MutableStateFlow("Active (English Only)")
    val tesseractStatus: StateFlow<String> = _tesseractStatus.asStateFlow()

    fun syncTesseractOfflineLanguageData() {
        viewModelScope.launch {
            _tesseractStatus.value = "Ready (English Only)"
            _bannerMessage.value = "OCR Engine Ready (English Only)"
        }
    }

    // Extraction States
    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _extractionStep = MutableStateFlow("")
    val extractionStep: StateFlow<String> = _extractionStep.asStateFlow()

    private val _lastExtractionResult = MutableStateFlow<ExtractionResult?>(null)
    val lastExtractionResult: StateFlow<ExtractionResult?> = _lastExtractionResult.asStateFlow()

    private val _lastExtractionSource = MutableStateFlow("CARD_SCAN")
    val lastExtractionSource: StateFlow<String> = _lastExtractionSource.asStateFlow()

    private val _lastExtractionSourceDetail = MutableStateFlow("")
    val lastExtractionSourceDetail: StateFlow<String> = _lastExtractionSourceDetail.asStateFlow()

    private val _selectedCardBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedCardBitmap: StateFlow<Bitmap?> = _selectedCardBitmap.asStateFlow()

    // Active queue of 1 to 50 captured or selected card images for current Excel conversion
    private val _capturedCardBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedCardBitmaps: StateFlow<List<Bitmap>> = _capturedCardBitmaps.asStateFlow()

    // Transient extracted rows for current Excel (.xlsx) conversion only (no database storage)
    private val _cardBatchLeads = MutableStateFlow<List<ExtractedLead>>(emptyList())
    val cardBatchLeads: StateFlow<List<ExtractedLead>> = _cardBatchLeads.asStateFlow()

    private val _isCurrentPhotoExtracted = MutableStateFlow(false)
    val isCurrentPhotoExtracted: StateFlow<Boolean> = _isCurrentPhotoExtracted.asStateFlow()

    // Persisted across configuration/orientation changes during external camera capture
    var pendingCameraUri: Uri? = null
    var pendingCameraFilePath: String? = null
    var pendingAppendCardMode: Boolean = false

    // Bulk Document Upload & Excel Conversion States (Word .docx/.doc, PDF .pdf)
    private val _selectedDocuments = MutableStateFlow<List<ParsedDocument>>(emptyList())
    val selectedDocuments: StateFlow<List<ParsedDocument>> = _selectedDocuments.asStateFlow()

    private val _isBulkExtracting = MutableStateFlow(false)
    val isBulkExtracting: StateFlow<Boolean> = _isBulkExtracting.asStateFlow()

    private val _bulkProgress = MutableStateFlow("")
    val bulkProgress: StateFlow<String> = _bulkProgress.asStateFlow()

    private val _bulkProgressCurrent = MutableStateFlow(0)
    val bulkProgressCurrent: StateFlow<Int> = _bulkProgressCurrent.asStateFlow()

    private val _bulkProgressTotal = MutableStateFlow(0)
    val bulkProgressTotal: StateFlow<Int> = _bulkProgressTotal.asStateFlow()

    private val _bulkExtractedLeads = MutableStateFlow<List<ExtractedLead>>(emptyList())
    val bulkExtractedLeads: StateFlow<List<ExtractedLead>> = _bulkExtractedLeads.asStateFlow()

    private val _bulkCsvContent = MutableStateFlow("")
    val bulkCsvContent: StateFlow<String> = _bulkCsvContent.asStateFlow()

    private val _bulkBatchCompleted = MutableStateFlow(false)
    val bulkBatchCompleted: StateFlow<Boolean> = _bulkBatchCompleted.asStateFlow()

    // Sheets Connection Testing
    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult.asStateFlow()

    // Status / Toast feedback banner
    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    // Dedicated alert state when no card is recognized in a scanned image
    private val _noCardRecognizedAlert = MutableStateFlow<String?>(null)
    val noCardRecognizedAlert: StateFlow<String?> = _noCardRecognizedAlert.asStateFlow()

    // Downloaded offline Excel file confirmation state
    private val _downloadedFileResult = MutableStateFlow<DownloadResult?>(null)
    val downloadedFileResult: StateFlow<DownloadResult?> = _downloadedFileResult.asStateFlow()

    // Dialog for Editing a row before re-downloading Excel
    private val _editingLead = MutableStateFlow<ExtractedLead?>(null)
    val editingLead: StateFlow<ExtractedLead?> = _editingLead.asStateFlow()

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    fun setBannerMessage(message: String) {
        _bannerMessage.value = message
    }

    fun dismissNoCardAlert() {
        _noCardRecognizedAlert.value = null
    }

    private fun showNoCardSystemNotification(context: Context) {
        try {
            val channelId = "no_card_scanner_channel"
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Card Scanner Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when no business card is recognized in a scanned photo"
                }
                notificationManager.createNotificationChannel(channel)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("There is no card")
                .setContentText("There is no card recognized in the photo. Please scan a valid business card.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(2001, notification)
        } catch (_: Exception) {
        }
    }

    fun setCardBitmap(bitmap: Bitmap?) {
        _selectedCardBitmap.value = bitmap
    }

    fun clearLastExtraction() {
        _lastExtractionResult.value = null
        _selectedCardBitmap.value = null
        _capturedCardBitmaps.value = emptyList()
        _isCurrentPhotoExtracted.value = false
        _noCardRecognizedAlert.value = null
        _downloadedFileResult.value = null
        cleanupCameraTempFiles()
    }

    /**
     * Combines extracted business cards in memory (including repeated cards, without saving to any database)
     * so every scanned card (even if repeated on a 2nd or 3rd scan) is converted directly into the Excel (.xlsx) file.
     */
    private fun combineCardsInMemoryForExcel(
        candidates: List<ExtractedLead>,
        appendToExisting: Boolean
    ): Pair<List<ExtractedLead>, Int> {
        val baseBatch = if (appendToExisting) _cardBatchLeads.value else emptyList()
        var nextId = (baseBatch.maxOfOrNull { it.id } ?: 0L) + 1L

        val newBatchItems = candidates.map { card ->
            card.copy(
                id = nextId++,
                syncStatus = "NOT_SAVED"
            )
        }

        val finalBatch = if (appendToExisting) {
            baseBatch + newBatchItems
        } else {
            newBatchItems
        }
        updateSessionLeads(finalBatch)
        return Pair(finalBatch, newBatchItems.size)
    }

    /**
     * Scrape a website URL, extract contact details, and convert directly into an Excel (.xlsx) file
     * without saving any data to a database.
     */
    fun extractFromWebsite(url: String) {
        if (url.isBlank()) {
            _bannerMessage.value = "Please enter a valid website URL."
            return
        }

        viewModelScope.launch {
            _isExtracting.value = true
            _downloadedFileResult.value = null
            _lastExtractionSource.value = "WEBSITE"
            _lastExtractionSourceDetail.value = url.trim()
            _extractionStep.value = "Connecting to website..."

            val scrapeResult = websiteScraper.scrapeWebsite(url)
            val scrapedData = scrapeResult.getOrNull()
            val textContent = scrapedData?.cleanText ?: ""

            if (textContent.isBlank()) {
                _isExtracting.value = false
                _lastExtractionResult.value = null
                _bannerMessage.value = "Could not read contact text from this URL."
                return@launch
            }

            _extractionStep.value = "Extracting contact details & converting to Excel (.xlsx)..."
            val extractionResult = geminiService.extractFromWebsite(
                url = url,
                pageText = textContent,
                agentConfig = _agentConfig.value
            )

            _isExtracting.value = false

            if (extractionResult.success &&
                (extractionResult.name.isNotBlank() || extractionResult.email.isNotBlank() || extractionResult.phone.isNotBlank())
            ) {
                _lastExtractionResult.value = extractionResult
                val leadForExcel = ExtractedLead(
                    id = 1L,
                    name = extractionResult.name,
                    phone = extractionResult.phone,
                    email = extractionResult.email,
                    company = extractionResult.company,
                    jobTitle = extractionResult.jobTitle,
                    website = extractionResult.website.ifBlank { url.trim() },
                    address = extractionResult.address,
                    notes = extractionResult.notes,
                    source = "WEBSITE",
                    sourceDetail = url.trim(),
                    confidence = extractionResult.confidence
                )
                _isExtracting.value = false
                _lastExtractionResult.value = extractionResult
                _extractionStep.value = "Extracted contact details ready in memory (No data saved)"
                _bannerMessage.value = "Extracted contact for ${extractionResult.name.ifBlank { url }}. No data saved to database or storage."
            } else {
                _lastExtractionResult.value = null
                _bannerMessage.value = extractionResult.errorMessage ?: "No contact information found on this page."
            }
        }
    }

    /**
     * Scan a single captured or selected card image (containing 1 to 20+ business cards)
     * and convert directly to Excel (.xlsx) without saving data.
     */
    fun extractFromCardImage(bitmap: Bitmap, appendToExisting: Boolean = false) {
        extractFromMultipleCardImages(listOf(bitmap), appendToExisting = appendToExisting)
    }

    /**
     * Capture or select 1 to 50 business card images at a time.
     * Extracts all cards (including repeated cards) and converts directly into an Excel (.xlsx) file
     * without saving any data to the app database.
     */
    fun extractFromMultipleCardImages(bitmaps: List<Bitmap>, appendToExisting: Boolean = false) {
        if (bitmaps.isEmpty()) return
        val limitedInput = bitmaps.take(50)

        val updatedImageQueue = if (appendToExisting) {
            (_capturedCardBitmaps.value + limitedInput).takeLast(50)
        } else {
            limitedInput
        }
        _capturedCardBitmaps.value = updatedImageQueue
        _selectedCardBitmap.value = updatedImageQueue.lastOrNull()

        viewModelScope.launch {
            _isExtracting.value = true
            _downloadedFileResult.value = null
            _noCardRecognizedAlert.value = null
            _lastExtractionSource.value = "CARD_SCAN"
            _lastExtractionSourceDetail.value = "${updatedImageQueue.size} Captured Card Image(s)"

            val newlyExtracted = mutableListOf<ExtractedLead>()
            for ((position, bmp) in limitedInput.withIndex()) {
                _extractionStep.value = if (limitedInput.size > 1) {
                    "Scanning card image ${position + 1} of ${limitedInput.size} → Converting to Excel..."
                } else {
                    "Scanning business cards from photo → Converting directly to Excel (.xlsx)..."
                }
                val leadsFromPhoto = try {
                    geminiService.extractLeadsFromCardImage(
                        bitmap = bmp,
                        agentConfig = _agentConfig.value
                    )
                } catch (_: Exception) {
                    emptyList()
                }
                if (leadsFromPhoto.isNotEmpty()) {
                    val labeled = leadsFromPhoto.map { lead ->
                        lead.copy(
                            source = "CARD_SCAN",
                            sourceDetail = "Card Image #${position + 1}",
                            syncStatus = "NOT_SAVED"
                        )
                    }
                    newlyExtracted.addAll(labeled)
                }
            }

            _isExtracting.value = false

            if (newlyExtracted.isNotEmpty()) {
                _noCardRecognizedAlert.value = null
                _isCurrentPhotoExtracted.value = true
                val (combinedLeads, newlyAddedCount) = combineCardsInMemoryForExcel(
                    candidates = newlyExtracted,
                    appendToExisting = appendToExisting
                )
                _cardBatchLeads.value = combinedLeads

                val primary = combinedLeads.lastOrNull() ?: combinedLeads.first()
                _lastExtractionResult.value = ExtractionResult(
                    name = primary.name,
                    phone = primary.phone,
                    email = primary.email,
                    company = primary.company,
                    jobTitle = primary.jobTitle,
                    website = primary.website,
                    address = primary.address,
                    notes = if (combinedLeads.size > 1) "Converted ${combinedLeads.size} cards directly to Excel." else primary.notes,
                    confidence = primary.confidence,
                    success = true
                )

                val totalCount = combinedLeads.size
                _extractionStep.value = "Extracted $totalCount card(s) ready in memory (No data saved)"
                _bannerMessage.value = if (appendToExisting) {
                    "Added $newlyAddedCount card(s) to memory (${totalCount} cards total). No data saved."
                } else {
                    "Extracted $totalCount card(s) in memory. No data saved to storage or database."
                }
            } else {
                if (!appendToExisting) {
                    _cardBatchLeads.value = emptyList()
                    updateSessionLeads(emptyList())
                    _isCurrentPhotoExtracted.value = false
                }
                _lastExtractionResult.value = null
                _extractionStep.value = "There is no card"
                _bannerMessage.value = "There is no card"
                _noCardRecognizedAlert.value = "There is no card"
                showNoCardSystemNotification(getApplication())
            }
        }
    }

    fun removeCapturedImageAt(index: Int) {
        val current = _capturedCardBitmaps.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _capturedCardBitmaps.value = current
            _selectedCardBitmap.value = current.lastOrNull()
        }
    }

    /**
     * "+ Add One More Card" (Manual Entry) — appends 1 card (even if repeated) to the Excel conversion sheet without saving to DB
     */
    fun addManualCardToBatch(
        name: String,
        jobTitle: String,
        company: String,
        phone: String,
        email: String,
        website: String,
        address: String,
        notes: String
    ) {
        if (name.isBlank() && company.isBlank() && phone.isBlank() && email.isBlank()) {
            _bannerMessage.value = "Please enter at least a name, company, phone, or email for the card."
            return
        }
        viewModelScope.launch {
            val nextNumber = _cardBatchLeads.value.size + 1
            val manualLead = ExtractedLead(
                name = name.trim().ifBlank { "Contact #$nextNumber" },
                jobTitle = jobTitle.trim(),
                company = company.trim(),
                phone = phone.trim(),
                email = email.trim(),
                website = website.trim(),
                address = address.trim(),
                notes = notes.trim().ifBlank { "Card #$nextNumber" },
                source = "CARD_SCAN",
                sourceDetail = "Card #$nextNumber",
                confidence = 99,
                syncStatus = "NOT_SAVED"
            )
            val (updatedBatch, _) = combineCardsInMemoryForExcel(
                candidates = listOf(manualLead),
                appendToExisting = true
            )
            _cardBatchLeads.value = updatedBatch
            _isCurrentPhotoExtracted.value = true
            _bannerMessage.value = "Added Card #$nextNumber to memory (${updatedBatch.size} cards total). No data saved."
        }
    }

    /**
     * Remove a single card row from the current Excel preview
     */
    fun removeCardFromBatch(lead: ExtractedLead) {
        val updated = _cardBatchLeads.value.filterNot { it.id == lead.id }
        _cardBatchLeads.value = updated
        updateSessionLeads(updated)
        _bannerMessage.value = "Removed ${lead.name} from Excel preview (${updated.size} remaining)."
    }

    fun clearCardBatch() {
        _cardBatchLeads.value = emptyList()
        updateSessionLeads(emptyList())
        _selectedCardBitmap.value = null
        _capturedCardBitmaps.value = emptyList()
        _lastExtractionResult.value = null
        _isCurrentPhotoExtracted.value = false
        _noCardRecognizedAlert.value = null
        _downloadedFileResult.value = null
        cleanupCameraTempFiles()
    }

    private fun cleanupCameraTempFiles() {
        try {
            val cameraDir = File(getApplication<Application>().cacheDir, "camera")
            if (cameraDir.exists()) {
                cameraDir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {
        }
    }

    fun syncCardBatchToSheets() {
        val leads = _cardBatchLeads.value
        if (leads.isEmpty()) {
            _bannerMessage.value = "No cards to export."
            return
        }
        downloadExcelOffline(getApplication(), leads, isCsv = false)
    }

    fun saveCurrentResultToDbAndSync(syncNow: Boolean = false) {
        // Never save to database — convert directly to Excel (.xlsx) instead
        val result = _lastExtractionResult.value ?: return
        val lead = ExtractedLead(
            id = 1L,
            name = result.name,
            phone = result.phone,
            email = result.email,
            company = result.company,
            jobTitle = result.jobTitle,
            website = result.website,
            address = result.address,
            notes = result.notes,
            source = _lastExtractionSource.value,
            sourceDetail = _lastExtractionSourceDetail.value,
            confidence = result.confidence
        )
        downloadExcelOffline(getApplication(), listOf(lead), isCsv = false)
    }

    fun syncLeadToGoogleSheets(lead: ExtractedLead) {
        downloadExcelOffline(getApplication(), listOf(lead), isCsv = false)
    }

    fun syncAllPendingLeads() {
        val leads = _cardBatchLeads.value.ifEmpty { _bulkExtractedLeads.value }
        if (leads.isNotEmpty()) {
            downloadExcelOffline(getApplication(), leads, isCsv = false)
        }
    }

    // ==========================================
    // Bulk Upload & Direct Excel Conversion Operations (PDF .pdf, Word .docx/.doc)
    // ==========================================

    fun addDocumentsFromUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _selectedDocuments.value.toMutableList()
            for (uri in uris) {
                val parsed = documentParser.parseDocument(context, uri)
                list.add(parsed)
            }
            _selectedDocuments.value = list
            _bannerMessage.value = "Selected ${uris.size} document(s) for Excel conversion."
        }
    }

    fun removeDocument(index: Int) {
        val list = _selectedDocuments.value.toMutableList()
        if (index in list.indices) {
            val removed = list.removeAt(index)
            _selectedDocuments.value = list
            _bannerMessage.value = "Removed ${removed.fileName}"
        }
    }

    fun clearBulkDocuments() {
        _selectedDocuments.value = emptyList()
        _bulkExtractedLeads.value = emptyList()
        _bulkCsvContent.value = ""
        _bulkBatchCompleted.value = false
        _bulkProgress.value = ""
        _downloadedFileResult.value = null
        cleanupCameraTempFiles()
    }

    fun clearBulkBatchResults() {
        _bulkExtractedLeads.value = emptyList()
        _bulkCsvContent.value = ""
        _bulkBatchCompleted.value = false
        _downloadedFileResult.value = null
    }

    /**
     * Analyzes all selected PDF & Word documents, extracts all contact details,
     * and keeps them in memory for Excel (.xlsx) & CSV export without saving any data to a database.
     */
    fun analyzeBulkDocumentsAndStoreInDba(
        context: Context? = null,
        autoDownloadCsv: Boolean = false
    ) {
        val docs = _selectedDocuments.value
        if (docs.isEmpty()) {
            _bannerMessage.value = "Please select at least one PDF document."
            return
        }

        viewModelScope.launch {
            _isBulkExtracting.value = true
            _bulkBatchCompleted.value = false
            _bulkProgressTotal.value = docs.size
            _bulkProgressCurrent.value = 0

            val allExtractedFromBatch = mutableListOf<ExtractedLead>()

            for ((index, doc) in docs.withIndex()) {
                _bulkProgressCurrent.value = index + 1
                _bulkProgress.value = "Analyzing PDF (${index + 1}/${docs.size}): ${doc.fileName}..."

                val extractedForDoc = geminiService.extractLeadsFromDocument(
                    document = doc,
                    agentConfig = _agentConfig.value
                )
                allExtractedFromBatch.addAll(extractedForDoc)
            }

            val deduplicated = geminiService.deduplicateLeadsOneTimeOnly(allExtractedFromBatch)
            val updatedLeadsWithIds = deduplicated.mapIndexed { idx, lead ->
                lead.copy(id = (idx + 1).toLong(), syncStatus = "NOT_SAVED")
            }

            _bulkExtractedLeads.value = updatedLeadsWithIds
            updateSessionLeads(updatedLeadsWithIds)

            val csvText = excelExportService.generateExcelCsv(updatedLeadsWithIds)
            _bulkCsvContent.value = csvText

            _isBulkExtracting.value = false
            _bulkBatchCompleted.value = true

            if (updatedLeadsWithIds.isNotEmpty()) {
                _bulkProgress.value = "Extracted ${updatedLeadsWithIds.size} contacts from ${docs.size} PDF(s) in memory. No data saved."
                _bannerMessage.value = "Extracted ${updatedLeadsWithIds.size} contacts in memory. No data saved to storage."
            } else {
                _bulkProgress.value = "No contact details found in the selected PDF(s)."
                _bannerMessage.value = "No contact details found in the selected PDF(s)."
            }
        }
    }

    fun syncBulkBatchToSheets() {
        val leads = _bulkExtractedLeads.value
        if (leads.isEmpty()) {
            _bannerMessage.value = "No extracted contacts to export."
            return
        }
        downloadExcelOffline(getApplication(), leads, isCsv = false)
    }

    fun deleteLead(lead: ExtractedLead) {
        val updated = _cardBatchLeads.value.filterNot { it.id == lead.id && it.name == lead.name && it.email == lead.email }
        _cardBatchLeads.value = updated
        updateSessionLeads(updated)
        _bannerMessage.value = "Removed row from Excel preview."
    }

    fun startEditingLead(lead: ExtractedLead) {
        _editingLead.value = lead
    }

    fun dismissEditLeadDialog() {
        _editingLead.value = null
    }

    fun updateLead(lead: ExtractedLead) {
        val updatedBatch = _cardBatchLeads.value.map { if (it.id == lead.id) lead else it }
        _cardBatchLeads.value = updatedBatch
        updateSessionLeads(updatedBatch)
        _editingLead.value = null
        _bannerMessage.value = "Updated row in Excel preview."
    }

    fun clearAllLeads() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDatabase.getInstance(getApplication()).leadDao().clearAll()
            } catch (_: Exception) {
            }
        }
        clearCardBatch()
        clearBulkDocuments()
        _bannerMessage.value = "Cleared screen."
    }

    fun testGoogleSheetsConnection() {
        val url = _agentConfig.value.googleSheetWebhookUrl
        viewModelScope.launch {
            _isTestingConnection.value = true
            _connectionTestResult.value = null
            val result = sheetsSyncService.testConnection(url)
            _isTestingConnection.value = false
            if (result.isSuccess) {
                _connectionTestResult.value = result.getOrNull()
                _bannerMessage.value = "Google Sheets Webhook responded successfully!"
            } else {
                _connectionTestResult.value = "Error: ${result.exceptionOrNull()?.message}"
                _bannerMessage.value = "Connection test failed."
            }
        }
    }

    fun updateAgentConfig(newConfig: AgentConfig) {
        _agentConfig.value = newConfig
        saveAgentConfig(newConfig)
        _bannerMessage.value = "Updated Offline OCR configuration."
    }

    fun applyPreset(preset: ExtractionPreset) {
        val current = _agentConfig.value
        val updated = current.copy(
            agentName = preset.title,
            agentPersona = preset.description,
            customInstructions = preset.instructions,
            extractionDepth = preset.depth
        )
        updateAgentConfig(updated)
    }

    fun getExportCsvString(leads: List<ExtractedLead>? = null): String {
        val targets = leads ?: _cardBatchLeads.value.ifEmpty { _bulkExtractedLeads.value }
        return excelExportService.generateExcelCsv(targets)
    }

    fun getExportExcelXmlString(leads: List<ExtractedLead>? = null): String {
        val targets = leads ?: _cardBatchLeads.value.ifEmpty { _bulkExtractedLeads.value }
        return excelExportService.generateExcelXml(targets, _agentConfig.value.spreadsheetName)
    }

    fun getExportTsvString(leads: List<ExtractedLead>? = null): String {
        val targets = leads ?: _cardBatchLeads.value.ifEmpty { _bulkExtractedLeads.value }
        return excelExportService.generateExcelTsv(targets)
    }

    fun createExcelShareableUri(context: Context, leads: List<ExtractedLead>? = null): Uri {
        val targets = leads ?: _cardBatchLeads.value.ifEmpty { _bulkExtractedLeads.value }
        return excelExportService.createExcelShareableUri(context, targets)
    }

    fun createCsvShareableUri(context: Context, leads: List<ExtractedLead>? = null): Uri {
        val targets = leads ?: _cardBatchLeads.value.ifEmpty { _bulkExtractedLeads.value }
        return excelExportService.createCsvShareableUri(context, targets)
    }

    fun writeLeadsToDocumentUri(context: Context, uri: Uri, isExcel: Boolean, leads: List<ExtractedLead>? = null) {
        val targets = leads ?: _cardBatchLeads.value.ifEmpty { _bulkExtractedLeads.value }
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                if (isExcel) {
                    excelExportService.writeXlsxWorkbook(os, targets, _agentConfig.value.spreadsheetName)
                } else {
                    val content = excelExportService.generateExcelCsv(targets)
                    java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8).use { writer ->
                        writer.write(content)
                        writer.flush()
                    }
                }
            }
            _bannerMessage.value = if (isExcel) "Converted to Excel (.xlsx) file!" else "Converted to CSV (.csv) file!"
        } catch (e: Exception) {
            _bannerMessage.value = "Export failed: ${e.localizedMessage ?: e.message}"
        }
    }

    /**
     * Converts extracted contacts directly into a formatted Excel (.xlsx) spreadsheet
     * in the device's public Downloads directory without saving any data in the app.
     */
    fun downloadExcelOffline(context: Context, leads: List<ExtractedLead>? = null, isCsv: Boolean = false): DownloadResult {
        val targets = leads ?: _cardBatchLeads.value.ifEmpty { _bulkExtractedLeads.value }
        val result = excelExportService.downloadExcelToDownloads(
            context = context,
            leads = targets,
            sheetTitle = _agentConfig.value.spreadsheetName,
            isCsv = isCsv
        )
        if (result.success) {
            _downloadedFileResult.value = result
            _bannerMessage.value = "Converted & downloaded ${result.fileName} to Downloads! (No data saved in app)"
        } else {
            _bannerMessage.value = result.message
        }
        return result
    }

    fun openDownloadedFile(context: Context, result: DownloadResult) {
        val uri = result.uri ?: return
        excelExportService.openExcelFile(context, uri, !result.isExcel)
    }

    fun shareDownloadedFile(context: Context, result: DownloadResult) {
        val uri = result.uri ?: return
        excelExportService.shareDownloadedFile(context, uri, result.fileName, !result.isExcel)
    }

    fun dismissDownloadedFileNotice() {
        _downloadedFileResult.value = null
    }

    fun getAppsScriptCode(): String {
        return sheetsSyncService.generateAppsScriptSnippet()
    }

    private fun loadAgentConfig(): AgentConfig {
        return AgentConfig(
            agentName = prefs.getString("agent_name", "Card Scanner") ?: "Card Scanner",
            agentPersona = prefs.getString("agent_persona", "English Card & Document to Excel (.xlsx) Converter") ?: "English Card & Document to Excel (.xlsx) Converter",
            customInstructions = prefs.getString("agent_instructions", "Extract accurate full names, business emails, direct phone numbers, job titles, and company affiliations offline and convert directly into Excel.") ?: "",
            extractionDepth = prefs.getString("agent_depth", "DETAILED") ?: "DETAILED",
            autoSyncToSheets = false,
            googleSheetWebhookUrl = prefs.getString("sheets_webhook_url", "") ?: "",
            spreadsheetName = prefs.getString("sheets_doc_name", "Extracted Contacts") ?: "Extracted Contacts",
            sheetTabName = prefs.getString("sheets_tab_name", "Contacts") ?: "Contacts",
            customApiKey = ""
        )
    }

    private fun saveAgentConfig(config: AgentConfig) {
        prefs.edit()
            .putString("agent_name", config.agentName)
            .putString("agent_persona", config.agentPersona)
            .putString("agent_instructions", config.customInstructions)
            .putString("agent_depth", config.extractionDepth)
            .putBoolean("auto_sync", false)
            .putString("sheets_webhook_url", config.googleSheetWebhookUrl)
            .putString("sheets_doc_name", config.spreadsheetName)
            .putString("sheets_tab_name", config.sheetTabName)
            .remove("custom_api_key")
            .apply()
    }
}
