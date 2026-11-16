package dev.openrune.gamevals

import java.io.File
import kotlin.system.exitProcess

object GamevalLint {
    private val SCANNED_DIRS = listOf("content", "api", "server")
    private val SCANNED_EXTENSIONS = setOf("kt", "kts", "toml")
    private val EXCLUDED_PATH_PARTS =
        listOf("/build/", "/out/", "/target/", "/api/generated/", "/src/test/")
    private val SYMBOL_LITERAL = Regex("\"([a-z]+)\\.([A-Za-z0-9_:+\\-]+)\"")
    private val PREFIX_CALL = Regex("(startsWith|removePrefix)\\(\\s*$")
    private val VAL_ASSIGNMENT = Regex("\\bval\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(:\\s*String\\s*)?=\\s*$")
    private const val MAX_SUGGESTIONS = 3

    data class Reference(val file: File, val line: Int, val column: Int, val symbol: String) {
        val table: String
            get() = symbol.substringBefore('.')
    }

    data class Finding(val reference: Reference, val unassigned: Boolean, val suggestions: List<String>)

    data class Report(val scannedFiles: Int, val references: Int, val findings: List<Finding>) {
        val errors: List<Finding>
            get() = findings.filterNot { it.unassigned }

        val pending: List<Finding>
            get() = findings.filter { it.unassigned }
    }

    fun lint(rootDir: File): Report {
        val provider = GameValProvider.loadIsolated(rootDir = rootDir.absolutePath + File.separator)
        val mappings = provider.mappings
        val files = sourceFiles(rootDir)
        val references = files.flatMap { findReferences(it, mappings.keys) }

        val findings =
            references.mapNotNull { ref ->
                when (mappings[ref.table]?.get(ref.symbol)) {
                    null -> Finding(ref, unassigned = false, suggestions(ref.symbol, mappings))
                    GameValProvider.UNASSIGNED_ID -> Finding(ref, unassigned = true, emptyList())
                    else -> null
                }
            }
        return Report(files.size, references.size, findings)
    }

    fun sourceFiles(rootDir: File): List<File> =
        SCANNED_DIRS.map { File(rootDir, it) }
            .filter(File::isDirectory)
            .flatMap { dir ->
                dir.walk()
                    .onEnter { sub ->
                        val path = "/${sub.relativeTo(rootDir).invariantSeparatorsPath}/"
                        EXCLUDED_PATH_PARTS.none { it in path }
                    }
                    .filter { it.isFile && it.extension in SCANNED_EXTENSIONS && it.name != "gamevals.toml" }
                    .toList()
            }

    fun findReferences(file: File, tables: Set<String>): List<Reference> =
        findReferences(file, file.readLines(), tables)

    fun findReferences(file: File, lines: List<String>, tables: Set<String>): List<Reference> {
        val text = lines.joinToString("\n")
        val references = mutableListOf<Reference>()
        lines.forEachIndexed { index, line ->
            if (isComment(line, file.extension)) return@forEachIndexed
            SYMBOL_LITERAL.findAll(line).forEach { match ->
                val table = match.groupValues[1]
                if (table !in tables) return@forEach

                val sameLine = line.substring(0, match.range.first)
                val before = if (sameLine.isBlank() && index > 0) lines[index - 1] + " " else sameLine
                val after = line.substring(match.range.last + 1)
                if (isPrefixFragment(before, after, text)) return@forEach

                val symbol = "$table.${match.groupValues[2]}"
                references += Reference(file, index + 1, match.range.first + 2, symbol)
            }
        }
        return references
    }

    /**
     * A literal that is only the start of a symbol: concatenated (`"npc.cone_" + i`), passed to
     * `startsWith`/`removePrefix`, or held in a val that the file later uses the same way or
     * extends inside a template (`"$END_LEVEL:btn_claim"`).
     */
    private fun isPrefixFragment(before: String, after: String, text: String): Boolean {
        if (after.trimStart().startsWith("+") || PREFIX_CALL.containsMatchIn(before)) return true

        val name = VAL_ASSIGNMENT.find(before)?.groupValues?.get(1) ?: return false
        val usedAsPrefix =
            Regex(
                "(startsWith|removePrefix)\\(\\s*$name\\b" +
                    "|\\b$name\\s*\\+" +
                    "|\\$\\{?$name\\}?(?![A-Za-z0-9_\"}])",
            )
        return usedAsPrefix.containsMatchIn(text)
    }

    private fun isComment(line: String, extension: String): Boolean {
        val trimmed = line.trimStart()
        return if (extension == "toml") {
            trimmed.startsWith("#")
        } else {
            trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
        }
    }

    fun suggestions(symbol: String, mappings: Map<String, Map<String, Int>>): List<String> {
        val table = symbol.substringBefore('.')
        val key = symbol.substringAfter('.')

        val otherTables =
            mappings.filterKeys { it != table }
                .mapNotNull { (other, entries) -> "$other.$key".takeIf { it in entries } }
        if (otherTables.isNotEmpty()) return otherTables.take(MAX_SUGGESTIONS)

        val maxDistance = (key.length / 3).coerceIn(1, 3)
        return mappings[table].orEmpty().keys
            .asSequence()
            .map { it.substringAfter('.') }
            .filter { kotlin.math.abs(it.length - key.length) <= maxDistance }
            .mapNotNull { candidate ->
                editDistance(key, candidate, maxDistance)?.let { candidate to it }
            }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .take(MAX_SUGGESTIONS)
            .map { "$table.${it.first}" }
            .toList()
    }

    /** Levenshtein distance, or null once it is certain to exceed [limit]. */
    fun editDistance(a: String, b: String, limit: Int): Int? {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > limit) return null
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length].takeIf { it <= limit }
    }
}

fun main(args: Array<String>) {
    val warnOnly = "--warn-only" in args
    val root = args.firstOrNull { !it.startsWith("--") }?.let(::File) ?: File(System.getProperty("user.dir"))

    val report =
        try {
            GamevalLint.lint(root)
        } catch (e: IllegalArgumentException) {
            System.err.println("error: gamevals failed to load: ${e.message}")
            exitProcess(1)
        }

    fun location(ref: GamevalLint.Reference): String =
        "${ref.file.relativeTo(root).invariantSeparatorsPath}:${ref.line}:${ref.column}"

    report.errors.forEach { finding ->
        val hint =
            finding.suggestions.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = " (did you mean ", postfix = "?)") { "'$it'" }
                .orEmpty()
        println("${location(finding.reference)}: error: unknown gameval '${finding.reference.symbol}'$hint")
    }
    report.pending.forEach { finding ->
        println(
            "${location(finding.reference)}: warning: '${finding.reference.symbol}' is declared but has " +
                "no id yet; run buildCache",
        )
    }

    println(
        "Checked ${report.references} gameval references in ${report.scannedFiles} files: " +
            "${report.errors.size} unknown, ${report.pending.size} awaiting an id.",
    )
    if (report.errors.isNotEmpty() && !warnOnly) {
        exitProcess(1)
    }
}
