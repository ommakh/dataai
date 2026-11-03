package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class WebsiteScraperService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun scrapeWebsite(rawUrl: String): Result<ScrapedPageData> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(rawUrl)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
            }

            val html = response.body?.string() ?: ""
            val parsedText = extractReadableText(html)
            val detectedEmails = extractRegexMatches(html, "[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
            val detectedPhones = extractRegexMatches(html, "(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}")

            Result.success(
                ScrapedPageData(
                    url = url,
                    cleanText = parsedText,
                    detectedEmails = detectedEmails.take(5),
                    detectedPhones = detectedPhones.take(5),
                    rawLength = html.length
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    private fun extractReadableText(html: String): String {
        // Strip scripts, styles, head, comments
        var text = html
            .replace("(?is)<script.*?>.*?</script>".toRegex(), " ")
            .replace("(?is)<style.*?>.*?</style>".toRegex(), " ")
            .replace("(?is)<!--.*?-->".toRegex(), " ")
            .replace("(?is)<header.*?>.*?</header>".toRegex(), " ")
            .replace("(?is)<footer.*?>.*?</footer>".toRegex(), " ")
            .replace("<[^>]+>".toRegex(), " ")
            .replace("&nbsp;".toRegex(), " ")
            .replace("&amp;".toRegex(), "&")
            .replace("&lt;".toRegex(), "<")
            .replace("&gt;".toRegex(), ">")
            .replace("&quot;".toRegex(), "\"")
            .replace("&#39;".toRegex(), "'")

        // Collapse whitespace
        text = text.replace("\\s+".toRegex(), " ").trim()
        // Keep first 12,000 characters to fit easily into model token budget
        return text.take(12000)
    }

    private fun extractRegexMatches(content: String, regex: String): List<String> {
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(content)
        val results = linkedSetOf<String>()
        while (matcher.find()) {
            val item = matcher.group().trim()
            if (item.length > 5) {
                results.add(item)
            }
        }
        return results.toList()
    }
}

data class ScrapedPageData(
    val url: String,
    val cleanText: String,
    val detectedEmails: List<String> = emptyList(),
    val detectedPhones: List<String> = emptyList(),
    val rawLength: Int = 0
)
