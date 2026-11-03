package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.util.UUID
import java.util.regex.Pattern
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

enum class DocumentType {
    PDF,
    DOCX,
    DOC,
    TXT
}

data class ParsedDocument(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileType: DocumentType,
    val textContent: String,
    val rawBytes: ByteArray? = null,
    val fileSizeFormatted: String = "",
    val previewBitmap: Bitmap? = null,
    val estimatedContactsCount: Int = 0
)

class DocumentParserService {

    /**
     * Parses a document from an Android content URI (supports Word DOCX, DOC, PDF, and TXT)
     */
    fun parseDocument(context: Context, uri: Uri): ParsedDocument {
        val (fileName, fileSize) = queryFileInfo(context, uri)
        val fileType = detectDocumentType(fileName, context.contentResolver.getType(uri))
        val rawBytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        } catch (e: Exception) {
            ByteArray(0)
        }

        val formattedSize = formatFileSize(if (fileSize > 0) fileSize else rawBytes.size.toLong())

        var extractedText = ""
        var previewBitmap: Bitmap? = null

        when (fileType) {
            DocumentType.DOCX -> {
                extractedText = extractDocxText(rawBytes)
            }
            DocumentType.PDF -> {
                extractedText = extractPdfText(rawBytes)
                previewBitmap = renderPdfFirstPage(context, rawBytes)
            }
            DocumentType.DOC -> {
                extractedText = extractDocText(rawBytes)
            }
            DocumentType.TXT -> {
                extractedText = String(rawBytes, Charsets.UTF_8)
            }
        }

        if (extractedText.isBlank()) {
            extractedText = extractPrintableAscii(rawBytes)
        }

        val estimatedContacts = estimateContactCount(extractedText)

