package org.rsmod.content.skills.agility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarksOfGraceTest {
    private fun successes(rate: MarkRate, baseLevel: Int, courseLevel: Int): Int =
        (0 until rate.denominator * 5).count { rollsMark(rate, baseLevel, courseLevel, it) }

    @Test
    fun `standard rate is one in three`() {
        assertEquals(10, successes(STANDARD_MARK_RATE, baseLevel = 30, courseLevel = 20))
    }

    @Test
    fun `twenty levels over the course cuts the rate to a fifth`() {
        assertEquals(10, successes(STANDARD_MARK_RATE, baseLevel = 39, courseLevel = 20))
        assertEquals(2, successes(STANDARD_MARK_RATE, baseLevel = 40, courseLevel = 20))
    }

    @Test
    fun `level one courses are penalised from level twenty one`() {
        assertEquals(10, successes(STANDARD_MARK_RATE, baseLevel = 20, courseLevel = 1))
        assertEquals(2, successes(STANDARD_MARK_RATE, baseLevel = 21, courseLevel = 1))
    }

    @Test
    fun `courses that ignore over levelling keep their full rate`() {
        val canifis = MarkRate(2, 3, ignoresOverLevel = true)
        assertEquals(10, successes(canifis, baseLevel = 99, courseLevel = 40))
    }
}
