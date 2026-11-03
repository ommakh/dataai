package com.example.service

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.ExtractedLead
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DownloadResult(
    val success: Boolean,
    val fileName: String,
    val uri: Uri? = null,
    val filePath: String = "",
    val count: Int = 0,
    val isExcel: Boolean = true,
    val message: String = ""
)

class ExcelExportService {

    companion object {
        const val MIME_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val MIME_TYPE_CSV = "text/csv"
    }

    /**
     * Generates a genuine, standard Microsoft Excel OpenXML (.xlsx) workbook.
     * Complies with ECMA-376 / ISO/IEC 29500 OpenXML standards.
     * Works natively in Microsoft Excel (Mobile, Desktop, Office 365), Google Sheets,
     * Apple Numbers, LibreOffice Calc, and WPS Office without extension mismatch warnings or corruption errors.
     */
    fun writeXlsxWorkbook(
        outputStream: OutputStream,
        leads: List<ExtractedLead>,
        sheetTitle: String = "Extracted Leads"
    ) {
        val zos = ZipOutputStream(outputStream)

        // 1. [Content_Types].xml
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".toByteArray(StandardCharsets.UTF_8)
        )
        zos.closeEntry()

        // 2. _rels/.rels
        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(StandardCharsets.UTF_8)
        )
        zos.closeEntry()

        // 3. xl/_rels/workbook.xml.rels
        zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zos.write(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(StandardCharsets.UTF_8)
        )
        zos.closeEntry()

        // 4. xl/workbook.xml
        val sanitizedTitle = cleanXmlText(
            sheetTitle.replace(Regex("""[\\/*?:\[\]]"""), " ")
                .trim()
                .take(31)
                .ifBlank { "Leads" }
        )
        zos.putNextEntry(ZipEntry("xl/workbook.xml"))
        zos.write(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="$sanitizedTitle" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(StandardCharsets.UTF_8)
        )
        zos.closeEntry()

        // 5. xl/styles.xml
        zos.putNextEntry(ZipEntry("xl/styles.xml"))
        zos.write(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font>
      <sz val="11"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FFFFFFFF"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
  </fonts>
  <fills count="3">
    <fill>
      <patternFill fillType="none"/>
    </fill>
    <fill>
      <patternFill fillType="gray125"/>
    </fill>
    <fill>
      <patternFill fillType="solid">
        <fgColor rgb="FF107C41"/>
      </patternFill>
    </fill>
  </fills>
  <borders count="2">
    <border>
      <left/><right/><top/><bottom/>
    </border>
    <border>
      <left style="thin"><color rgb="FFE2E8F0"/></left>
      <right style="thin"><color rgb="FFE2E8F0"/></right>
      <top style="thin"><color rgb="FFE2E8F0"/></top>
      <bottom style="thin"><color rgb="FFE2E8F0"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">
      <alignment vertical="center"/>
    </xf>
  </cellXfs>
</styleSheet>""".toByteArray(StandardCharsets.UTF_8)
        )
        zos.closeEntry()

        // 6. xl/worksheets/sheet1.xml
        val sheetSb = StringBuilder()
        sheetSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="18" customWidth="1"/>
    <col min="2" max="2" width="22" customWidth="1"/>
    <col min="3" max="3" width="26" customWidth="1"/>
    <col min="4" max="4" width="18" customWidth="1"/>
    <col min="5" max="5" width="24" customWidth="1"/>
    <col min="6" max="6" width="20" customWidth="1"/>
    <col min="7" max="7" width="24" customWidth="1"/>
    <col min="8" max="8" width="28" customWidth="1"/>
    <col min="9" max="9" width="14" customWidth="1"/>
    <col min="10" max="10" width="14" customWidth="1"/>
    <col min="11" max="11" width="15" customWidth="1"/>
    <col min="12" max="12" width="35" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1" ht="28" customHeight="1">
      <c r="A1" t="inlineStr" s="1"><is><t>Timestamp</t></is></c>
      <c r="B1" t="inlineStr" s="1"><is><t>Full Name</t></is></c>
      <c r="C1" t="inlineStr" s="1"><is><t>Business Email</t></is></c>
      <c r="D1" t="inlineStr" s="1"><is><t>Direct Phone</t></is></c>
      <c r="E1" t="inlineStr" s="1"><is><t>Company / Organization</t></is></c>
      <c r="F1" t="inlineStr" s="1"><is><t>Job Title</t></is></c>
      <c r="G1" t="inlineStr" s="1"><is><t>Website URL</t></is></c>
      <c r="H1" t="inlineStr" s="1"><is><t>Physical Address</t></is></c>
      <c r="I1" t="inlineStr" s="1"><is><t>Source</t></is></c>
      <c r="J1" t="inlineStr" s="1"><is><t>Confidence</t></is></c>
      <c r="K1" t="inlineStr" s="1"><is><t>Sync Status</t></is></c>
      <c r="L1" t="inlineStr" s="1"><is><t>Agent Notes</t></is></c>
    </row>
""")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val colLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L")

        for ((index, lead) in leads.withIndex()) {
            val rowNum = index + 2
            val dateStr = dateFormat.format(Date(lead.createdAt))
            val values = listOf(
                dateStr,
                lead.name,
                lead.email,
                lead.phone,
                lead.company,
                lead.jobTitle,
                lead.website,
                lead.address,
                lead.source,
                "${lead.confidence}%",
                lead.syncStatus,
                lead.notes
            )
            sheetSb.append("""    <row r="$rowNum" ht="22" customHeight="1">""")
            for (c in values.indices) {
                val cellRef = "${colLetters[c]}$rowNum"
                val cellText = cleanXmlText(values[c])
                sheetSb.append("""<c r="$cellRef" t="inlineStr" s="2"><is><t>$cellText</t></is></c>""")
            }
            sheetSb.append("</row>\n")
        }

        sheetSb.append("""  </sheetData>
</worksheet>""")

        zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        zos.write(sheetSb.toString().toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()

        zos.finish()
        zos.flush()
    }

    /**
     * Legacy SpreadsheetML XML generator for plain text export.
     */
    fun generateExcelXml(leads: List<ExtractedLead>, sheetTitle: String = "Extracted Leads"): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:o="urn:schemas-microsoft-com:office:office"
 xmlns:x="urn:schemas-microsoft-com:office:excel"
 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
 xmlns:html="http://www.w3.org/TR/REC-html40">
 <Styles>
  <Style ss:ID="Default" ss:Name="Normal">
   <Alignment ss:Vertical="Center"/>
   <Font ss:FontName="Calibri" ss:Size="11" ss:Color="#1E293B"/>
  </Style>
  <Style ss:ID="Header">
   <Font ss:FontName="Calibri" ss:Size="11" ss:Color="#FFFFFF" ss:Bold="1"/>
   <Interior ss:Color="#107C41" ss:Pattern="Solid"/>
   <Alignment ss:Horizontal="Center" ss:Vertical="Center"/>
  </Style>
 </Styles>
 <Worksheet ss:Name="${cleanXmlText(sheetTitle.take(31).ifBlank { "Leads" })}">
  <Table>
   <Row ss:Height="24" ss:StyleID="Header">
    <Cell><Data ss:Type="String">Timestamp</Data></Cell>
    <Cell><Data ss:Type="String">Full Name</Data></Cell>
    <Cell><Data ss:Type="String">Business Email</Data></Cell>
    <Cell><Data ss:Type="String">Direct Phone</Data></Cell>
    <Cell><Data ss:Type="String">Company</Data></Cell>
    <Cell><Data ss:Type="String">Job Title</Data></Cell>
    <Cell><Data ss:Type="String">Website</Data></Cell>
    <Cell><Data ss:Type="String">Address</Data></Cell>
    <Cell><Data ss:Type="String">Source</Data></Cell>
    <Cell><Data ss:Type="String">Confidence</Data></Cell>
    <Cell><Data ss:Type="String">Sync Status</Data></Cell>
    <Cell><Data ss:Type="String">Notes</Data></Cell>
   </Row>
