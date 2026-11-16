package dev.openrune.gamevals

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GamevalLintTest {
    private val tables = setOf("npc", "obj", "seq", "component")

    private fun symbols(source: String, extension: String = "kt"): List<String> =
        GamevalLint.findReferences(File("Test.$extension"), source.lines(), tables).map { it.symbol }

    @Test
    fun `finds quoted symbols with their position`() {
        val refs =
            GamevalLint.findReferences(File("Test.kt"), listOf("""onOpNpc1("npc.bob") { }"""), tables)
        assertEquals(listOf("npc.bob"), refs.map { it.symbol })
        assertEquals(1 to 11, refs.single().line to refs.single().column)
    }

    @Test
    fun `ignores unknown prefixes templates and comments`() {
        val source =
            """
            val a = "whisperer.shot"
            val b = "obj.${'$'}{name}_logs"
            val c = "obj.logs_${'$'}type"
            // objbox("obj.commented")
             * See "npc.kdoc".
            val d = "obj.potato_tuna+sweetcorn"
            """.trimIndent()
        assertEquals(listOf("obj.potato_tuna+sweetcorn"), symbols(source))
    }

    @Test
    fun `skips literals that are only a symbol prefix`() {
        val source =
            """
            if (name.startsWith("obj.uncut_")) return
            val cone = "npc.duke_cone_" + index
            private const val END_LEVEL = "component.dom_end_level_ui"
            private const val ROUSE =
                "seq.rouse_0"
            private const val REST = "obj.cup_guthix_rest"
            private const val WHOLE = "obj.bones"
            fun bind() = onIfModalButton("${'$'}END_LEVEL:btn_claim")
            fun anim() = locAnim("${'$'}ROUSE${'$'}index")
            fun rest() = name.startsWith(REST)
            fun bones() = objbox("${'$'}WHOLE")
            """.trimIndent()
        assertEquals(listOf("obj.bones"), symbols(source))
    }

    @Test
    fun `toml comments use hash`() {
        val source =
            """
            # obj = "obj.commented"
            obj = "obj.graceful_hood"
            """.trimIndent()
        assertEquals(listOf("obj.graceful_hood"), symbols(source, extension = "toml"))
    }

    @Test
    fun `suggests near misses and the same key in another table`() {
        val mappings =
            mapOf(
                "obj" to mapOf("obj.bronze_pickaxe" to 1265, "obj.rune_essence" to 1436),
                "seq" to mapOf("seq.human_mining_bronze_pickaxe" to 625),
                "jingle" to mapOf("jingle.tuna" to 9),
            )
        assertEquals(
            listOf("seq.human_mining_bronze_pickaxe"),
            GamevalLint.suggestions("seq.human_mine_bronze_pickaxe", mappings),
        )
        assertEquals(listOf("jingle.tuna"), GamevalLint.suggestions("obj.tuna", mappings))
        assertEquals(emptyList<String>(), GamevalLint.suggestions("obj.dragon_scimitar", mappings))
    }

    @Test
    fun `edit distance stops once past the limit`() {
        assertEquals(3, GamevalLint.editDistance("runecraft", "runecrafting", limit = 3))
        assertNull(GamevalLint.editDistance("runecraft", "woodcutting", limit = 3))
    }
}
