package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.FACE_SOUTH
import org.rsmod.content.skills.agility.agilityXp
import org.rsmod.content.skills.agility.lapComplete
import org.rsmod.content.skills.agility.meetsLevel
import org.rsmod.content.skills.agility.progressLap
import org.rsmod.content.skills.agility.rollMarkOfGrace
import org.rsmod.content.skills.agility.stepTo
import org.rsmod.content.skills.agility.withMoveAnims
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorRooftopCourse @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    private var Player.laps by intVarp("varp.agility_laps_draynor")

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_draynor_wallclimb") { roughWall() }
        onOpLoc1("loc.rooftops_draynor_tightrope_1") { firstTightrope() }
        onOpLoc1("loc.rooftops_draynor_tightrope_2") { secondTightrope() }
        onOpLoc1("loc.rooftops_draynor_wallcrossing") { narrowWall() }
        onOpLoc1("loc.rooftops_draynor_wallscramble") { wall() }
        onOpLoc1("loc.rooftops_draynor_leapdown") { gap() }
        onOpLoc1("loc.rooftops_draynor_crate") { crate() }
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsLevel(LEVEL)) {
            return
        }
        delay(1)
        anim("seq.human_reachforladder", delay = 15)
        delay(2)
        telejump(ROUGH_WALL_TOP, TeleportType.Exempt)
        resetAnim()
        agilityXp(5.0)
        progressLap(0)
    }

    private suspend fun ProtectedAccess.firstTightrope() {
        arriveDelay()
        stepTo(ROPE_1_START)
        withMoveAnims(BALANCE_READY, BALANCE_WALK, BALANCE_WALK) { stepTo(ROPE_1_END) }
        forcedWalk(ROPE_1_EXIT, crossTiles = 1)
        agilityXp(8.0)
        progressLap(1)
    }

    private suspend fun ProtectedAccess.secondTightrope() {
        arriveDelay()
        stepTo(ROPE_2_START)
        withMoveAnims(BALANCE_READY, BALANCE_WALK, BALANCE_WALK) { stepTo(ROPE_2_END) }
        forcedWalk(ROPE_2_EXIT, crossTiles = 1)
        agilityXp(7.0)
        progressLap(2)
    }

    private suspend fun ProtectedAccess.narrowWall() {
        arriveDelay()
        stepTo(NARROW_WALL_START)
        withMoveAnims(SIDESTEP_READY, SIDESTEP_READY, SIDESTEP_WALK) {
            anim("seq.human_into_sidestepl")
            forcedWalk(NARROW_WALL_PATH, crossTiles = 4)
        }
        anim("seq.human_outof_sidestepl")
        agilityXp(7.0)
        progressLap(3)
    }

    private suspend fun ProtectedAccess.wall() {
        arriveDelay()
        stepTo(WALL_START)
        delay(1)
        anim("seq.agility_shortcut_wall_jump", delay = 20)
        exactMove(WALL_START, WALL_LEDGE, delay1 = 25, delay2 = 30, dir = FACE_SOUTH)
        delay(1)
        anim("seq.agility_shortcut_wall_jump2")
        delay(1)
        exactMove(WALL_LEDGE, WALL_TOP, delay1 = 17, delay2 = 26, dir = FACE_SOUTH)
        delay(1)
        resetAnim()
        agilityXp(10.0)
        progressLap(4)
    }

    private suspend fun ProtectedAccess.gap() {
        arriveDelay()
        stepTo(GAP_START)
        delay(1)
        jumpDown(GAP_LANDING)
        delay(1)
        resetAnim()
        agilityXp(4.0)
        progressLap(5)
    }

    private suspend fun ProtectedAccess.crate() {
        arriveDelay()
        stepTo(CRATE_START)
        delay(1)
        jumpDown(CRATE_MIDAIR)
        delay(1)
        resetAnim()
        delay(1)
        jumpDown(FINISH)
        agilityXp(79.0)
        progressLap(6)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        mes("Your Draynor Village Rooftop lap count is: <col=ff0000>${player.laps}</col>.")
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS)
    }

    private suspend fun ProtectedAccess.jumpDown(dest: CoordGrid) {
        anim("seq.agility_shortcut_wall_jumpdown", delay = 15)
        delay(1)
        telejump(dest, TeleportType.Exempt)
        anim("seq.agility_shortcut_wall_jumpdown2")
    }

    private companion object {
        const val LEVEL = 1
        const val OBSTACLES = 7
        const val BALANCE_READY = "seq.human_walk_logbalance_ready"
        const val BALANCE_WALK = "seq.human_walk_logbalance"
        const val SIDESTEP_READY = "seq.human_ready_sidestepl"
        const val SIDESTEP_WALK = "seq.human_walk_sidestepl"

        val ROUGH_WALL_TOP = CoordGrid(3102, 3279, 3)
        val ROPE_1_START = CoordGrid(3098, 3277, 3)
        val ROPE_1_END = CoordGrid(3090, 3277, 3)
        val ROPE_1_EXIT = CoordGrid(3090, 3276, 3)
        val ROPE_2_START = CoordGrid(3092, 3276, 3)
        val ROPE_2_END = CoordGrid(3092, 3267, 3)
        val ROPE_2_EXIT = CoordGrid(3092, 3266, 3)
        val NARROW_WALL_START = CoordGrid(3089, 3265, 3)
        val NARROW_WALL_PATH = listOf(CoordGrid(3089, 3262, 3), CoordGrid(3088, 3261, 3))
        val WALL_START = CoordGrid(3088, 3257, 3)
        val WALL_LEDGE = CoordGrid(3088, 3256, 3)
        val WALL_TOP = CoordGrid(3088, 3255, 3)
        val GAP_START = CoordGrid(3094, 3255, 3)
        val GAP_LANDING = CoordGrid(3096, 3256, 3)
        val CRATE_START = CoordGrid(3101, 3261, 3)
        val CRATE_MIDAIR = CoordGrid(3102, 3261, 1)
        val FINISH = CoordGrid(3103, 3261, 0)

        val MARK_SPAWNS =
            listOf(
                CoordGrid(3097, 3279, 3),
                CoordGrid(3089, 3274, 3),
                CoordGrid(3093, 3266, 3),
                CoordGrid(3098, 3258, 3),
            )
    }
}
