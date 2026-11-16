package org.rsmod.content.other.websiteapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.michaelbull.logging.InlineLogger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DiscordWebhook(private val url: String) {
    private val logger = InlineLogger()
    private val mapper = jacksonObjectMapper()
    private val client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

    /** Posts [content] without pinging anyone; failures are logged, never thrown. */
    fun post(content: String) {
        val body =
            mapper.writeValueAsString(mapOf("content" to content, "allowed_mentions" to mapOf("parse" to emptyList<String>())))
        val request =
            HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() !in 200..299) {
                logger.warn { "Discord webhook returned HTTP ${response.statusCode()}" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Could not post to the Discord webhook" }
        }
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
