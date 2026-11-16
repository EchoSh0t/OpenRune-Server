package org.rsmod.content.other.websiteapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.droptable.toml.TomlChanceEntry
import org.rsmod.api.droptable.toml.TomlDropTableDef
import org.rsmod.api.droptable.toml.TomlGuaranteedEntry
import org.rsmod.api.droptable.toml.TomlSeparateRoll
import org.rsmod.api.droptable.toml.TomlWeightedEntry
import org.rsmod.api.droptable.toml.TomlWeightedSection

class DropRatesTest {
    @Test
    fun `main table entries use weight over the declared total`() {
        val def =
            table(
                main =
                    TomlWeightedSection(
                        total = 128,
                        entries =
                            listOf(
                                TomlWeightedEntry(weight = 8, obj = "obj.a"),
                                TomlWeightedEntry(weight = 1, obj = "obj.b"),
                                TomlWeightedEntry(weight = 119, nothing = true),
                            ),
                    )
            )
        val lines = DropRates.lines(def)
        assertEquals(listOf("obj.a", "obj.b"), lines.map { it.obj })
        assertEquals(8.0 / 128, lines[0].chance, EPSILON)
        assertEquals("1/16", lines[0].rarity)
        assertEquals("1/128", lines[1].rarity)
    }

    @Test
    fun `main total falls back to the sum of weights`() {
        val def =
            table(
                main =
                    TomlWeightedSection(
                        entries = listOf(TomlWeightedEntry(weight = 1, obj = "obj.a"), TomlWeightedEntry(weight = 3, obj = "obj.b"))
                    )
            )
        assertEquals(listOf(0.25, 0.75), DropRates.lines(def).map { it.chance })
    }

    @Test
    fun `separate rolls multiply the gate by the entry share`() {
        val roll =
            TomlSeparateRoll(
                numerator = 1,
                denominator = 50,
                entries = listOf(TomlWeightedEntry(weight = 1, obj = "obj.a"), TomlWeightedEntry(weight = 3, obj = "obj.b")),
            )
        val lines = DropRates.lines(table(preRollSeparateRolls = listOf(roll)))
        assertEquals(1.0 / 200, lines[0].chance, EPSILON)
        assertEquals("1/200", lines[0].rarity)
        assertEquals(3.0 / 200, lines[1].chance, EPSILON)
        assertEquals("1/67", lines[1].rarity)
    }

    @Test
    fun `guaranteed, pre-roll and tertiary entries`() {
        val def =
            table(
                guaranteed = listOf(TomlGuaranteedEntry(obj = "obj.bones")),
                preRoll = listOf(TomlChanceEntry(numerator = 1, denominator = 5000, obj = "obj.pet")),
                tertiary = listOf(TomlChanceEntry(numerator = 3, denominator = 128, obj = "obj.clue")),
            )
        val lines = DropRates.lines(def)
        assertEquals(listOf("Always", "1/5000", "1/43"), lines.map { it.rarity })
        assertEquals(listOf(DropRates.GUARANTEED, DropRates.PRE_ROLL, DropRates.TERTIARY), lines.map { it.section })
    }

    @Test
    fun `small denominators keep one decimal`() {
        assertEquals("1/2.5", DropRates.rarity(0.4))
        assertEquals("1/4", DropRates.rarity(0.25))
        assertEquals("Always", DropRates.rarity(1.0))
        assertEquals("Never", DropRates.rarity(0.0))
    }

    @Test
    fun `quantity formats ranges`() {
        assertEquals("5–10", DropRates.quantity("5-10", null, null))
        assertEquals("3–7", DropRates.quantity(null, 3, 7))
        assertEquals("4", DropRates.quantity(null, 4, 4))
        assertEquals("1", DropRates.quantity(null, null, null))
    }

    @Test
    fun `shared tables are kept and empty entries skipped`() {
        val def =
            table(
                main =
                    TomlWeightedSection(
                        total = 10,
                        entries = listOf(TomlWeightedEntry(weight = 1, shared = "rareDrop"), TomlWeightedEntry(weight = 9)),
                    )
            )
        val lines = DropRates.lines(def)
        assertEquals(1, lines.size)
        assertEquals("rareDrop", lines[0].shared)
        assertEquals("1/10", lines[0].rarity)
    }

    private fun table(
        guaranteed: List<TomlGuaranteedEntry> = emptyList(),
        preRoll: List<TomlChanceEntry> = emptyList(),
        preRollSeparateRolls: List<TomlSeparateRoll> = emptyList(),
        main: TomlWeightedSection? = null,
        tertiary: List<TomlChanceEntry> = emptyList(),
    ) =
        TomlDropTableDef(
            id = "test",
            guaranteed = guaranteed,
            preRoll = preRoll,
            preRollSeparateRolls = preRollSeparateRolls,
            main = main,
            tertiary = tertiary,
        )

    private companion object {
        const val EPSILON = 1e-9
    }
}