        return ParsedDocument(
            fileName = fileName,
            fileType = fileType,
            textContent = extractedText,
            rawBytes = rawBytes,
            fileSizeFormatted = formattedSize,
            previewBitmap = previewBitmap,
            estimatedContactsCount = maxOf(1, estimatedContacts)
        )
    }

    /**
     * Extracts text from Microsoft Word (.docx) OpenXML package
     */
    fun extractDocxText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val stringBuilder = StringBuilder()
        try {
            val zip = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml" || entry.name.endsWith("/document.xml")) {
                    val parser = Xml.newPullParser()
                    parser.setInput(zip, "UTF-8")
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                when (parser.name) {
                                    "p" -> stringBuilder.append("\n")
                                    "t" -> stringBuilder.append(parser.nextText())
                                    "tab" -> stringBuilder.append("\t")
                                    "br" -> stringBuilder.append("\n")
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        } catch (e: Exception) {
            // fallback to ASCII/text scanning
            return extractPrintableAscii(bytes)
        }
        val result = stringBuilder.toString().trim()
        return if (result.isNotBlank()) result else extractPrintableAscii(bytes)
    }

    /**
     * Extracts text tokens from both uncompressed PDF content and FlateDecode compressed PDF streams
     */
    fun extractPdfText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder()
        try {
            val rawIso = String(bytes, Charsets.ISO_8859_1)

            // 1. Extract text operators from uncompressed PDF text (e.g., plain text or uncompressed streams)
            extractPdfOperatorsFromStream(rawIso, sb)

            // 2. Decompress all FlateDecode / zlib stream ... endstream blocks in the PDF
            var searchPos = 0
            while (searchPos < rawIso.length) {
                val streamIdx = rawIso.indexOf("stream", searchPos)
                if (streamIdx == -1) break

                // Verify it's not "endstream"
                if (streamIdx >= 3 && rawIso.substring(streamIdx - 3, streamIdx) == "end") {
                    searchPos = streamIdx + 6
                    continue
                }

                var dataStart = streamIdx + 6
                if (dataStart < rawIso.length && rawIso[dataStart] == '\r') dataStart++
                if (dataStart < rawIso.length && rawIso[dataStart] == '\n') dataStart++

                val endStreamIdx = rawIso.indexOf("endstream", dataStart)
                if (endStreamIdx == -1) break

                var dataEnd = endStreamIdx
                while (dataEnd > dataStart && (rawIso[dataEnd - 1] == '\n' || rawIso[dataEnd - 1] == '\r')) {
                    dataEnd--
                }

                if (dataEnd > dataStart && (dataEnd - dataStart) < 5_000_000) {
                    val streamBytes = bytes.copyOfRange(dataStart, dataEnd)
                    val decompressed = inflatePdfStream(streamBytes)
                    if (decompressed.isNotEmpty()) {
                        val streamStr = String(decompressed, Charsets.ISO_8859_1)
                        extractPdfOperatorsFromStream(streamStr, sb)
                    }
                }
                searchPos = endStreamIdx + 9
            }

            // 3. If the PDF is a plain-text structured PDF (like %PDF-1.7 followed by readable text lines), preserve readable lines
            if (sb.length < 60 && rawIso.contains("@")) {
                sb.append("\n").append(extractPrintableAscii(bytes))
            }
        } catch (e: Exception) {
            // fallback
        }

        val result = sb.toString().trim()
        return if (result.length > 40) result else extractPrintableAscii(bytes)
    }

    private fun inflatePdfStream(compressed: ByteArray): ByteArray {
        for (nowrap in listOf(false, true)) {
            val inflater = Inflater(nowrap)
            try {
                inflater.setInput(compressed)
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var loops = 0
                while (!inflater.finished() && loops < 1000) {
                    val count = inflater.inflate(buffer)
                    if (count <= 0) break
                    out.write(buffer, 0, count)
                    loops++
                }
                val res = out.toByteArray()
                if (res.isNotEmpty()) return res
            } catch (_: Exception) {
            } finally {
                inflater.end()
            }
        }
        return ByteArray(0)
    }

    private fun extractPdfOperatorsFromStream(streamContent: String, sb: StringBuilder) {
        // Look for PDF text operator tokens: (text) Tj or (text) '
        val tjPattern = Pattern.compile("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)\\s*(?:Tj|'|\\\")")
        val matcher = tjPattern.matcher(streamContent)
        while (matcher.find()) {
            val token = matcher.group(1) ?: ""
            sb.append(cleanPdfToken(token)).append(" ")
        }

        // Also search for TJ array chunks: [(text) -20 (more)] TJ
        val arrayPattern = Pattern.compile("\\[(.*?)\\]\\s*TJ", Pattern.DOTALL)
        val arrayMatcher = arrayPattern.matcher(streamContent)
        while (arrayMatcher.find()) {
            val arrayContent = arrayMatcher.group(1) ?: ""
            val innerMatcher = Pattern.compile("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)").matcher(arrayContent)
            while (innerMatcher.find()) {
                sb.append(cleanPdfToken(innerMatcher.group(1) ?: ""))
            }
            sb.append("\n")
        }

        // Hex string tokens: <48656C6C6F> Tj
        val hexPattern = Pattern.compile("<([0-9A-Fa-f\\s]{4,})>\\s*Tj")
        val hexMatcher = hexPattern.matcher(streamContent)
        while (hexMatcher.find()) {
            val hex = (hexMatcher.group(1) ?: "").replace("\\s".toRegex(), "")
            sb.append(decodePdfHexString(hex)).append(" ")
        }
    }

    private fun decodePdfHexString(hex: String): String {
        val sb = StringBuilder()
        val isUtf16 = hex.length % 4 == 0 && (hex.startsWith("00") || hex.startsWith("FEFF", ignoreCase = true))
        val step = if (isUtf16) 4 else 2
        var i = 0
        while (i + step <= hex.length) {
            val code = hex.substring(i, i + step).toIntOrNull(16) ?: 0
            if (code in 32..126 || code in 160..65533) {
                sb.append(code.toChar())
            }
            i += step
        }
        return sb.toString()
    }

    private fun cleanPdfToken(token: String): String {
        return token
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", " ")
            .replace("\\t", " ")
            .replace("\\\\", "\\")
    }

    /**
     * Renders up to the first 2 pages of a PDF to a composite Bitmap using Android's PdfRenderer
     * so Vision AI can optically inspect scanned or custom-font PDFs.
     */
    private fun renderPdfFirstPage(context: Context, bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        var tempFile: File? = null
        return try {
            tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}_${UUID.randomUUID()}.pdf")
            FileOutputStream(tempFile).use { it.write(bytes) }
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pagesToRender = minOf(renderer.pageCount, 2)
            if (pagesToRender > 0) {
                val pageBitmaps = mutableListOf<Bitmap>()
                for (i in 0 until pagesToRender) {
                    val page = renderer.openPage(i)
                    val width = (page.width * 1.5).toInt().coerceIn(400, 1200)
                    val height = (page.height * 1.5).toInt().coerceIn(500, 1600)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pageBitmaps.add(bmp)
                }
                renderer.close()
                pfd.close()

                if (pageBitmaps.size == 1) {
                    pageBitmaps.first()
                } else {
                    val maxWidth = pageBitmaps.maxOf { it.width }
                    val totalHeight = pageBitmaps.sumOf { it.height }
                    val combined = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888)
                    combined.eraseColor(Color.WHITE)
                    val canvas = Canvas(combined)
                    var yOffset = 0f
                    for (bmp in pageBitmaps) {
                        canvas.drawBitmap(bmp, 0f, yOffset, null)
                        yOffset += bmp.height
                    }
                    combined
                }
            } else {
                renderer.close()
                pfd.close()
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            tempFile?.delete()
        }
    }

    /**
     * Extracts printable text from legacy Word (.doc) or binary data
     */
    fun extractDocText(bytes: ByteArray): String {
        return extractPrintableAscii(bytes)
    }

    /**
     * Scans arbitrary byte chunks for continuous printable ASCII/UTF strings
     */
    private fun extractPrintableAscii(bytes: ByteArray): String {
        val sb = StringBuilder()
        val currentWord = StringBuilder()
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c in ' '..'~' || c == '\n' || c == '\t' || c == '\r') {
                currentWord.append(c)
            } else {
                if (currentWord.length >= 4) {
                    sb.append(currentWord).append(" ")
                }
                currentWord.clear()
            }
        }
        if (currentWord.length >= 4) {
            sb.append(currentWord)
        }
        return sb.toString().trim()
    }

    /**
     * Detects document format by file extension and MIME type
     */
    fun detectDocumentType(fileName: String, mimeType: String?): DocumentType {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".docx") || mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DocumentType.DOCX
            lower.endsWith(".pdf") || mimeType == "application/pdf" -> DocumentType.PDF
            lower.endsWith(".doc") || mimeType == "application/msword" -> DocumentType.DOC
            else -> DocumentType.TXT
        }
    }

    private fun queryFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = "Document_${System.currentTimeMillis()}"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // Use default
        }
        return Pair(name, size)
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    private fun estimateContactCount(text: String): Int {
        val emailRegex = "[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+"
        val pattern = Pattern.compile(emailRegex)
        val matcher = pattern.matcher(text)
        var count = 0
        while (matcher.find()) {
            count++
        }
        return count
    }
}

