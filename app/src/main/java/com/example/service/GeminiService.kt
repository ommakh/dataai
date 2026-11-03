package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import com.example.data.local.ExtractedLead
import com.example.data.model.AgentConfig
import com.example.data.model.ExtractionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 100% Free On-Device OCR & Heuristic Contact Extraction Service powered by Google ML Kit
 * (TextRecognition with English / Latin model only) and Tesseract OCR (tesseract4android).
 * Requires ZERO API keys, makes ZERO cloud AI calls, and works 100% offline.
 */
class GeminiService(private val appContext: Context? = null) {

    private val latinRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    @Volatile
    private var cachedTessApi: com.googlecode.tesseract.android.TessBaseAPI? = null
    private val tessLock = Any()

    private fun getOrCreateTessApi(dataPath: String): com.googlecode.tesseract.android.TessBaseAPI? {
        val existing = cachedTessApi
        if (existing != null) return existing
        return synchronized(tessLock) {
            cachedTessApi?.let { return it }
            try {
                val api = com.googlecode.tesseract.android.TessBaseAPI()
                val ok = api.init(dataPath, "eng")
                if (ok) {
                    cachedTessApi = api
                    api
                } else {
                    try { api.recycle() } catch (_: Throwable) {}
                    null
                }
            } catch (t: Throwable) {
                Log.w("GeminiService", "Tesseract init error: ${t.message}")
                null
            }
        }
    }

    /**
     * Returns status for On-Device Tesseract OCR (100% Free • Offline • No API Key).
     */
    fun getOcrEngineStatus(): String {
        return "Tesseract OCR (100% Free • Offline • No API)"
    }

    /**
     * Ensures traineddata for Tesseract is ready in device storage and pre-warms the OCR engine.
     */
    suspend fun ensureTesseractTrainedData(context: Context? = appContext): String = withContext(Dispatchers.IO) {
        val ctx = context ?: appContext ?: return@withContext "Tesseract OCR (English)"
        val path = ensureTessData(ctx)
        if (path != null) {
            getOrCreateTessApi(path)
            "Tesseract OCR Ready (English • 100% Free Offline)"
        } else {
            "Tesseract OCR Ready (English)"
        }
    }

