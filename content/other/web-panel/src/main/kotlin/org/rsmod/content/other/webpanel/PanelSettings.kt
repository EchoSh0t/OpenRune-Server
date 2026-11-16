package org.rsmod.content.other.webpanel

import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties

class PanelSettings(private val file: Path) {
    var maintenance: Boolean = false
    var globalXpRate: Double = 1.0
    var bindAddress: String = "127.0.0.1"
    var apiKey: String = ""

    fun load() {
        if (Files.exists(file)) {
            val props = Properties()
            Files.newBufferedReader(file).use(props::load)
            maintenance = props.getProperty(MAINTENANCE)?.toBoolean() ?: false
            globalXpRate = props.getProperty(GLOBAL_XP_RATE)?.toDoubleOrNull() ?: 1.0
            bindAddress = props.getProperty(BIND_ADDRESS)?.trim()?.ifEmpty { null } ?: "127.0.0.1"
            apiKey = props.getProperty(API_KEY)?.trim().orEmpty()
        }
        if (apiKey.length < MIN_KEY_LENGTH) {
            apiKey = generateKey()
        }
    }

    fun save() {
        val props = Properties()
        props.setProperty(MAINTENANCE, maintenance.toString())
        props.setProperty(GLOBAL_XP_RATE, globalXpRate.toString())
        props.setProperty(BIND_ADDRESS, bindAddress)
        props.setProperty(API_KEY, apiKey)
        Files.createDirectories(file.parent)
        Files.newBufferedWriter(file).use {
            props.store(it, "Admin API settings. Copy api_key into website/.env.local as ADMIN_API_KEY")
        }
    }

    private fun generateKey(): String {
        val bytes = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val MAINTENANCE = "maintenance"
        const val GLOBAL_XP_RATE = "global_xp_rate"
        const val BIND_ADDRESS = "bind_address"
        const val API_KEY = "api_key"
        const val MIN_KEY_LENGTH = 24
        const val KEY_BYTES = 32
    }
}