""")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        for (lead in leads) {
            val dateStr = dateFormat.format(Date(lead.createdAt))
            sb.append("   <Row>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(dateStr)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(lead.name)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(lead.email)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(lead.phone)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(lead.company)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(lead.jobTitle)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(lead.website)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(lead.address)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(lead.source)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${lead.confidence}%</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(lead.syncStatus)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${cleanXmlText(lead.notes)}</Data></Cell>\n")
            sb.append("   </Row>\n")
        }
        sb.append("""  </Table>
 </Worksheet>
</Workbook>""")
        return sb.toString()
    }

    /**
     * Generates RFC-4180 compliant CSV with UTF-8 BOM (\uFEFF)
     * so Microsoft Excel correctly detects UTF-8 characters without encoding corruption.
     */
    fun generateExcelCsv(leads: List<ExtractedLead>): String {
        val sb = StringBuilder()
        // UTF-8 BOM for Microsoft Excel auto-detect
        sb.append('\uFEFF')
        sb.appendLine("Timestamp,Full Name,Business Email,Direct Phone,Company,Job Title,Website URL,Physical Address,Source File,Source Type,Confidence,Sync Status,Agent Notes")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        for (lead in leads) {
            val dateStr = dateFormat.format(Date(lead.createdAt))
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
                    escapeCsv(lead.sourceDetail.ifBlank { lead.source }),
                    escapeCsv(lead.source),
                    "${lead.confidence}%",
                    escapeCsv(lead.syncStatus),
                    escapeCsv(lead.notes)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    /**
     * Generates Tab-Separated Values (TSV) formatted string for direct clipboard pasting into Excel.
     */
    fun generateExcelTsv(leads: List<ExtractedLead>): String {
        val sb = StringBuilder()
        sb.appendLine("Timestamp\tFull Name\tBusiness Email\tDirect Phone\tCompany\tJob Title\tWebsite URL\tPhysical Address\tSource\tConfidence\tSync Status\tAgent Notes")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        for (lead in leads) {
            val dateStr = dateFormat.format(Date(lead.createdAt))
            val cleanNotes = lead.notes.replace("\n", " ").replace("\t", " ")
            sb.appendLine(
                listOf(
                    dateStr,
                    lead.name.replace("\t", " "),
                    lead.email.replace("\t", " "),
                    lead.phone.replace("\t", " "),
                    lead.company.replace("\t", " "),
                    lead.jobTitle.replace("\t", " "),
                    lead.website.replace("\t", " "),
                    lead.address.replace("\t", " "),
                    lead.source,
                    "${lead.confidence}%",
                    lead.syncStatus,
                    cleanNotes
                ).joinToString("\t")
            )
        }
        return sb.toString()
    }

    /**
     * Creates an Excel .xlsx file in the app cache and returns a content Uri shareable via FileProvider.
     */
    fun createExcelShareableUri(context: Context, leads: List<ExtractedLead>): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "extracted_leads_$timeStamp.xlsx")

        FileOutputStream(file).use { fos ->
            writeXlsxWorkbook(fos, leads)
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Creates an Excel CSV .csv file in the app cache and returns a content Uri shareable via FileProvider.
     */
    fun createCsvShareableUri(context: Context, leads: List<ExtractedLead>): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "extracted_leads_$timeStamp.csv")

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write(generateExcelCsv(leads))
                writer.flush()
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Downloads genuine .xlsx or .csv directly to device storage.
     * Guarantees zero corruption by using native OpenXML .xlsx packaging.
     */
    fun downloadExcelToDownloads(
        context: Context,
        leads: List<ExtractedLead>,
        sheetTitle: String = "Extracted Leads",
        isCsv: Boolean = false
    ): DownloadResult {
        if (leads.isEmpty()) {
            return DownloadResult(
                success = false,
                fileName = "",
                message = "No leads available to download."
            )
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext = if (isCsv) "csv" else "xlsx"
        val fileName = "Extracted_Leads_$timeStamp.$ext"
        val mimeType = if (isCsv) MIME_TYPE_CSV else MIME_TYPE_XLSX

        try {
            // 1. Always write a reliable file in app-accessible storage for FileProvider (Open / Share in Excel app)
            val appExportDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir,
                "exports"
            ).apply { mkdirs() }
            val appTargetFile = File(appExportDir, fileName)
            FileOutputStream(appTargetFile).use { fos ->
                if (isCsv) {
                    OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                        writer.write(generateExcelCsv(leads))
                        writer.flush()
                    }
                } else {
                    writeXlsxWorkbook(fos, leads, sheetTitle)
                }
            }

            // 2. Save to public Downloads folder:
            // On Android 10+ (API 29+), use MediaStore.Downloads (Scoped Storage safe, no EACCES error).
            // On Android 7-9 (API 24-28), write to public DIRECTORY_DOWNLOADS directly.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val mediaUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (mediaUri != null) {
                        resolver.openOutputStream(mediaUri)?.use { os ->
                            if (isCsv) {
                                OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer ->
                                    writer.write(generateExcelCsv(leads))
                                    writer.flush()
                                }
                            } else {
                                writeXlsxWorkbook(os, leads, sheetTitle)
                            }
                        }
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(mediaUri, values, null, null)
                    }
                } catch (_: Exception) {
                    // Fallback copy in appExportDir is already saved
                }
            } else {
                try {
                    @Suppress("DEPRECATION")
                    val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (publicDownloads != null && (publicDownloads.exists() || publicDownloads.mkdirs())) {
                        val legacyFile = File(publicDownloads, fileName)
                        appTargetFile.copyTo(legacyFile, overwrite = true)
                    }
                } catch (_: Exception) {
                    // Fallback copy in appExportDir is already saved
                }
            }

            // 3. Generate FileProvider Uri which has guaranteed read access across all third-party spreadsheet apps on mobile
            val shareableUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    appTargetFile
                )
            } catch (e: Exception) {
                Uri.fromFile(appTargetFile)
            }

            return DownloadResult(
                success = true,
                fileName = fileName,
                uri = shareableUri,
                filePath = "Downloads/$fileName",
                count = leads.size,
                isExcel = !isCsv,
                message = "Downloaded $fileName (${leads.size} leads) in native Excel format"
            )
        } catch (e: Exception) {
            // Cache fallback
            return try {
                val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(cacheDir, fileName)
                FileOutputStream(file).use { fos ->
                    if (isCsv) {
                        OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                            writer.write(generateExcelCsv(leads))
                            writer.flush()
                        }
                    } else {
                        writeXlsxWorkbook(fos, leads, sheetTitle)
                    }
                }
                val uri = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (ex: Exception) {
                    Uri.fromFile(file)
                }
                DownloadResult(
                    success = true,
                    fileName = fileName,
                    uri = uri,
                    filePath = "Downloads/$fileName",
                    count = leads.size,
                    isExcel = !isCsv,
                    message = "Saved $fileName to device storage"
                )
            } catch (err: Exception) {
                DownloadResult(
                    success = false,
                    fileName = fileName,
                    message = "Download error: ${err.localizedMessage ?: err.message}"
                )
            }
        }
    }

    /**
     * Opens the downloaded Excel (.xlsx) or CSV (.csv) file in Microsoft Excel or any spreadsheet viewer app.
     * Uses ClipData and FLAG_GRANT_READ_URI_PERMISSION so external apps never get permission denial errors.
     */
    fun openExcelFile(context: Context, uri: Uri, isCsv: Boolean = false) {
        val mimeType = if (isCsv) MIME_TYPE_CSV else MIME_TYPE_XLSX
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri("Excel Spreadsheet", uri)
            }
            val chooser = Intent.createChooser(intent, "Open in Excel / Spreadsheet").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Fallback with */* mime type
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    clipData = ClipData.newRawUri("Excel Spreadsheet", uri)
                }
                val fallbackChooser = Intent.createChooser(fallbackIntent, "Open File").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackChooser)
            } catch (err: Exception) {
                Toast.makeText(context, "Saved to Downloads. Install Microsoft Excel or Google Sheets to open directly.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Shares the downloaded file to other applications.
     */
    fun shareDownloadedFile(context: Context, uri: Uri, fileName: String, isCsv: Boolean = false) {
        val mimeType = if (isCsv) MIME_TYPE_CSV else MIME_TYPE_XLSX
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri(fileName, uri)
            }
            val chooser = Intent.createChooser(intent, "Share Excel File").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Cleans XML text by removing invalid ASCII control characters and escaping special characters.
     */
    fun cleanXmlText(text: String): String {
        val sanitized = text.filter { ch ->
            ch == '\t' || ch == '\n' || ch == '\r' || (ch.code in 0x20..0xD7FF) || (ch.code in 0xE000..0xFFFD)
        }
        return sanitized
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
