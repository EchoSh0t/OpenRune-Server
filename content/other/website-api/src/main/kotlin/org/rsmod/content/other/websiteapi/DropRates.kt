package org.rsmod.content.other.websiteapi

import java.util.Locale
import kotlin.math.roundToLong
import org.rsmod.api.droptable.toml.TomlChanceEntry
import org.rsmod.api.droptable.toml.TomlDropTableDef
import org.rsmod.api.droptable.toml.TomlSeparateRoll
import org.rsmod.api.droptable.toml.TomlWeightedEntry

/** One line of a monster's drop table as shown on the website. */
data class DropLine(
    val obj: String?,
    val shared: String?,
    val quantity: String,
    val chance: Double,
    val rarity: String,
    val section: String,
)

/**
 * Flattens a drop table into per-item chances for a single kill. Each section rolls on its own:
 * guaranteed drops always land, the main table picks one entry by weight, separate rolls first
 * pass their own `numerator/denominator` gate and then pick by weight, and pre-roll and tertiary
 * entries are independent `numerator/denominator` chances.
 */
object DropRates {
    fun lines(def: TomlDropTableDef): List<DropLine> =
        buildList {
            for (entry in def.guaranteed) {
                add(line(entry.obj, null, quantity(entry.count, entry.countMin, entry.countMax), 1.0, GUARANTEED))
            }
            def.preRoll.forEach { add(chance(it, PRE_ROLL)) }
            def.preRollSeparateRolls.forEach { addAll(separate(it, PRE_ROLL)) }
            def.main?.let { main ->
                val total = main.total ?: main.entries.sumOf { it.weight }
                if (total > 0) {
                    main.entries.mapNotNullTo(this) { weighted(it, it.weight.toDouble() / total, MAIN) }
                }
                main.separateRolls.forEach { addAll(separate(it, MAIN)) }
            }
            def.tertiary.forEach { add(chance(it, TERTIARY)) }
        }

    /** "Always", or "1/N" with N rounded the way the OSRS wiki shows rarities. */
    fun rarity(chance: Double): String {
        if (chance >= 1.0) return "Always"
        if (chance <= 0.0) return "Never"
        val denominator = 1.0 / chance
        return when {
            denominator < 10 -> "1/${String.format(Locale.ROOT, "%.1f", denominator).removeSuffix(".0")}"
            else -> "1/${denominator.roundToLong()}"
        }
    }

    fun quantity(count: String?, min: Int?, max: Int?): String =
        when {
            !count.isNullOrBlank() -> count.replace("-", "–")
            min != null && max != null && min != max -> "$min–$max"
            min != null -> min.toString()
            else -> "1"
        }

    private fun separate(roll: TomlSeparateRoll, section: String): List<DropLine> {
        val total = roll.entries.sumOf { it.weight }
        if (total <= 0 || roll.denominator <= 0) return emptyList()
        val gate = roll.numerator.toDouble() / roll.denominator
        return roll.entries.mapNotNull { weighted(it, gate * it.weight / total, section) }
    }

    private fun weighted(entry: TomlWeightedEntry, chance: Double, section: String): DropLine? {
        if (entry.nothing || (entry.obj == null && entry.shared == null)) return null
        return line(entry.obj, entry.shared, quantity(entry.count, entry.countMin, entry.countMax), chance, section)
    }

    private fun chance(entry: TomlChanceEntry, section: String): DropLine {
        val chance = if (entry.denominator <= 0) 0.0 else entry.numerator.toDouble() / entry.denominator
        return line(entry.obj, null, quantity(entry.count, entry.countMin, entry.countMax), chance, section)
    }

    private fun line(obj: String?, shared: String?, quantity: String, chance: Double, section: String) =
        DropLine(obj, shared, quantity, chance.coerceAtMost(1.0), rarity(chance), section)

    const val GUARANTEED = "Always"
    const val PRE_ROLL = "Pre-roll"
    const val MAIN = "Main"
    const val TERTIARY = "Tertiary"
}
