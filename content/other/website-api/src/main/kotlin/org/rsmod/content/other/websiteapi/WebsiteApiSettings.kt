package org.rsmod.content.other.websiteapi

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties

class WebsiteApiSettings(private val file: Path) {
    var enabled: Boolean = true
    var bindAddress: String = "127.0.0.1"
    var port: Int = 8091
    var apiKey: String = ""
    var discordWebhookUrl: String = ""

    fun load() {
        if (Files.exists(file)) {
            val props = Properties()
            Files.newBufferedReader(file).use(props::load)
            enabled = props.getProperty(ENABLED)?.toBoolean() ?: true
            bindAddress = props.getProperty(BIND_ADDRESS)?.trim()?.ifEmpty { null } ?: "127.0.0.1"
            port = props.getProperty(PORT)?.trim()?.toIntOrNull() ?: 8091
            apiKey = props.getProperty(API_KEY)?.trim().orEmpty()
            discordWebhookUrl = props.getProperty(DISCORD_WEBHOOK_URL)?.trim().orEmpty()
        }
        if (apiKey.length < MIN_KEY_LENGTH) {
            apiKey = generateKey()
        }
        save()
    }

    fun save() {
        val props = Properties()
        props.setProperty(ENABLED, enabled.toString())
        props.setProperty(BIND_ADDRESS, bindAddress)
        props.setProperty(PORT, port.toString())
        props.setProperty(API_KEY, apiKey)
        props.setProperty(DISCORD_WEBHOOK_URL, discordWebhookUrl)
        Files.createDirectories(file.parent)
        Files.newBufferedWriter(file).use {
            props.store(
                it,
                "Website API settings. Copy api_key into website/.env.local. " +
                    "discord_webhook_url (optional) receives level-99 and max-total announcements.",
            )
        }
    }

    private fun generateKey(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val ENABLED = "enabled"
        const val BIND_ADDRESS = "bind_address"
        const val PORT = "port"
        const val API_KEY = "api_key"
        const val DISCORD_WEBHOOK_URL = "discord_webhook_url"
        const val MIN_KEY_LENGTH = 24
    }
}
