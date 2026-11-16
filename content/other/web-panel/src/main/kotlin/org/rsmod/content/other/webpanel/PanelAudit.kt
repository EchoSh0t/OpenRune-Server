package org.rsmod.content.other.webpanel

import com.github.michaelbull.logging.InlineLogger
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PanelAudit(private val file: Path) {
    private val logger = InlineLogger()

    @Synchronized
    fun record(user: String?, client: String, event: String) {
        val line = "${LocalDateTime.now().format(TIME)} user=${user ?: "-"} client=$client $event"
        logger.info { "Web panel: $line" }
        try {
            Files.createDirectories(file.parent)
            Files.writeString(
                file,
                line.replace('\n', ' ') + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        } catch (e: Exception) {
            logger.warn(e) { "Could not write web panel audit log." }
        }
    }

    /** Newest first, read from the last [TAIL_BYTES] of the log so a huge file stays cheap. */
    @Synchronized
    fun recent(filter: String?, limit: Int): List<AuditEntryView> {
        if (!Files.exists(file)) {
            return emptyList()
        }
        val needle = filter?.trim()?.lowercase()?.ifEmpty { null }
        return tail()
            .lineSequence()
            .mapNotNull(::parse)
            .filter { needle == null || "${it.user} ${it.client} ${it.event}".lowercase().contains(needle) }
            .toList()
            .takeLast(limit)
            .reversed()
    }

    private fun tail(): String =
        RandomAccessFile(file.toFile(), "r").use { raf ->
            val start = (raf.length() - TAIL_BYTES).coerceAtLeast(0)
            raf.seek(start)
            val bytes = ByteArray((raf.length() - start).toInt())
            raf.readFully(bytes)
            val text = String(bytes, StandardCharsets.UTF_8)
            if (start > 0) text.substringAfter('\n') else text
        }

    private fun parse(line: String): AuditEntryView? {
        val match = LINE.matchEntire(line.trim()) ?: return null
        val (time, user, client, event) = match.destructured
        return AuditEntryView(time, user, client, event)
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val LINE = Regex("^(\\S+ \\S+) user=(\\S+) client=(\\S+) (.*)$")
        const val TAIL_BYTES = 2L * 1024 * 1024
    }
}