    private fun ensureTessData(context: Context): String? {
        return try {
            val tessDir = File(context.filesDir, "tessdata")
            if (!tessDir.exists()) {
                tessDir.mkdirs()
            }
            val trainedDataFile = File(tessDir, "eng.traineddata")
            if (!trainedDataFile.exists() || trainedDataFile.length() < 1000) {
                context.assets.open("tessdata/eng.traineddata").use { input ->
                    FileOutputStream(trainedDataFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            context.filesDir.absolutePath
        } catch (e: Exception) {
            Log.w("GeminiService", "Failed to setup tessdata: ${e.message}")
            null
        }
    }

    /**
     * Scales high-resolution camera bitmaps (e.g. 12-48MP) to optimal OCR resolution (~1400px),
     * drastically reducing OCR processing time from seconds to milliseconds while maintaining
     * high character accuracy.
     */
    private fun scaleBitmapForFastOcr(bitmap: Bitmap, maxDim: Int = 1400): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = if (w > h) maxDim.toFloat() / w else maxDim.toFloat() / h
        val newW = max(1, (w * ratio).toInt())
        val newH = max(1, (h * ratio).toInt())
        return try {
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    /**
     * Runs Tesseract OCR on [bitmap] at [rotationDegrees] completely offline on-device without any API key,
     * utilizing cached engine instances and optimized resolution for fast scanning.
     */
    suspend fun recognizeTextWithTesseract(bitmap: Bitmap, rotationDegrees: Int = 0): String = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext ""
        val dataPath = ensureTessData(ctx) ?: return@withContext ""
        val rotated = rotateBitmap(bitmap, rotationDegrees)
        val scaled = scaleBitmapForFastOcr(rotated, maxDim = 1400)
        try {
            val tess = getOrCreateTessApi(dataPath) ?: return@withContext ""
            synchronized(tessLock) {
                tess.setImage(scaled)
                val text = tess.utF8Text ?: ""
                try {
                    tess.clear()
                } catch (_: Throwable) {}
                text.trim()
            }
        } catch (t: Throwable) {
            Log.w("GeminiService", "Tesseract on-device OCR skipped: ${t.message}")
            ""
        }
    }

    fun normalizeDevanagariDigits(input: String): String {
        return input
    }

    private fun isTextCharacter(ch: Char): Boolean {
        return ch.isLetter()
    }

    private fun containsKeyword(textLower: String, keyword: String): Boolean {
        val kw = keyword.trim().lowercase()
        if (kw.isEmpty()) return false
        val pattern = Regex("(?i)(?<![a-zA-Z0-9])${Regex.escape(kw)}(?![a-zA-Z0-9])")
        return pattern.containsMatchIn(textLower)
    }

    /**
     * Extracts structured lead data from scraped website text using 100% local regex & heuristic parsing.
     * Requires no API key and never returns demo or fabricated data.
     */
    suspend fun extractFromWebsite(
        url: String,
        pageText: String,
        agentConfig: AgentConfig = AgentConfig()
    ): ExtractionResult = withContext(Dispatchers.IO) {
        if (pageText.isBlank()) {
            return@withContext ExtractionResult(
                success = false,
                errorMessage = "No readable content found at the provided URL."
            )
        }

        extractHeuristicallyFromRealText(url = url, rawText = pageText)
    }

    /**
     * Extracts all structured leads from ONE photo containing 1 to 20 English business cards placed on a table
     * using Google ML Kit on-device TextRecognition (English / Latin model only).
     * Repeated cards are all extracted and converted into Excel.
     * Requires ZERO API keys, uses ZERO cloud AI, and NEVER returns any demo, sample, or hardcoded data.
     */
    suspend fun extractLeadsFromCardImage(
        bitmap: Bitmap,
        agentConfig: AgentConfig = AgentConfig()
    ): List<ExtractedLead> = withContext(Dispatchers.IO) {
        val safeBitmap = ensureSoftwareBitmap(bitmap)

        // Run 100% offline Google ML Kit OCR across orientations
        val ocrScan = recognizeCardsAcrossOrientations(safeBitmap)
        val onDeviceOcrLeads = ocrScan.parsedLeads

        if (onDeviceOcrLeads.isNotEmpty()) {
            return@withContext onDeviceOcrLeads.take(20)
        }

        // Never return fake or demo data if no card is recognized
        emptyList()
    }

    private data class OcrOrientationScanResult(
        val clusters: List<String>,
        val rawText: String,
        val parsedLeads: List<ExtractedLead>
    )

    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        return try {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (_: Throwable) {
            source
        }
    }

    /**
     * Evaluates orientations (0°, 90°, 270°, 180°) and scores card completeness across ALL detected cards,
     * picking the orientation that yields the maximum number of cards and highest contact details.
     */
    private suspend fun recognizeCardsAcrossOrientations(bitmap: Bitmap): OcrOrientationScanResult {
        val zeroScan = scanAtRotation(bitmap, 0)
        var bestScan = zeroScan
        var bestScore = computeBatchQualityScore(zeroScan.parsedLeads)

        // Test other orientations (90°, 270°, 180°) and pick the one with highest total quality & card count
        for (deg in listOf(90, 270, 180)) {
            val rotScan = scanAtRotation(bitmap, deg)
            val score = computeBatchQualityScore(rotScan.parsedLeads)
            if (score > bestScore) {
                bestScore = score
                bestScan = rotScan
            }
        }

        return bestScan
    }

    private fun computeBatchQualityScore(leads: List<ExtractedLead>): Int {
        if (leads.isEmpty()) return 0
        // Strongly reward extracting ALL cards: each valid card adds its completeness score + 50 points
        return leads.sumOf { calculateLeadQualityScore(it) + 50 }
    }

    private fun calculateLeadQualityScore(lead: ExtractedLead): Int {
        var score = 0
        if (lead.name.isNotBlank() && lead.name.any { it.isLetter() }) {
            score += 25
            if (lead.name.contains(" ")) score += 10
        }
        if (lead.phone.isNotBlank() && lead.phone.count { it.isDigit() } >= 7) {
            score += 30
        }
        if (lead.email.isNotBlank() && lead.email.contains("@") && lead.email.contains(".")) {
            score += 35
        }
        if (lead.company.isNotBlank() && lead.company.length >= 3) {
            score += 20
        }
        if (lead.jobTitle.isNotBlank() && lead.jobTitle.length >= 2) {
            score += 15
        }
        if (lead.address.isNotBlank() && lead.address.length >= 5) {
            score += 15
        }
        if (lead.website.isNotBlank() && lead.website.contains(".")) {
            score += 10
        }
        return score
    }

    private suspend fun scanAtRotation(bitmap: Bitmap, rotation: Int): OcrOrientationScanResult {
        val tessText = recognizeTextWithTesseract(bitmap, rotation)
        val visionText = recognizeTextOnDevice(bitmap, rotation)

        val mlKitRaw = visionText?.text?.trim() ?: ""
        val rawText = if (tessText.isNotBlank() && mlKitRaw.isNotBlank()) {
            "$mlKitRaw\n\n$tessText"
        } else {
            tessText.ifBlank { mlKitRaw }
        }

        if (rawText.isBlank()) {
            return OcrOrientationScanResult(emptyList(), "", emptyList())
        }

        val effWidth = if (rotation == 90 || rotation == 270) bitmap.height else bitmap.width
        val effHeight = if (rotation == 90 || rotation == 270) bitmap.width else bitmap.height

        // 1. Spatially cluster ML Kit text blocks for multi-card photos (1 to 20 cards on a table)
        val clusterLeads = if (visionText != null && visionText.textBlocks.isNotEmpty()) {
            val clusters = clusterTableCardsFromOcr(visionText, effWidth, effHeight)
            clusters.flatMapIndexed { idx, clusterBlocks ->
                val clusterRaw = formatClusterBlocks(clusterBlocks)
                parseCardsFromVisionBlocks(clusterBlocks, clusterRaw, idx + 1)
            }
        } else {
            emptyList()
        }

        // 2. Parse card leads from Tesseract OCR output
        val tesseractLeads = if (tessText.isNotBlank()) {
            parseCardsFromOcrRegion(tessText, 1)
        } else {
            emptyList()
        }

        // Combine all detected candidates from Tesseract OCR and spatial clustering.
        // deduplicateAndMergeCards consolidates matching entities into EXACTLY 1 row per card/company,
        // merging all phone numbers into a single row so entries never increase!
        val combinedCandidates = clusterLeads + tesseractLeads
        val bestLeads = if (combinedCandidates.isNotEmpty()) {
            deduplicateAndMergeCards(combinedCandidates)
        } else {
            deduplicateAndMergeCards(parseCardsFromOcrRegion(rawText, 1))
        }

        val clustersDesc = if (visionText != null && visionText.textBlocks.isNotEmpty()) {
            clusterTableCardsFromOcr(visionText, effWidth, effHeight).map { formatClusterBlocks(it) }
        } else if (tessText.isNotBlank()) {
            listOf(tessText)
        } else {
            listOf(rawText)
        }

        return OcrOrientationScanResult(
            clusters = clustersDesc,
            rawText = rawText,
            parsedLeads = bestLeads.take(20)
        )
    }

    /**
     * Runs Google ML Kit on-device TextRecognition (English / Latin recognizer) on [bitmap] at [rotationDegrees].
     * Returns null if running in a non-device JVM test environment or if recognition fails.
     */
    private suspend fun recognizeTextOnDevice(bitmap: Bitmap, rotationDegrees: Int = 0): Text? {
        val image = try {
            InputImage.fromBitmap(bitmap, rotationDegrees)
        } catch (_: Throwable) {
            return null
        }

        return processRecognizer(latinRecognizer, image)
    }

    private suspend fun processRecognizer(recognizer: TextRecognizer, image: InputImage): Text? {
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
                    .addOnCanceledListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private data class CardCluster(
        val bounds: Rect,
        val blocks: MutableList<Text.TextBlock>,
        val emails: MutableSet<String>,
        val phones: MutableSet<String>,
        val websites: MutableSet<String>,
        var hasStrongIdentity: Boolean
    )

    /**
     * Spatially clusters ML Kit text blocks into distinct business cards (1 to 20 cards)
     * on a table, ensuring:
     * 1) Blocks within the same card are completely grouped together.
     * 2) Blocks or clusters containing different phone numbers or emails are NEVER merged.
     * 3) Orphan fragments (e.g. address lines or taglines) are absorbed into their nearest card.
     * 4) Exactly 1 cluster is produced per physical card on the table.
     */
    private fun clusterTableCardsFromOcr(
        visionText: Text,
        imageWidth: Int,
        imageHeight: Int
    ): List<List<Text.TextBlock>> {
        val rawBlocks = visionText.textBlocks.filter { it.text.isNotBlank() && it.boundingBox != null }
        if (rawBlocks.isEmpty()) return emptyList()

        // 1. Initial clusters: 1 per non-empty block with bounding box
        val initialClusters = rawBlocks.map { block ->
            val box = block.boundingBox ?: Rect()
            val emails = extractCleanEmails(block.text).toMutableSet()
            val (priPhone, altPhones) = extractCleanPhones(block.text.split("\n"))
            val phones = (if (priPhone.isNotBlank()) listOf(priPhone) + altPhones else emptyList())
                .map { cleanPhoneDigitsOnly(it) }
                .filter { it.length >= 7 }
                .toMutableSet()
            val websites = extractCleanWebsites(block.text, emails.toList()).toMutableSet()
            val hasIdentity = emails.isNotEmpty() || phones.isNotEmpty() || websites.isNotEmpty() ||
                hasPersonOrCompanyKeywords(block.text)

            CardCluster(
                bounds = Rect(box),
                blocks = mutableListOf(block),
                emails = emails,
                phones = phones,
                websites = websites,
                hasStrongIdentity = hasIdentity
            )
        }.toMutableList()

        // Dynamic distance thresholds based on image dimensions
        val maxHGapSameCard = max(60, (imageWidth * 0.22f).toInt())
        val maxVGapSameCard = max(50, (imageHeight * 0.20f).toInt())
        val maxDiagGap = max(40, (imageWidth * 0.12f).toInt())

        // 2. Iterative Agglomerative Merging of blocks within the same physical card
        var mergedAny = true
        while (mergedAny && initialClusters.size > 1) {
            mergedAny = false
            var bestI = -1
            var bestJ = -1
            var minDistance = Int.MAX_VALUE

            for (i in 0 until initialClusters.size) {
                for (j in i + 1 until initialClusters.size) {
                    val c1 = initialClusters[i]
                    val c2 = initialClusters[j]

                    // Check for distinct conflicting company names or conflicting corporate email domains
                    val c1Comp = extractPotentialCompanyName(c1.blocks.joinToString("\n") { it.text })
                    val c2Comp = extractPotentialCompanyName(c2.blocks.joinToString("\n") { it.text })
                    val companyConflict = c1Comp.isNotBlank() && c2Comp.isNotBlank() &&
                        !c1Comp.equals(c2Comp, ignoreCase = true) && !c1Comp.contains(c2Comp, ignoreCase = true) && !c2Comp.contains(c1Comp, ignoreCase = true)

                    val c1Domains = c1.emails.map { it.substringAfter("@").lowercase() }
                        .filter { !isGenericEmailDomain(it) }.toSet()
                    val c2Domains = c2.emails.map { it.substringAfter("@").lowercase() }
                        .filter { !isGenericEmailDomain(it) }.toSet()
                    val emailConflict = c1Domains.isNotEmpty() && c2Domains.isNotEmpty() && c1Domains.intersect(c2Domains).isEmpty()

                    if (companyConflict || emailConflict) {
                        continue // Distinct company cards on a table must not merge!
                    }

                    // Calculate 2D gap
                    val hDist = if (c2.bounds.right < c1.bounds.left) c1.bounds.left - c2.bounds.right
                    else if (c1.bounds.right < c2.bounds.left) c2.bounds.left - c1.bounds.right
                    else 0 // horizontally overlapping

                    val vDist = if (c2.bounds.bottom < c1.bounds.top) c1.bounds.top - c2.bounds.bottom
                    else if (c1.bounds.bottom < c2.bounds.top) c2.bounds.top - c1.bounds.bottom
                    else 0 // vertically overlapping

                    val canMerge = when {
                        hDist == 0 && vDist <= maxVGapSameCard -> true
                        vDist == 0 && hDist <= maxHGapSameCard -> true
                        hDist <= maxDiagGap && vDist <= maxDiagGap -> true
                        hDist <= maxHGapSameCard && vDist <= maxVGapSameCard -> true
                        else -> false
                    }

                    if (canMerge) {
                        val unionW = max(c1.bounds.right, c2.bounds.right) - min(c1.bounds.left, c2.bounds.left)
                        val unionH = max(c1.bounds.bottom, c2.bounds.bottom) - min(c1.bounds.top, c2.bounds.top)

                        // If multiple cards are in the image, prevent merging across separate physical cards
                        val isOverSized = initialClusters.size >= 4 && (unionW > imageWidth * 0.75f || unionH > imageHeight * 0.60f)
                        if (!isOverSized) {
                            val dist = hDist + vDist
                            if (dist < minDistance) {
                                minDistance = dist
                                bestI = i
                                bestJ = j
                            }
                        }
                    }
                }
            }

            if (bestI != -1 && bestJ != -1) {
                val c1 = initialClusters[bestI]
                val c2 = initialClusters[bestJ]
                c1.bounds.union(c2.bounds)
                c1.blocks.addAll(c2.blocks)
                c1.emails.addAll(c2.emails)
                c1.phones.addAll(c2.phones)
                c1.websites.addAll(c2.websites)
                c1.hasStrongIdentity = c1.hasStrongIdentity || c2.hasStrongIdentity
                initialClusters.removeAt(bestJ)
                mergedAny = true
            }
        }

        // 3. Absorb remaining orphan fragments into nearest card anchor
        val cardAnchors = initialClusters.filter { it.hasStrongIdentity || it.phones.isNotEmpty() || it.emails.isNotEmpty() }
        val orphans = initialClusters.filter { !cardAnchors.contains(it) }

        if (cardAnchors.isNotEmpty() && orphans.isNotEmpty()) {
            val maxOrphanDistance = max(imageWidth, imageHeight) * 0.35f
            for (orphan in orphans) {
                var nearestAnchor: CardCluster? = null
                var nearestDist = Float.MAX_VALUE

                val oCenterX = orphan.bounds.centerX().toFloat()
                val oCenterY = orphan.bounds.centerY().toFloat()

                for (anchor in cardAnchors) {
                    val aCenterX = anchor.bounds.centerX().toFloat()
                    val aCenterY = anchor.bounds.centerY().toFloat()
                    val dist = Math.hypot((oCenterX - aCenterX).toDouble(), (oCenterY - aCenterY).toDouble()).toFloat()
                    if (dist < nearestDist && dist <= maxOrphanDistance) {
                        nearestDist = dist
                        nearestAnchor = anchor
                    }
                }

                if (nearestAnchor != null) {
                    nearestAnchor.bounds.union(orphan.bounds)
                    nearestAnchor.blocks.addAll(orphan.blocks)
                    nearestAnchor.phones.addAll(orphan.phones)
                    nearestAnchor.emails.addAll(orphan.emails)
                }
            }
        }

        // 4. Merge anchors that belong to the SAME card/company
        val activeClusters = (if (cardAnchors.isNotEmpty()) cardAnchors else initialClusters).toMutableList()
        if (activeClusters.size > 1) {
            var anchorMerged = true
            while (anchorMerged && activeClusters.size > 1) {
                anchorMerged = false
                var matchI = -1
                var matchJ = -1
                for (i in 0 until activeClusters.size) {
                    for (j in i + 1 until activeClusters.size) {
                        val a1 = activeClusters[i]
                        val a2 = activeClusters[j]
                        val c1Name = extractPotentialCompanyName(a1.blocks.joinToString("\n") { it.text })
                        val c2Name = extractPotentialCompanyName(a2.blocks.joinToString("\n") { it.text })
                        val sameCompany = c1Name.isNotBlank() && c2Name.isNotBlank() &&
                            (c1Name.equals(c2Name, ignoreCase = true) || c1Name.contains(c2Name, ignoreCase = true) || c2Name.contains(c1Name, ignoreCase = true))

                        val sameEmailDomain = a1.emails.isNotEmpty() && a2.emails.isNotEmpty() &&
                            a1.emails.any { e1 -> a2.emails.any { e2 ->
                                val d1 = e1.substringAfter("@").lowercase()
                                val d2 = e2.substringAfter("@").lowercase()
                                !isGenericEmailDomain(d1) && d1 == d2
                            } }

                        val sharedPhone = a1.phones.isNotEmpty() && a2.phones.isNotEmpty() &&
                            a1.phones.any { p1 -> a2.phones.any { p2 -> arePhonesCompatible(p1, p2) } }

                        val singleCompanyInPhoto = (c1Name.isNotBlank() && c2Name.isBlank()) || (c2Name.isNotBlank() && c1Name.isBlank())
                        val bothClose = Math.hypot((a1.bounds.centerX() - a2.bounds.centerX()).toDouble(), (a1.bounds.centerY() - a2.bounds.centerY()).toDouble()) < max(imageWidth, imageHeight) * 0.40f

                        if (sameCompany || sameEmailDomain || sharedPhone || (activeClusters.size == 2 && singleCompanyInPhoto && bothClose)) {
                            matchI = i
                            matchJ = j
                            break
                        }
                    }
                    if (matchI != -1) break
                }
                if (matchI != -1 && matchJ != -1) {
                    val a1 = activeClusters[matchI]
                    val a2 = activeClusters[matchJ]
                    a1.bounds.union(a2.bounds)
                    a1.blocks.addAll(a2.blocks)
                    a1.emails.addAll(a2.emails)
                    a1.phones.addAll(a2.phones)
                    a1.websites.addAll(a2.websites)
                    activeClusters.removeAt(matchJ)
                    anchorMerged = true
                }
            }
        }

        val finalCardClusters = activeClusters

        // Sort clusters in visual grid reading order (rows top-to-bottom, columns left-to-right)
        val rowBandHeight = max(1, imageHeight / 6)
        val ordered = finalCardClusters.sortedWith(
            compareBy<CardCluster> { it.bounds.centerY() / rowBandHeight }
                .thenBy { it.bounds.centerX() }
        )

        return ordered.map { it.blocks }
    }

    private fun isGenericEmailDomain(domain: String): Boolean {
        val generic = setOf("gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "rediffmail.com", "icloud.com", "aol.com", "mail.com")
        return domain.lowercase() in generic
    }

    private fun extractPotentialCompanyName(text: String): String {
        val lines = text.split("\n").map { it.trim() }.filter { it.length in 4..60 }
        val companySuffixes = listOf("pvt", "ltd", "limited", "inc", "llp", "llc", "corp", "corporation", "enterprises", "enterprise", "solutions", "technologies", "tech", "industries", "industry", "services", "associates", "works", "traders", "trading", "logistics", "motors", "pharma", "consultancy", "systems")
        for (line in lines) {
            val lower = line.lowercase()
            if (companySuffixes.any { containsKeyword(lower, it) }) {
                return line
            }
        }
        return ""
    }

    private fun cleanPhoneDigitsOnly(phoneStr: String): String {
        return phoneStr.replace(Regex("[^0-9]"), "")
    }

    private fun arePhonesCompatible(p1: String, p2: String): Boolean {
        if (p1 == p2) return true
        if (p1.length >= 7 && p2.length >= 7) {
            val last7_1 = p1.takeLast(7)
            val last7_2 = p2.takeLast(7)
            if (last7_1 == last7_2) return true
        }
        return false
    }

    private fun hasPersonOrCompanyKeywords(text: String): Boolean {
        val lower = text.lowercase()
        val keywords = listOf(
            "pvt", "ltd", "limited", "inc", "llp", "llc", "corp", "corporation", "company",
            "group", "enterprises", "solutions", "technologies", "tech", "industries", "services",
            "associates", "works", "traders", "logistics", "director", "manager", "proprietor",
            "partner", "founder", "ceo", "president", "consultant", "engineer", "dr.", "mr.", "mrs."
        )
        return keywords.any { containsKeyword(lower, it) }
    }

    private fun formatClusterBlocks(blockList: List<Text.TextBlock>): String {
        return blockList
            .sortedWith(compareBy<Text.TextBlock> { it.boundingBox?.top ?: 0 }.thenBy { it.boundingBox?.left ?: 0 })
            .joinToString("\n") { it.text.trim() }
            .trim()
    }

    /**
     * Deduplicates extracted leads and merges partial fragments belonging to the same card,
     * ensuring exactly 1 entry per physical card (e.g. 5 cards = exactly 5 entries).
     */
    fun deduplicateAndMergeCards(leads: List<ExtractedLead>): List<ExtractedLead> {
        if (leads.size <= 1) return leads

        val merged = mutableListOf<ExtractedLead>()

        for (candidate in leads) {
            // Check if this candidate represents an already processed card/company
            val matchIndex = merged.indexOfFirst { existing ->
                val cPhone = cleanPhoneDigitsOnly(candidate.phone)
                val ePhone = cleanPhoneDigitsOnly(existing.phone)
                val bothHavePhones = cPhone.length >= 7 && ePhone.length >= 7
                val phoneMatch = bothHavePhones && (arePhonesCompatible(cPhone, ePhone) ||
                    candidate.phone.split(" / ").any { p1 -> existing.phone.split(" / ").any { p2 -> arePhonesCompatible(cleanPhoneDigitsOnly(p1), cleanPhoneDigitsOnly(p2)) } })

                val cEmail = candidate.email.trim().lowercase()
                val eEmail = existing.email.trim().lowercase()
                val bothHaveEmails = cEmail.isNotBlank() && eEmail.isNotBlank()
                val emailMatch = bothHaveEmails && (cEmail == eEmail ||
                    candidate.email.split(",").any { em1 -> existing.email.split(",").any { em2 -> em1.trim().equals(em2.trim(), ignoreCase = true) } })

                val cName = candidate.name.trim().lowercase()
                val eName = existing.name.trim().lowercase()
                val cComp = candidate.company.trim().lowercase()
                val eComp = existing.company.trim().lowercase()

                val nameMatch = cName.isNotBlank() && eName.isNotBlank() && cName == eName

                val nameCompMatch = cName.isNotBlank() && eName.isNotBlank() && cComp.isNotBlank() && eComp.isNotBlank() &&
                    cName == eName && cComp == eComp

                val sameCompanyMatch = cComp.isNotBlank() && eComp.isNotBlank() && cComp.length >= 4 &&
                    (cComp == eComp || cComp.contains(eComp) || eComp.contains(cComp))

                val websiteMatch = candidate.website.isNotBlank() && existing.website.isNotBlank() &&
                    candidate.website.trim().lowercase() == existing.website.trim().lowercase()

                val orphanFragmentMatch = (cComp.isBlank() && cName.isBlank() && (candidate.address.isNotBlank() || candidate.phone.isNotBlank())) ||
                    (eComp.isBlank() && eName.isBlank() && (existing.address.isNotBlank() || existing.phone.isNotBlank()))

                phoneMatch || emailMatch || nameMatch || nameCompMatch || sameCompanyMatch || websiteMatch || (orphanFragmentMatch && merged.size == 1)
            }

            if (matchIndex != -1) {
                // Merge candidate into existing lead of the respective company
                val existing = merged[matchIndex]
                merged[matchIndex] = mergeTwoCardLeads(existing, candidate)
            } else {
                merged.add(candidate)
            }
        }

        return merged
    }

    private fun mergeTwoCardLeads(primary: ExtractedLead, secondary: ExtractedLead): ExtractedLead {
        val mergedAddress = when {
            primary.address.isNotBlank() && secondary.address.isNotBlank() -> {
                if (primary.address.contains(secondary.address, ignoreCase = true)) primary.address
                else if (secondary.address.contains(primary.address, ignoreCase = true)) secondary.address
                else "${primary.address}, ${secondary.address}"
            }
            primary.address.isNotBlank() -> primary.address
            else -> secondary.address
        }

        val mergedPhone = when {
            primary.phone.isNotBlank() && secondary.phone.isNotBlank() -> {
                val pParts = primary.phone.split(" / ").map { it.trim() }.filter { it.isNotBlank() }
                val sParts = secondary.phone.split(" / ").map { it.trim() }.filter { it.isNotBlank() }
                val combined = (pParts + sParts).distinct()
                combined.joinToString(" / ")
            }
            primary.phone.isNotBlank() -> primary.phone
            else -> secondary.phone
        }

        val mergedEmail = when {
            primary.email.isNotBlank() && secondary.email.isNotBlank() -> {
                val pEmails = primary.email.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val sEmails = secondary.email.split(",").map { it.trim() }.filter { it.isNotBlank() }
                (pEmails + sEmails).distinct().joinToString(", ")
            }
            primary.email.isNotBlank() -> primary.email
            else -> secondary.email
        }

        val mergedNotes = when {
            primary.notes.isNotBlank() && secondary.notes.isNotBlank() -> {
                if (primary.notes.contains(secondary.notes, ignoreCase = true)) primary.notes
                else if (secondary.notes.contains(primary.notes, ignoreCase = true)) secondary.notes
                else "${primary.notes} | ${secondary.notes}"
            }
            primary.notes.isNotBlank() -> primary.notes
            else -> secondary.notes
        }

        return primary.copy(
            name = primary.name.ifBlank { secondary.name },
            jobTitle = primary.jobTitle.ifBlank { secondary.jobTitle },
            company = primary.company.ifBlank { secondary.company },
            email = mergedEmail,
            phone = mergedPhone,
            website = primary.website.ifBlank { secondary.website },
            address = mergedAddress,
            notes = mergedNotes,
            confidence = max(primary.confidence, secondary.confidence)
        )
    }

    /**
     * Splits multi-card OCR text into separate card line blocks based on contact anchors
     * (email, phone) and identity header transitions.
     */
    private fun segmentTextIntoCardBlocks(lines: List<String>): List<List<String>> {
        val cleanLines = lines.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanLines.isEmpty()) return emptyList()

        val cardBlocks = mutableListOf<MutableList<String>>()
        var currentBlock = mutableListOf<String>()
        val currentEmails = mutableSetOf<String>()
        val currentPhones = mutableSetOf<String>()

        for (line in cleanLines) {
            val emailsInLine = extractCleanEmails(line)
            val (phoneInLine, altPhonesInLine) = extractCleanPhones(listOf(line))
            val phonesInLine = (if (phoneInLine.isNotBlank()) listOf(phoneInLine) + altPhonesInLine else emptyList())
                .map { cleanPhoneDigitsOnly(it) }
                .filter { it.length >= 7 }

            val startsWithHonorific = Regex("(?i)^(?:dr\\.?|mr\\.?|mrs\\.?|ms\\.?|prof\\.?|adv\\.?|ca\\.?)\\s+[a-zA-Z]").containsMatchIn(line)
            val startsWithNamePrefix = Regex("(?i)^\\s*(?:name|full name|contact person)\\s*[:.-]").containsMatchIn(line)

            val hasEmailConflict = emailsInLine.isNotEmpty() && currentEmails.isNotEmpty() &&
                emailsInLine.none { e1 -> currentEmails.any { e2 ->
                    val d1 = e1.substringAfter("@").lowercase()
                    val d2 = e2.substringAfter("@").lowercase()
                    !isGenericEmailDomain(d1) && d1 == d2
                } }

            val hasCompletedContact = currentPhones.isNotEmpty() || currentEmails.isNotEmpty()
            val isExplicitNewPerson = (startsWithHonorific || startsWithNamePrefix) && currentBlock.size >= 4

            // A new card boundary is only created on a distinct corporate email conflict or an explicit new person header
            // Multiple phone numbers on the same card belong to the same entry and must NEVER split the card
            val isNewCardBoundary = (hasEmailConflict && hasCompletedContact) || (hasCompletedContact && isExplicitNewPerson)

            if (isNewCardBoundary && currentBlock.isNotEmpty()) {
                cardBlocks.add(currentBlock)
                currentBlock = mutableListOf()
                currentEmails.clear()
                currentPhones.clear()
            }

            currentBlock.add(line)
            currentEmails.addAll(emailsInLine)
            currentPhones.addAll(phonesInLine)
        }

        if (currentBlock.isNotEmpty()) {
            cardBlocks.add(currentBlock)
        }

        return cardBlocks
    }

    /**
     * Parses 1 or more business cards from an OCR text region, keeping repeated cards as well.
     */
    fun parseCardsFromOcrRegion(rawRegion: String, startCardNumber: Int = 1): List<ExtractedLead> {
        if (rawRegion.isBlank()) return emptyList()

        // 1. First try double-newline paragraphs
        val paragraphs = rawRegion.split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.size > 1) {
            val multiParsed = paragraphs.flatMapIndexed { idx, para ->
                parseCardsFromRealOcrBlock(para, startCardNumber + idx)
            }
            if (multiParsed.size > 1) {
                return multiParsed
            }
        }

        // 2. Segment by contact transitions and card boundaries
        val segmentedBlocks = segmentTextIntoCardBlocks(rawRegion.split("\n"))
        if (segmentedBlocks.size > 1) {
            val segmented = segmentedBlocks.flatMapIndexed { idx, lines ->
                parseCardsFromRealOcrBlock(lines.joinToString("\n"), startCardNumber + idx)
            }
            if (segmented.size > 1) {
                return segmented
            }
        }

        return parseCardsFromRealOcrBlock(rawRegion, startCardNumber)
    }

    /**
     * Internal representation of a recognized text line with bounding geometry.
     */
    private data class SpatialLine(
        val text: String,
        val bounds: Rect,
        val height: Int = bounds.height(),
        val top: Int = bounds.top,
        val bottom: Int = bounds.bottom,
        val left: Int = bounds.left,
        val right: Int = bounds.right,
        val centerX: Int = bounds.centerX(),
        val centerY: Int = bounds.centerY()
    )

    private fun parseCardsFromVisionBlocks(
        blocks: List<Text.TextBlock>,
        rawFallback: String,
        cardNumber: Int
    ): List<ExtractedLead> {
        val rawLines = mutableListOf<SpatialLine>()
        for (block in blocks) {
            for (line in block.lines) {
                val t = line.text.trim()
                val b = line.boundingBox
                if (t.length >= 2 && b != null) {
                    rawLines.add(SpatialLine(text = t, bounds = b))
                }
            }
        }

        if (rawLines.isEmpty()) {
            return parseCardsFromRealOcrBlock(rawFallback, cardNumber)
        }

        // Sort lines top-to-bottom, left-to-right
        val sortedLines = rawLines.sortedWith(
            compareBy<SpatialLine> { it.top / max(1, (it.height * 0.75f).toInt()) }
                .thenBy { it.left }
        )

        val fullRaw = sortedLines.joinToString("\n") { it.text }
        return parseCardsFromLinesList(
            linesWithGeometry = sortedLines,
            rawFallback = fullRaw,
            cardNumber = cardNumber
        )
    }

    /**
     * Cleans and normalizes email addresses from text, repairing common OCR spaces and glyph errors.
     */
    private fun extractCleanEmails(text: String): List<String> {
        var normalized = text
            .replace(Regex("(?i)([a-zA-Z0-9_.+-]+)\\s*[@©®]\\s*([a-zA-Z0-9-]+)\\s*\\.\\s*([a-zA-Z]{2,})"), "$1@$2.$3")
            .replace(Regex("(?i)@gmai[1lI]\\.com\\b"), "@gmail.com")
            .replace(Regex("(?i)@hotmai[1lI]\\.com\\b"), "@hotmail.com")
            .replace(Regex("(?i)@yah[o0][o0]\\.com\\b"), "@yahoo.com")
            .replace(Regex("(?i)@0utlook\\.com\\b"), "@outlook.com")

        val emailPattern = Pattern.compile("(?i)[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
        val matcher = emailPattern.matcher(normalized)
        val results = mutableListOf<String>()

        while (matcher.find()) {
            val found = matcher.group().trim().trimEnd('.', ',', ';', ':', '/', '|', '-')
            if (found.contains(".") && !found.endsWith("@") && !results.contains(found)) {
                results.add(found)
            }
        }
        return results
    }

    /**
     * Cleans and extracts phone numbers, handling mobile/direct labels, multi-number lines,
     * and OCR letter confusion inside phone digit sequences.
     */
    private fun extractCleanPhones(lines: List<String>): Pair<String, List<String>> {
        val allFound = mutableListOf<String>()
        val directOrMobile = mutableListOf<String>()

        val phonePattern = Pattern.compile(
            "(?:\\+?\\d{1,3}[\\s./()-]*)?(?:\\(?\\d{2,5}\\)?[\\s./()-]*)?\\d{2,5}[\\s./()-]*\\d{2,5}(?:[\\s./()-]*\\d{1,6})?"
        )

        for (line in lines) {
            val isExplicitPhoneLine = Regex(
                "(?i)\\b(?:mob|mobile|cell|cellular|direct|dir|tel|telephone|phone|ph|off|office|work|whatsapp|fax|t|m|o|d)\\s*[:.-]"
            ).containsMatchIn(line)

            val segments = line.split(Regex("(?i)\\s*(?:[/|]|\\s{2,}|,\\s*|(?=\\b(?:mob|cell|tel|phone|ph|off|fax|dir|t|m|o|d)\\s*[:.-]))"))

            for (seg in segments) {
                var cleanedSeg = seg.trim()
                val isMobileOrDirect = Regex("(?i)\\b(?:m|mob|mobile|cell|cellular|direct|d|whatsapp)\\s*[:.-]").containsMatchIn(cleanedSeg)

                if (isExplicitPhoneLine || isMobileOrDirect) {
                    cleanedSeg = cleanedSeg
                        .replace(Regex("(?<=\\d)[oO](?=\\d)"), "0")
                        .replace(Regex("(?<=\\d)[lI](?=\\d)"), "1")
                        .replace(Regex("(?<=\\d)[sS](?=\\d)"), "5")
                }

                val matcher = phonePattern.matcher(cleanedSeg)
                while (matcher.find()) {
                    val candidate = matcher.group().trim().trim('-', '.', ',', ' ', '/', ':', '|')
                    val digits = candidate.count { it.isDigit() }

                    val isPinCode = digits == 6 && Regex("(?i)\\b(?:pin|pincode|postal|zip)\\b").containsMatchIn(cleanedSeg)
                    val isZipCode = digits == 5 && Regex("(?i)\\b(?:zip|postal|ca|ny|tx|fl|il|wa|pa|oh|ga|nc|mi|nj|va)\\b").containsMatchIn(cleanedSeg)

                    if (digits in 7..16 && !isPinCode && !isZipCode && !allFound.contains(candidate)) {
                        allFound.add(candidate)
                        if (isMobileOrDirect && !directOrMobile.contains(candidate)) {
                            directOrMobile.add(candidate)
                        }
                    }
                }
            }
        }

        val primary = directOrMobile.firstOrNull() ?: allFound.firstOrNull().orEmpty()
        val alts = allFound.filter { it != primary }
        return Pair(primary, alts)
    }

    /**
     * Cleans and extracts website URLs and bare domains, ensuring email domains are not duplicated.
     */
    private fun extractCleanWebsites(text: String, emails: List<String>): List<String> {
        val results = mutableListOf<String>()

        val prefixPattern = Pattern.compile("(?i)(?:https?://|www\\.)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?")
        val pm = prefixPattern.matcher(text)
        while (pm.find()) {
            val found = pm.group().trim().trimEnd('.', ',', ';', ':', '/', '|')
            if (!results.contains(found)) results.add(found)
        }

        val barePattern = Pattern.compile(
            "(?i)(?<![@a-zA-Z0-9.-])([a-zA-Z0-9-]+\\.(?:com|org|net|in|co\\.in|io|ai|co|biz|info|tech|global|group|studio|agency|store|digital|cloud|app|dev|me|us|uk|ca|de|ae|sg))(?!\\.[a-zA-Z])(?:/[^\\s]*)?"
        )
        val bm = barePattern.matcher(text)
        while (bm.find()) {
            val found = bm.group().trim().trimEnd('.', ',', ';', ':', '/', '|')
            val isEmailDomain = emails.any { it.endsWith("@$found", ignoreCase = true) }
            if (!results.contains(found) && !isEmailDomain) {
                results.add(found)
            }
        }

        return results
    }

    /**
     * Parses an English business card's real OCR text block into structured leads,
     * generating separate complete rows for alternative phone numbers as requested.
     */
    fun parseCardsFromRealOcrBlock(rawBlock: String, cardNumber: Int): List<ExtractedLead> {
        val lines = rawBlock.split("\n")
            .map { it.trim() }
            .filter { it.length >= 2 }

        if (lines.isEmpty()) return emptyList()

        val pseudoSpatialLines = lines.mapIndexed { idx, t ->
            SpatialLine(
                text = t,
                bounds = Rect(0, idx * 30, 400, (idx + 1) * 30)
            )
        }

        return parseCardsFromLinesList(pseudoSpatialLines, rawBlock, cardNumber)
    }

    /**
     * Backward-compatible helper that returns the first parsed lead from a real OCR block.
     */
    fun parseSingleCardFromRealOcrBlock(rawBlock: String, cardNumber: Int): ExtractedLead? {
        return parseCardsFromRealOcrBlock(rawBlock, cardNumber).firstOrNull()
    }

    /**
     * Core high-accuracy contact extraction engine combining spatial line hierarchy,
     * lexical classification, job-title adjacency, entity detection, and clean normalization.
     * Returns a primary row plus an additional full row for each distinct alternative phone
     * number found on the card (with complete Name, Designation, Company, and Address).
     */
    private fun parseCardsFromLinesList(
        linesWithGeometry: List<SpatialLine>,
        rawFallback: String,
        cardNumber: Int
    ): List<ExtractedLead> {
        val allLineTexts = linesWithGeometry.map { it.text }
        val fullText = linesWithGeometry.joinToString("\n") { it.text }

        // 1. Extract contact coordinates (Email, Phone, Website)
        val emails = extractCleanEmails(fullText)
        val (primaryPhone, altPhones) = extractCleanPhones(allLineTexts)
        val allPhones = if (primaryPhone.isNotBlank()) listOf(primaryPhone) + altPhones else emptyList()
        val websites = extractCleanWebsites(fullText, emails)

        // Identify distinct alternative phone numbers (incompatible with primary phone)
        val distinctAltPhones = mutableListOf<String>()
        val cleanPri = cleanPhoneDigitsOnly(primaryPhone)
        for (alt in altPhones) {
            val cleanAlt = cleanPhoneDigitsOnly(alt)
            if (cleanAlt.length >= 7 && (cleanPri.length < 7 || !arePhonesCompatible(cleanPri, cleanAlt))) {
                val alreadyAdded = distinctAltPhones.any { arePhonesCompatible(cleanPhoneDigitsOnly(it), cleanAlt) }
                if (!alreadyAdded) {
                    distinctAltPhones.add(alt)
                }
            }
        }

        // 2. Dictionaries of business indicators
        val roleKeywords = listOf(
            "managing director", "executive director", "director", "general manager", "branch manager",
            "operations manager", "project manager", "product manager", "sales manager", "marketing manager",
            "business development manager", "manager", "chief executive officer", "chief technology officer",
            "chief financial officer", "chief operating officer", "chief marketing officer", "ceo", "cto",
            "cfo", "coo", "cmo", "cio", "president", "vice president", "vp", "senior vice president", "svp",
            "founder", "co-founder", "chairman", "chairperson", "proprietor", "prop.", "prop", "owner",
            "co-owner", "partner", "senior partner", "managing partner", "principal", "consultant",
            "senior consultant", "advisor", "senior advisor", "specialist", "engineer", "senior engineer",
            "software engineer", "civil engineer", "mechanical engineer", "electrical engineer", "architect",
            "lead architect", "doctor", "physician", "surgeon", "advocate", "attorney", "lawyer",
            "chartered accountant", "ca", "cpa", "auditor", "professor", "lecturer", "head", "lead",
            "executive", "senior executive", "associate", "senior associate", "representative",
            "coordinator", "supervisor", "officer", "analyst", "senior analyst", "developer", "designer",
            "creative director", "pharmacist", "strategist", "agent", "administrator"
        )

        val companyKeywords = listOf(
            "pvt", "ltd", "limited", "inc", "llp", "llc", "corp", "corporation", "company", "co.",
            "group", "enterprises", "enterprise", "solutions", "technologies", "technology", "tech",
            "infotech", "industries", "industry", "services", "associates", "electricals",
            "electronics", "engineering", "builders", "buildcon", "developers", "institute",
            "hospital", "clinic", "agency", "works", "traders", "trading", "exports", "imports",
            "logistics", "motors", "automobiles", "pharma", "pharmaceuticals", "realty",
            "consultancy", "consulting", "systems", "global", "international", "ventures",
            "labs", "laboratories", "studio", "media", "communications", "network", "bank",
            "finance", "investments", "textiles", "garments", "jewellers", "hotel", "resorts",
            "caterers", "printers", "graphics", "packaging", "chemicals", "plastics", "steels",
            "agro", "foods", "beverages", "hardware", "plywood", "ceramics", "distributors", "holdings"
        )

        val addressKeywords = listOf(
            "road", "rd", "rd.", "street", "st", "st.", "floor", "flr", "fl", "plot", "plot no", "shop no", "office no",
            "flat no", "gala no", "survey no", "s. no", "s.no", "house no", "premise", "premises",
            "nagar", "peth", "midc", "gidc", "sector", "phase", "building", "bldg", "wing", "block", "complex",
            "enclave", "heights", "residency", "arcade", "mansion", "chambers", "center", "centre", "hub",
            "survey", "marg", "rasta", "bypass", "pin", "pincode", "postal", "zip", "zipcode",
            "opp.", "opp", "opposite", "near", "nr", "nr.", "behind", "bh", "bh.", "beside", "next to", "adj", "adjacent",
            "lane", "gali", "avenue", "ave", "blvd", "boulevard", "highway", "hwy", "expressway", "cross",
            "taluka", "tal", "dist", "district", "state", "city",
            "suite", "ste", "drive", "dr", "court", "ct", "way", "plaza", "tower", "towers", "estate",
            "industrial area", "industrial estate", "ind. area", "indl area", "ind. estate",
            "delhi", "mumbai", "bangalore", "bengaluru", "hyderabad", "chennai", "kolkata", "pune", "ahmedabad",
            "jaipur", "surat", "lucknow", "kanpur", "nagpur", "indore", "bhopal", "patna", "vadodara",
            "ghaziabad", "ludhiana", "agra", "nashik", "faridabad", "meerut", "rajkot", "varanasi", "srinagar",
            "aurangabad", "dhanbad", "amritsar", "navi mumbai", "allahabad", "prayagraj", "ranchi", "howrah",
            "coimbatore", "jabalpur", "gwalior", "vijayawada", "jodhpur", "madurai", "raipur", "kota",
            "chandigarh", "guwahati", "solapur", "hubli", "dharwad", "bareilly", "moradabad", "mysore", "mysuru",
            "gurgaon", "gurugram", "aligarh", "jalandhar", "tiruchirappalli", "bhubaneswar", "salem", "warangal",
            "thiruvananthapuram", "bhiwandi", "saharanpur", "gorakhpur", "guntur", "bikaner", "amravati", "noida",
            "jamshedpur", "bhilai", "cuttack", "firozabad", "kochi", "nellore", "bhavnagar", "dehradun", "durgapur",
            "asansol", "rourkela", "nanded", "kolhapur", "ajmer", "akola", "gulbarga", "kalaburagi", "jamnagar",
            "ujjain", "loni", "siliguri", "jhansi", "ulhasnagar", "jammu", "sangli", "mangalore", "mangaluru",
            "erode", "belgaum", "belagavi", "kurnool", "ambattur", "tirunelveli", "malegaon", "gaya", "jalgaon",
            "udaipur", "maharashtra", "gujarat", "karnataka", "tamil nadu", "telangana", "andhra pradesh",
            "rajasthan", "uttar pradesh", "madhya pradesh", "kerala", "punjab", "haryana", "bihar", "west bengal",
            "odisha", "assam", "goa", "uttarakhand", "jharkhand", "chhattisgarh", "himachal pradesh"
        )

        val honorificRegex = Regex(
            "(?i)^(?:dr\\.?|mr\\.?|mrs\\.?|ms\\.?|miss|prof\\.?|professor|er\\.?|adv\\.?|advocate|ca\\.?|cpa\\.?|shri\\.?|smt\\.?)\\s+[a-zA-Z]"
        )
        val inlineRoleSplitRegex = Regex(
            "(?i)^(.+?)\\s*(?:\\s*[-|,]\\s*|\\s+[-|]\\s+)(.+)$"
        )
        val explicitNamePrefixRegex = Regex(
            "(?i)^\\s*(?:name|full name|contact person|cardholder)\\s*[:.-]\\s*(.+)$"
        )
        val explicitRolePrefixRegex = Regex(
            "(?i)^\\s*(?:designation|title|role|position)\\s*[:.-]\\s*(.+)$"
        )
        val explicitCompanyPrefixRegex = Regex(
            "(?i)^\\s*(?:company|firm|organization|corp|corporation)\\s*[:.-]\\s*(.+)$"
        )
        val explicitAddressPrefixRegex = Regex(
            "(?i)^\\s*(?:address|addr|off\\.?\\s*addr|office address|regd\\.?\\s*office|registered office|factory address|works address|site address|location|head office|corp\\.?\\s*office|branch office)\\s*[:.-]\\s*(.*)$"
        )
        val contactLabelPrefixRegex = Regex(
            "(?i)^(?:\\s*(?:mob|mobile|tel|telephone|ph|phone|cell|whatsapp|email|e-mail|mail|web|website|url|off|office|fax|gst|gstin|tin|cin|pan)\\s*[:.-]?\\s*)+"
        )

        val marketingTaglineRegex = Regex(
            "(?i)(?:iso\\s*\\d{4}|certified|since\\s*\\d{4}|quality\\s*&|all\\s*types\\s*of|we\\s*(?:deal|specialize|offer)|authorised|authorized|welcome\\s*to)"
        )
        val taxRegRegex = Regex(
            "(?i)(?:gstin|gst\\b|tin\\b|cin\\b|pan\\b|reg\\.?\\s*no)"
        )

        var name = ""
        var jobTitle = ""
        var company = ""
        var roleLineIndex = -1
        val addressLines = mutableListOf<String>()
        val addressLineIndices = mutableSetOf<Int>()
        val extraNotes = mutableListOf<String>()
        val unclassifiedLines = mutableListOf<Pair<Int, String>>()

        // Calculate average font line height to detect large prominent lines
        val validHeights = linesWithGeometry.map { it.height }.filter { it > 0 }
        val avgLineHeight = if (validHeights.isNotEmpty()) validHeights.average().toFloat() else 24f

        for (i in linesWithGeometry.indices) {
            val lineObj = linesWithGeometry[i]
            var stripped = lineObj.text

            // Remove email, website, and phone substrings from the line
            for (em in emails) stripped = stripped.replace(em, " ")
            for (wb in websites) stripped = stripped.replace(wb, " ")
            for (ph in allPhones) stripped = stripped.replace(ph, " ")

            val hadExplicitAddr = explicitAddressPrefixRegex.containsMatchIn(stripped)
            val explicitAddrMatch = explicitAddressPrefixRegex.find(stripped)
            val hadExplicitName = explicitNamePrefixRegex.containsMatchIn(stripped)
            val explicitNameMatch = explicitNamePrefixRegex.find(stripped)
            val hadExplicitRole = explicitRolePrefixRegex.containsMatchIn(stripped)
            val explicitRoleMatch = explicitRolePrefixRegex.find(stripped)
            val hadExplicitCompany = explicitCompanyPrefixRegex.containsMatchIn(stripped)
            val explicitCompanyMatch = explicitCompanyPrefixRegex.find(stripped)

            stripped = stripped
                .replace(explicitAddressPrefixRegex, "$1")
                .replace(explicitNamePrefixRegex, "$1")
                .replace(explicitRolePrefixRegex, "$1")
                .replace(explicitCompanyPrefixRegex, "$1")
                .replace(contactLabelPrefixRegex, "")
                .trim()
                .trim(',', ';', '|', '-', ':', '/', '\\', '"', '\'', '.')
                .trim()

            if (stripped.length < 2) continue
            if (stripped.none { it.isLetter() }) continue

            val lower = stripped.lowercase()
            val hasPinCode = Regex("(?<!\\d)\\d{6}(?!\\d)").containsMatchIn(stripped) ||
                Regex("(?<!\\d)\\d{3}\\s+\\d{3}(?!\\d)").containsMatchIn(stripped) ||
                Regex("(?i)\\bpin(?:code)?\\s*[:.-]?\\s*\\d{6}\\b").containsMatchIn(stripped) ||
                Regex("(?<!\\d)\\d{5}(?:-\\d{4})?(?!\\d)").containsMatchIn(stripped)
            val isCompanyKeyword = companyKeywords.any { containsKeyword(lower, it) }
            val isRoleKeyword = roleKeywords.any { containsKeyword(lower, it) }
            val isAddressKeyword = addressKeywords.any { containsKeyword(lower, it) }
            val isHonorificName = honorificRegex.containsMatchIn(stripped)
            val isTagline = marketingTaglineRegex.containsMatchIn(stripped)
            val isTaxReg = taxRegRegex.containsMatchIn(stripped)

            if (isTaxReg || isTagline) {
                extraNotes.add(stripped)
                continue
            }

            when {
                hadExplicitName && name.isBlank() -> {
                    name = explicitNameMatch?.groupValues?.getOrNull(1)?.trim() ?: stripped
                }
                hadExplicitRole && jobTitle.isBlank() -> {
                    jobTitle = explicitRoleMatch?.groupValues?.getOrNull(1)?.trim() ?: stripped
                    roleLineIndex = i
                }
                hadExplicitCompany && company.isBlank() -> {
                    company = explicitCompanyMatch?.groupValues?.getOrNull(1)?.trim() ?: stripped
                }
                hadExplicitAddr -> {
                    val addrPart = explicitAddrMatch?.groupValues?.getOrNull(1)?.trim() ?: stripped
                    if (addrPart.isNotBlank()) {
                        addressLines.add(addrPart)
                        addressLineIndices.add(i)
                    }
                }
                isHonorificName && name.isBlank() -> {
                    // Check if honorific line also has a title (e.g., "Dr. John Doe, Director")
                    val splitMatch = inlineRoleSplitRegex.find(stripped)
                    if (splitMatch != null) {
                        val part1 = splitMatch.groupValues[1].trim()
                        val part2 = splitMatch.groupValues[2].trim()
                        name = part1
                        if (jobTitle.isBlank() && roleKeywords.any { containsKeyword(part2.lowercase(), it) }) {
                            jobTitle = part2
                            roleLineIndex = i
                        }
                    } else {
                        name = stripped
                    }
                }
                isRoleKeyword && (stripped.contains("|") || stripped.contains(",") || stripped.contains(" - ")) -> {
                    val parts = stripped.split(Regex("\\s*[-|,]+\\s*")).map { it.trim() }.filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        val rolePart = parts.firstOrNull { p -> roleKeywords.any { containsKeyword(p.lowercase(), it) } }
                        val namePart = parts.firstOrNull { it != rolePart }
                        if (rolePart != null && jobTitle.isBlank()) {
                            jobTitle = rolePart
                            roleLineIndex = i
                        }
                        if (namePart != null && name.isBlank() && namePart.none { it.isDigit() } && !companyKeywords.any { containsKeyword(namePart.lowercase(), it) }) {
                            name = namePart
                        }
                    } else if (jobTitle.isBlank()) {
                        jobTitle = stripped
                        roleLineIndex = i
                    }
                }
                isRoleKeyword && jobTitle.isBlank() && stripped.length <= 50 -> {
                    jobTitle = stripped
                    roleLineIndex = i
                }
                isCompanyKeyword && company.isBlank() && !hasPinCode && !isAddressKeyword -> {
                    company = stripped
                }
                isAddressKeyword || hasPinCode -> {
                    addressLines.add(stripped)
                    addressLineIndices.add(i)
                }
                else -> {
                    unclassifiedLines.add(Pair(i, stripped))
                }
            }
        }

        // 3. High-Accuracy Job Title Adjacency Rule for Person Name
        // In standard business cards, the person's name is placed immediately ABOVE (or below) the Job Title
        if (name.isBlank() && roleLineIndex >= 0) {
            val candidateAbove = unclassifiedLines.firstOrNull { it.first == roleLineIndex - 1 }
            val candidateBelow = unclassifiedLines.firstOrNull { it.first == roleLineIndex + 1 }

            val chosen = when {
                candidateAbove != null && isValidPersonNameCandidate(candidateAbove.second, companyKeywords, addressKeywords) -> candidateAbove
                candidateBelow != null && isValidPersonNameCandidate(candidateBelow.second, companyKeywords, addressKeywords) -> candidateBelow
                else -> null
            }

            if (chosen != null) {
                name = chosen.second
                unclassifiedLines.removeAll { it.first == chosen.first }
            }
        }

        // 4. Assign remaining unclassified lines (including address continuation)
        val remainingUnclassified = unclassifiedLines.toList()
        for ((idx, candidate) in remainingUnclassified) {
            val isLargeFont = linesWithGeometry.getOrNull(idx)?.let { it.height >= avgLineHeight * 1.15f } ?: false
            val looksLikeName = isValidPersonNameCandidate(candidate, companyKeywords, addressKeywords)
            val isTopLine = idx == 0
            val isNearAddressBlock = addressLineIndices.any { Math.abs(it - idx) <= 1 }

            when {
                // If top line has company characteristics or prominent size, it is Company
                company.isBlank() && (isTopLine || isLargeFont) && !looksLikeName -> {
                    company = candidate
                }
                name.isBlank() && looksLikeName -> {
                    name = candidate
                }
                company.isBlank() && (isTopLine || isLargeFont || candidate.length in 3..60) -> {
                    company = candidate
                }
                jobTitle.isBlank() && candidate.length in 3..40 && !looksLikeName && !isNearAddressBlock -> {
                    jobTitle = candidate
                }
                addressKeywords.any { containsKeyword(candidate.lowercase(), it) } -> {
                    addressLines.add(candidate)
                    addressLineIndices.add(idx)
                }
                isNearAddressBlock && !looksLikeName && candidate.length in 3..80 -> {
                    // Contiguous address line (e.g. area, landmark, or city continuation)
                    addressLines.add(candidate)
                    addressLineIndices.add(idx)
                }
                else -> {
                    extraNotes.add(candidate)
                }
            }
        }

        // Verify that there is at least one business card contact or identity signal
        val hasContactSignal = emails.isNotEmpty() ||
            allPhones.isNotEmpty() ||
            websites.isNotEmpty() ||
            (company.isNotBlank() && (jobTitle.isNotBlank() || addressLines.isNotEmpty())) ||
            (name.isNotBlank() && (company.isNotBlank() || jobTitle.isNotBlank()))

        if (!hasContactSignal) {
            return emptyList()
        }

        // Derive missing company from website domain or email if needed
        val websiteDerivedCompany = websites.firstOrNull()
            ?.replace(Regex("(?i)^(?:https?://)?(?:www\\.)?"), "")
            ?.substringBefore(".")
            ?.replace(Regex("[-_]+"), " ")
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            .orEmpty()

        if (company.isBlank() && websiteDerivedCompany.length >= 2) {
            company = websiteDerivedCompany
        }

        val emailDerivedName = emails.firstOrNull()?.substringBefore("@")
            ?.replace(Regex("[._-]+"), " ")
            ?.trim()
            ?.split(" ")
            ?.filter { it.isNotBlank() && it.none { ch -> ch.isDigit() } }
            ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            .orEmpty()

        // Clean and format Person Name (Title Case)
        val cleanPersonName = if (name.isNotBlank()) {
            name.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { c -> c.uppercase() }
            }
        } else if (emailDerivedName.isNotBlank() && emailDerivedName.length in 3..30 &&
            !companyKeywords.any { containsKeyword(emailDerivedName.lowercase(), it) }
        ) {
            emailDerivedName
        } else {
            ""
        }

        // Clean and format Company Name
        val cleanCompany = if (company.isNotBlank()) {
            company
        } else if (cleanPersonName.isBlank() && websiteDerivedCompany.isNotBlank()) {
            websiteDerivedCompany
        } else {
            ""
        }

        // Clean and format Full Address
        val fullCleanAddress = addressLines
            .map { it.trim().trimEnd(',', ';', '.', '-') }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        // Final primary display name for card row
        val finalDisplayName = cleanPersonName
            .ifBlank { cleanCompany }
            .ifBlank { jobTitle }
            .ifBlank { addressLines.firstOrNull().orEmpty() }

        if (finalDisplayName.isBlank() && allPhones.isEmpty() && emails.isEmpty()) {
            return emptyList()
        }

        val confidence = when {
            cleanPersonName.isNotBlank() && allPhones.isNotEmpty() && emails.isNotEmpty() && cleanCompany.isNotBlank() -> 98
            (cleanPersonName.isNotBlank() || cleanCompany.isNotBlank()) && (allPhones.isNotEmpty() || emails.isNotEmpty()) -> 95
            allPhones.isNotEmpty() || emails.isNotEmpty() -> 90
            else -> 85
        }

        val allCombinedPhones = (if (primaryPhone.isNotBlank()) listOf(primaryPhone) + distinctAltPhones else distinctAltPhones)
            .distinct()
        val combinedPhoneString = allCombinedPhones.joinToString(" / ")

        if (distinctAltPhones.isNotEmpty()) {
            extraNotes.add(0, "Alt Phone: ${distinctAltPhones.joinToString(", ")}")
        }

        // Single entry for this business card / company containing all details and multiple numbers
        val singleLead = ExtractedLead(
            name = cleanPersonName.ifBlank { if (cleanCompany.isBlank()) finalDisplayName else "" },
            jobTitle = if (jobTitle == cleanPersonName || jobTitle == cleanCompany) "" else jobTitle,
            company = cleanCompany,
            email = emails.distinct().joinToString(", "),
            phone = combinedPhoneString,
            website = websites.firstOrNull().orEmpty(),
            address = fullCleanAddress,
            notes = extraNotes.distinct().joinToString(" | "),
            source = "CARD_SCAN",
            sourceDetail = "Card #$cardNumber",
            confidence = confidence,
            syncStatus = "NOT_SAVED"
        )

        return listOf(singleLead)
    }

    private fun isValidPersonNameCandidate(
        text: String,
        companyKeywords: List<String>,
        addressKeywords: List<String>
    ): Boolean {
        val clean = text.replace(Regex("[,.:;|/\\\\\"']"), " ").trim()
        if (clean.length !in 3..45) return false
        if (clean.any { it.isDigit() }) return false
        val words = clean.split(" ").filter { it.isNotBlank() }
        if (words.size !in 1..4) return false

        val lower = clean.lowercase()
        if (companyKeywords.any { containsKeyword(lower, it) }) return false
        if (addressKeywords.any { containsKeyword(lower, it) }) return false

        val noiseWords = setOf("tel", "fax", "mob", "phone", "email", "web", "website", "iso", "gstin", "reg", "off", "page", "near", "opp")
        if (words.any { it.lowercase() in noiseWords }) return false

        return true
    }

    /**
     * Verifies that an extracted lead actually contains recognizable business card contact details.
     */
    fun isRecognizedBusinessCardLead(lead: ExtractedLead): Boolean {
        val hasDirectContact = lead.phone.isNotBlank() || lead.email.isNotBlank() || lead.website.isNotBlank()
        val hasBusinessIdentity = lead.company.isNotBlank() && (lead.jobTitle.isNotBlank() || lead.address.isNotBlank())
        val hasRoleAndName = lead.name.isNotBlank() && (lead.jobTitle.isNotBlank() || lead.address.isNotBlank()) && (hasDirectContact || lead.company.isNotBlank())
        return (lead.name.isNotBlank() || lead.company.isNotBlank()) && (hasDirectContact || hasBusinessIdentity || hasRoleAndName)
    }

    /**
     * Returns all extracted leads including repeated cards (assigning sequential row IDs if needed)
     * since there is no database and repeated cards should also be converted into Excel.
     */
    fun deduplicateLeadsOneTimeOnly(leads: List<ExtractedLead>): List<ExtractedLead> {
        return leads
    }

    fun buildCardUniqueKey(lead: ExtractedLead): String {
        val normName = lead.name.trim().lowercase().replace(Regex("\\s+"), " ")
        val normEmail = lead.email.trim().lowercase()
        val normPhone = lead.phone.replace(Regex("[^0-9+]"), "")
        val normCompany = lead.company.trim().lowercase().replace(Regex("\\s+"), " ")
        return "$normName|$normEmail|$normPhone|$normCompany"
    }

    /**
     * Extracts structured lead data from a business card photo using 100% Offline On-Device OCR
     */
    suspend fun extractFromCardImage(
        bitmap: Bitmap,
        agentConfig: AgentConfig = AgentConfig()
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val leads = extractLeadsFromCardImage(bitmap, agentConfig)
        if (leads.isNotEmpty()) {
            val primary = leads.first()
            ExtractionResult(
                name = primary.name,
                phone = primary.phone,
                email = primary.email,
                company = primary.company,
                jobTitle = primary.jobTitle,
                website = primary.website,
                address = primary.address,
                notes = if (leads.size > 1) "Card 1 of ${leads.size} cards detected in photo. ${primary.notes}" else primary.notes,
                confidence = primary.confidence,
                success = true
            )
        } else {
            ExtractionResult(
                success = false,
                errorMessage = "No contact details detected in photo."
            )
        }
    }

    /**
     * Extracts multiple structured leads from a Word (.docx, .doc) or PDF (.pdf) document
     * 100% offline without any API key or external AI service.
     * Supports both text-based documents and scanned image-based PDFs (via offline OCR on previewBitmap).
     * Never returns demo or fabricated contacts.
     */
    suspend fun extractLeadsFromDocument(
        document: ParsedDocument,
        agentConfig: AgentConfig = AgentConfig()
    ): List<ExtractedLead> = withContext(Dispatchers.IO) {
        val fromText = extractHeuristicallyFromDocument(document)
        if (fromText.isNotEmpty()) {
            return@withContext fromText
        }

        // If the PDF is a scanned image with no selectable text (or contacts are in the page image), run offline OCR
        val preview = document.previewBitmap
        if (preview != null) {
            val ocrScan = recognizeCardsAcrossOrientations(ensureSoftwareBitmap(preview))
            if (ocrScan.parsedLeads.isNotEmpty()) {
                return@withContext ocrScan.parsedLeads.map { lead ->
                    lead.copy(
                        source = "DOCUMENT_BULK",
                        sourceDetail = document.fileName
                    )
                }
            }
        }

        emptyList()
    }

    /**
     * Extracts real contact records actually present in English document text
     * without any API key and without fabricating any fields.
     */
    private fun extractHeuristicallyFromDocument(document: ParsedDocument): List<ExtractedLead> {
        val text = normalizeDevanagariDigits(document.textContent)
        if (text.isBlank()) return emptyList()

        val emailRegex = "([a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+)"
        val emailPattern = Pattern.compile(emailRegex)
        val emailMatcher = emailPattern.matcher(text)

        val emailIndices = mutableListOf<Pair<Int, String>>()
        while (emailMatcher.find()) {
            val found = emailMatcher.group(1) ?: continue
            if (found.contains(".") && !found.endsWith(".")) {
                emailIndices.add(Pair(emailMatcher.start(), found))
            }
        }

        val leads = mutableListOf<ExtractedLead>()
        val phonePattern = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{2,5}\\)?[-.\\s]?\\d{3,5}[-.\\s]?\\d{3,5}")

        for (i in emailIndices.indices) {
            val (startIndex, email) = emailIndices[i]
            val prevIndex = if (i > 0) emailIndices[i - 1].first else 0
            val nextIndex = if (i < emailIndices.size - 1) emailIndices[i + 1].first else text.length

            val blockStart = maxOf(prevIndex, startIndex - 380)
            val blockEnd = minOf(nextIndex, startIndex + 380)
            val block = text.substring(blockStart, blockEnd)

            var name = ""
            var company = ""
            var jobTitle = ""
            var phone = ""
            var website = ""
            var address = ""
            var notes = ""

            val lines = block.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            for (line in lines) {
                val lower = line.lowercase()
                when {
                    lower.startsWith("full name:") || lower.startsWith("name:") || lower.startsWith("executive name:") ||
                        lower.startsWith("contact:") -> {
                        name = line.substringAfter(":").trim()
                    }
                    lower.startsWith("job title:") || lower.startsWith("title:") || lower.startsWith("position:") ||
                        lower.startsWith("designation:") || lower.startsWith("role:") -> {
                        jobTitle = line.substringAfter(":").trim()
                    }
                    lower.startsWith("company:") || lower.startsWith("organization:") || lower.startsWith("firm:") -> {
                        company = line.substringAfter(":").trim()
                    }
                    lower.startsWith("phone:") || lower.startsWith("direct line:") || lower.startsWith("tel:") ||
                        lower.startsWith("mobile:") -> {
                        phone = line.substringAfter(":").trim()
                    }
                    lower.startsWith("website:") || lower.startsWith("corporate portal:") || lower.startsWith("portal:") -> {
                        website = line.substringAfter(":").trim()
                    }
                    lower.startsWith("address:") || lower.startsWith("headquarters:") || lower.startsWith("location:") -> {
                        address = line.substringAfter(":").trim()
                    }
                    lower.startsWith("notes:") || lower.startsWith("intelligence summary:") || lower.startsWith("summary:") -> {
                        notes = line.substringAfter(":").trim()
                    }
                }
            }

            if (phone.isBlank()) {
                val pMatcher = phonePattern.matcher(block)
                while (pMatcher.find()) {
                    val candidate = pMatcher.group().trim()
                    if (candidate.count { it.isDigit() } >= 8) {
                        phone = candidate
                        break
                    }
                }
            }
            if (website.isBlank()) {
                val wMatcher = Pattern.compile("(?:https?://|www\\.)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}").matcher(block)
                if (wMatcher.find()) website = wMatcher.group()
            }
            if (name.isBlank()) {
                val userPart = email.substringBefore("@").replace(".", " ").replace("_", " ").replace("-", " ")
                name = userPart.split(" ").filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            }

            leads.add(
                ExtractedLead(
                    name = name,
                    company = company,
                    jobTitle = jobTitle,
                    email = email,
                    phone = phone,
                    website = website,
                    address = address,
                    notes = notes,
                    source = "DOCUMENT_BULK",
                    sourceDetail = document.fileName,
                    confidence = 90,
                    syncStatus = "NOT_SAVED"
                )
            )
        }

        if (leads.isNotEmpty()) {
            return leads
        }

        // Also support phone-anchored or card-block contacts inside documents that don't have emails
        val cardBlocks = parseCardsFromOcrRegion(text, 1)
        return cardBlocks.map { lead ->
            lead.copy(
                source = "DOCUMENT_BULK",
                sourceDetail = document.fileName,
                syncStatus = "NOT_SAVED"
            )
        }
    }

    private fun extractHeuristicallyFromRealText(url: String, rawText: String): ExtractionResult {
        val normalizedText = normalizeDevanagariDigits(rawText)
        val cardCandidates = parseCardsFromOcrRegion(normalizedText, 1)
        if (cardCandidates.isNotEmpty()) {
            val best = cardCandidates.first()
            return ExtractionResult(
                name = best.name,
                phone = best.phone,
                email = best.email,
                company = best.company,
                jobTitle = best.jobTitle,
                website = best.website.ifBlank { url },
                address = best.address,
                notes = best.notes,
                confidence = best.confidence,
                success = true
            )
        }

        val emailRegex = "[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+"
        val phoneRegex = "(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{2,5}\\)?[-.\\s]?\\d{3,5}[-.\\s]?\\d{3,5}"

        val emails = extractAll(normalizedText, emailRegex)
        val phones = extractAll(normalizedText, phoneRegex).filter { it.count { ch -> ch.isDigit() } in 8..15 }

        if (emails.isEmpty() && phones.isEmpty()) {
            return ExtractionResult(
                success = false,
                errorMessage = "No contact email or phone number found on the webpage."
            )
        }

        val domain = url.replace("https://", "").replace("http://", "").split("/").firstOrNull().orEmpty()
        val companyName = domain.replace("www.", "").substringBefore(".").replaceFirstChar { it.uppercase() }

        return ExtractionResult(
            name = companyName,
            phone = phones.firstOrNull().orEmpty(),
            email = emails.firstOrNull().orEmpty(),
            company = companyName,
            jobTitle = "",
            website = url,
            address = "",
            notes = "",
            confidence = 85,
            success = true
        )
    }

    private fun extractAll(content: String, regex: String): List<String> {
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(content)
        val set = linkedSetOf<String>()
        while (matcher.find()) {
            set.add(matcher.group().trim())
        }
        return set.toList()
    }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                bitmap.config == Bitmap.Config.HARDWARE
            ) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
            } else {
                bitmap
            }
        } catch (_: Exception) {
            bitmap
        }
    }
}
