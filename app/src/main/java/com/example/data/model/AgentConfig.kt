package com.example.data.model

data class AgentConfig(
    val agentName: String = "Card Scanner",
    val agentPersona: String = "English Card & Document to Excel Converter",
    val customInstructions: String = "Extract accurate full names, business emails, direct phone numbers, job titles, and company affiliations offline.",
    val extractionDepth: String = "DETAILED", // "FAST", "DETAILED", "EXHAUSTIVE"
    val autoSyncToSheets: Boolean = false,
    val googleSheetWebhookUrl: String = "",
    val spreadsheetName: String = "Extracted Contacts",
    val sheetTabName: String = "Contacts",
    val customApiKey: String = ""
)

enum class ExtractionPreset(
    val title: String,
    val description: String,
    val instructions: String,
    val depth: String
) {
    SALES_LEADS(
        title = "Sales Prospector",
        description = "Optimized for corporate decision-makers, executives, and company leads.",
        instructions = "Focus on leadership roles (Founder, CEO, VP, Director). Extract corporate domain emails, direct mobile/desk phone, and company HQ address.",
        depth = "DETAILED"
    ),
    EVENT_BADGES(
        title = "Card & Badge Scanner",
        description = "Best for scanning physical business cards, conference badges, and desk nameplates.",
        instructions = "Carefully identify business card typography. Separate personal name from company name. Parse phone extensions and scan card notes or credentials.",
        depth = "EXHAUSTIVE"
    ),
    WEB_DIRECTORY(
        title = "Website Directory Miner",
        description = "Geared towards About Us, Team, and Contact Us web pages.",
        instructions = "Scan for structured team listings, contact anchor links, mailto/tel schemas, and physical addresses from website footers and headers.",
        depth = "DETAILED"
    )
}
