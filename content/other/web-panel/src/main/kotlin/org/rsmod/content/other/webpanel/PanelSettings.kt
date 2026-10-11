package org.rsmod.content.other.webpanel

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class PanelSettings(private val file: Path) {
    var maintenance: Boolean = false
    var globalXpRate: Double = 1.0

    fun load() {
        if (!Files.exists(file)) {
            return
        }
        val props = Properties()
        Files.newBufferedReader(file).use(props::load)
        maintenance = props.getProperty(MAINTENANCE)?.toBoolean() ?: false
        globalXpRate = props.getProperty(GLOBAL_XP_RATE)?.toDoubleOrNull() ?: 1.0
    }

    fun save() {
        val props = Properties()
        props.setProperty(MAINTENANCE, maintenance.toString())
        props.setProperty(GLOBAL_XP_RATE, globalXpRate.toString())
        Files.createDirectories(file.parent)
        Files.newBufferedWriter(file).use { props.store(it, "OpenRune web panel settings") }
    }

    private companion object {
        const val MAINTENANCE = "maintenance"
        const val GLOBAL_XP_RATE = "global_xp_rate"
    }
}
