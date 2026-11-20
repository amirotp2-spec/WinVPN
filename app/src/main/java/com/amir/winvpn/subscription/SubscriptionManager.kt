package com.amir.winvpn.subscription

import android.util.Base64
import com.amir.winvpn.config.ConfigParser
import com.amir.winvpn.config.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class SubscriptionEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastUpdated: Long = 0L,
    val configIds: List<String> = emptyList()
)

data class ParsedSubscriptionConfig(
    val raw: String,
    val name: String?,
    val parsed: ParseResult.Valid
)

data class SubscriptionResult(
    val configs: List<ParsedSubscriptionConfig>,
    val totalLines: Int,
    val failedLines: Int
)

class SubscriptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SubscriptionManager {
    suspend fun fetch(urlString: String): SubscriptionResult = withContext(Dispatchers.IO) {
        val url = urlString.trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            throw SubscriptionException("Subscription URL must start with http:// or https://")
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WinVPN/3.0 Android")
            setRequestProperty("Accept", "text/plain,*/*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw SubscriptionException("Subscription HTTP error: $code")
            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(raw)
        } catch (e: SubscriptionException) {
            throw e
        } catch (e: Exception) {
            throw SubscriptionException("Subscription download failed", e)
        } finally {
            connection.disconnect()
        }
    }

    fun parse(content: String): SubscriptionResult {
        val decoded = decodeIfBase64(content.trim())
        val lines = decoded.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        val out = ArrayList<ParsedSubscriptionConfig>()
        var failed = 0
        for (line in lines) {
            when (val parsed = ConfigParser.parse(line)) {
                is ParseResult.Valid -> out += ParsedSubscriptionConfig(line, parsed.name, parsed)
                else -> failed++
            }
        }
        return SubscriptionResult(out.distinctBy { it.raw }, lines.size, failed)
    }

    private fun decodeIfBase64(value: String): String {
        if (value.contains("://")) return value
        val normalized = value.replace('-', '+').replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        return runCatching { String(Base64.decode(normalized, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(value)
    }
}
