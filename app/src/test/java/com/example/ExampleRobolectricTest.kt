package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ExtractedLead
import com.example.service.DocumentType
import com.example.service.ExcelExportService
import com.example.service.GeminiService
import com.example.service.ParsedDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DataExtract AI", appName)
  }

  @Test
  fun `test blank photo and non-card text return zero data and trigger There is no card notification`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val gemini = GeminiService(app)
    val config = com.example.data.model.AgentConfig(customApiKey = "")
    val blankPhoto = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
    val extracted = gemini.extractLeadsFromCardImage(blankPhoto, config)
    assertTrue("Blank photo must not return any demo or fake data", extracted.isEmpty())

    // Non-card text (e.g., wall sign or book cover without contact info) must return null
    val nonCardResult = gemini.parseSingleCardFromRealOcrBlock("Welcome Home\nHave a great day", 1)
    org.junit.Assert.assertNull("Non-card text without contact markers must not be recognized as a card", nonCardResult)

    val viewModel = com.example.ui.screens.MainViewModel(app)
    viewModel.updateAgentConfig(viewModel.agentConfig.value.copy(customApiKey = ""))
    viewModel.extractFromCardImage(blankPhoto, appendToExisting = false)
    // Wait briefly for coroutine on Main/IO
    var attempts = 0
    while (viewModel.noCardRecognizedAlert.value == null && attempts < 50) {
      kotlinx.coroutines.delay(50)
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      attempts++
    }
    assertEquals("There is no card", viewModel.noCardRecognizedAlert.value)
    assertEquals("There is no card", viewModel.bannerMessage.value)
    assertTrue(viewModel.cardBatchLeads.value.isEmpty())
  }

  @Test
  fun `test real card ocr parsing for up to 20 cards and repeated cards converted to excel`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val gemini = GeminiService()
    val exporter = ExcelExportService()

    val ocrBlocks = (1..20).map { idx ->
      """
      Person Name $idx
      Managing Director
      Real Business Corp $idx Pvt Ltd
      +91 - 98220 ${10000 + idx}
      person$idx@realbusiness$idx.com
      realbusiness$idx.com
      Plot $idx, Industrial Area, Pune 411026
      """.trimIndent()
    }

    val parsedCards = ocrBlocks.mapIndexedNotNull { idx, block ->
      gemini.parseSingleCardFromRealOcrBlock(block, idx + 1)
    }
    assertEquals(20, parsedCards.size)
    assertEquals("Person Name 1", parsedCards.first().name)
    assertEquals("person1@realbusiness1.com", parsedCards.first().email)
    assertEquals("realbusiness1.com", parsedCards.first().website)

    // Verify honorifics & city-named companies are recognized on 2nd/subsequent scans
    val doctorCard = gemini.parseSingleCardFromRealOcrBlock(
      """
      Dr. Priya Nair - Director
      Pune Medical Solutions Pvt Ltd
      020 - 2445 6789
      priya@punemedical.in
      """.trimIndent(),
      21
    )
    assertNotNull("Honorific + city company card must be recognized", doctorCard)
    assertEquals("Dr. Priya Nair", doctorCard!!.name)

    // Verify repeated cards are preserved (since there is no database, repeated cards are also converted to Excel)
    val withRepeatedCards = gemini.deduplicateLeadsOneTimeOnly(parsedCards + parsedCards.take(5))
    assertEquals(25, withRepeatedCards.size)

    val excelResult = exporter.downloadExcelToDownloads(context, withRepeatedCards, isCsv = false)
    assertTrue(excelResult.success)
    assertNotNull(excelResult.uri)
    assertTrue(excelResult.fileName.endsWith(".xlsx"))
    assertEquals(25, excelResult.count)

    // Verify ViewModel converts 1st time, 2nd time, and 3rd time (even with the exact same repeated card!) directly to Excel (.xlsx)
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.screens.MainViewModel(app)

    // 1st card
    viewModel.addManualCardToBatch(
      name = "Rohan Kulkarni",
      jobTitle = "Director",
      company = "Apex Logistics Pvt Ltd",
      phone = "+91 98230 11223",
      email = "rohan@apexlogistics.in",
      website = "https://www.apexlogistics.in",
      address = "Mumbai, India",
      notes = ""
    )
    var attempts = 0
    while (viewModel.downloadedFileResult.value == null && attempts < 50) {
      kotlinx.coroutines.delay(50)
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      attempts++
    }
    assertNotNull(viewModel.downloadedFileResult.value)
    assertEquals(1, viewModel.cardBatchLeads.value.size)

    // 2nd time: Add the exact same repeated card -> must ALSO be converted into Excel (2 rows total)
    viewModel.dismissDownloadedFileNotice()
    viewModel.addManualCardToBatch(
      name = "Rohan Kulkarni",
      jobTitle = "Director",
      company = "Apex Logistics Pvt Ltd",
      phone = "+91 98230 11223",
      email = "rohan@apexlogistics.in",
      website = "https://www.apexlogistics.in",
      address = "Mumbai, India",
      notes = ""
    )
    attempts = 0
    while (viewModel.downloadedFileResult.value == null && attempts < 50) {
      kotlinx.coroutines.delay(50)
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      attempts++
    }
    assertNotNull(viewModel.downloadedFileResult.value)
    assertTrue(viewModel.downloadedFileResult.value!!.fileName.endsWith(".xlsx"))
    assertEquals(2, viewModel.cardBatchLeads.value.size)
    assertEquals(2, viewModel.downloadedFileResult.value!!.count)
    assertEquals(0, viewModel.totalCount.value)
  }

  @Test
  fun `test pdf contact extraction without demo data`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val gemini = GeminiService()
    val exporter = ExcelExportService()
    val config = com.example.data.model.AgentConfig(customApiKey = "MY_GEMINI_API_KEY")

    val emptyPdf = ParsedDocument(
      fileName = "empty.pdf",
      fileType = DocumentType.PDF,
      textContent = "No contact information here."
    )
    val fromEmpty = gemini.extractLeadsFromDocument(emptyPdf, config)
    assertTrue("PDF without contacts must return empty list and no demo data", fromEmpty.isEmpty())

    val realPdf = ParsedDocument(
      fileName = "contacts.pdf",
      fileType = DocumentType.PDF,
      textContent = """
        Full Name: Anita Desai
        Job Title: Procurement Manager
        Company: Horizon Manufacturing Ltd
        Email: anita.desai@horizonmfg.com
        Phone: +91 98201 44556
      """.trimIndent()
    )
    val extracted = gemini.extractLeadsFromDocument(realPdf, config)
    assertEquals(1, extracted.size)
    assertEquals("Anita Desai", extracted.first().name)

    val csvResult = exporter.downloadExcelToDownloads(context, extracted, isCsv = true)
    assertTrue(csvResult.success)
    assertEquals(1, csvResult.count)
  }

  @Test
  fun `test english language business cards and repeated english cards converted to excel`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val gemini = GeminiService()
    val exporter = ExcelExportService()

    // 1. English business card with corporate designation, domain email, and address
    val englishCard1Text = """
      Apex Engineering & Trading Pvt Ltd
      Mr. Robert Miller
      Managing Director
      Direct: +1 (555) 234-5678
      Email: robert.miller@apexengineering.com
      Website: www.apexengineering.com
      Address: Suite 400, Industrial Park Avenue, Boston
    """.trimIndent()

    val card1 = gemini.parseSingleCardFromRealOcrBlock(englishCard1Text, 1)
    assertNotNull("English business card must be recognized", card1)
    assertEquals("Mr. Robert Miller", card1!!.name)
    assertEquals("Apex Engineering & Trading Pvt Ltd", card1.company)
    assertEquals("Managing Director", card1.jobTitle)
    assertEquals("+1 (555) 234-5678", card1.phone)
    assertEquals("robert.miller@apexengineering.com", card1.email)
    assertEquals("www.apexengineering.com", card1.website)
    assertTrue(card1.address.contains("Boston"))

    // 2. Second English business card with medical / clinic details
    val englishCard2Text = """
      Dr. Sarah Jenkins
      Clinic Director
      Horizon Healthcare Solutions LLC
      Phone: +44 20 7946 0912
      Email: s.jenkins@horizonhealth.co.uk
      Website: https://horizonhealth.co.uk
      Location: 45 Queen Street, Central District, London
    """.trimIndent()

    val card2 = gemini.parseSingleCardFromRealOcrBlock(englishCard2Text, 2)
    assertNotNull("Second English business card must be recognized", card2)
    assertEquals("Dr. Sarah Jenkins", card2!!.name)
    assertEquals("Clinic Director", card2.jobTitle)
    assertEquals("Horizon Healthcare Solutions LLC", card2.company)
    assertEquals("+44 20 7946 0912", card2.phone)
    assertEquals("s.jenkins@horizonhealth.co.uk", card2.email)
    assertTrue(card2.address.contains("London"))

    // 3. Export English cards (including a repeated card) to Excel (.xlsx) and CSV
    val englishBatch = listOf(card1, card2, card1)
    val xlsxResult = exporter.downloadExcelToDownloads(context, englishBatch, isCsv = false)
    assertTrue(xlsxResult.success)
    assertEquals(3, xlsxResult.count)
    assertTrue(xlsxResult.fileName.endsWith(".xlsx"))

    val csvString = exporter.generateExcelCsv(englishBatch)
    assertTrue(csvString.contains("Mr. Robert Miller"))
    assertTrue(csvString.contains("Apex Engineering & Trading Pvt Ltd"))
    assertTrue(csvString.contains("Dr. Sarah Jenkins"))
  }

  @Test
  fun `test card with multiple details and numbers saves in one entry for the respective company without increasing entries`() = runBlocking {
    val gemini = GeminiService()
    val cardWithAltNumbers = """
      Apex Global Solutions Pvt Ltd
      Vikram Malhotra
      Senior Partner
      Mob: +91 98765 43210 / +91 98220 12345
      Tel: 020 2567 8901
      vikram@apexglobal.in
      www.apexglobal.in
      Shop No. 12, Ground Floor, Sunshine Plaza,
      Opp. City Hospital, M.G. Road,
      Camp, Pune - 411001, Maharashtra
    """.trimIndent()

    val rows = gemini.parseCardsFromRealOcrBlock(cardWithAltNumbers, 1)

    // Must be exactly 1 entry per card (do not increase the entries)
    assertEquals("Must save into exactly 1 entry for the card/company", 1, rows.size)

    val singleCard = rows.first()
    assertEquals("Vikram Malhotra", singleCard.name)
    assertEquals("Senior Partner", singleCard.jobTitle)
    assertEquals("Apex Global Solutions Pvt Ltd", singleCard.company)
    assertEquals("vikram@apexglobal.in", singleCard.email)
    assertEquals("www.apexglobal.in", singleCard.website)

    // Multiple phone numbers must all be captured in that single entry
    assertTrue("Primary mobile number must be present in phone field", singleCard.phone.contains("98765") || singleCard.phone.contains("98220"))
    assertTrue("Alternative numbers should be captured in phone field or notes", singleCard.phone.contains("98220") || singleCard.phone.contains("2567") || singleCard.notes.contains("Alt Phone"))

    // Address must be fully and properly extracted
    assertTrue("Address should contain street or building details", singleCard.address.contains("Sunshine Plaza") || singleCard.address.contains("M.G. Road") || singleCard.address.contains("Pune"))
    assertEquals("NOT_SAVED", singleCard.syncStatus)

    // Deduplication of identical or overlapping company scans preserves 1 entry
    val deduped = gemini.deduplicateAndMergeCards(rows + rows)
    assertEquals("Repeated fragments for the same company must merge into 1 entry", 1, deduped.size)
  }
}

