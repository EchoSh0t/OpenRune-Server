package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.FACE_SOUTH
import org.rsmod.content.skills.agility.WALL_JUMP
import org.rsmod.content.skills.agility.WALL_JUMP_LAND
import org.rsmod.content.skills.agility.announceLap
import org.rsmod.content.skills.agility.balanceAlong
import org.rsmod.content.skills.agility.climbTo
import org.rsmod.content.skills.agility.lapComplete
import org.rsmod.content.skills.agility.leapTo
import org.rsmod.content.skills.agility.meetsLevel
import org.rsmod.content.skills.agility.passRooftopObstacle
import org.rsmod.content.skills.agility.rollMarkOfGrace
import org.rsmod.content.skills.agility.sidestepAlong
import org.rsmod.content.skills.agility.stepTo
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
        climbTo(ROUGH_WALL_TOP)
        passRooftopObstacle(0, 5.0)
    }

    private suspend fun ProtectedAccess.firstTightrope() {
        arriveDelay()
        stepTo(ROPE_1_START)
        balanceAlong(ROPE_1_END)
        forcedWalk(ROPE_1_EXIT, crossTiles = 1)
        passRooftopObstacle(1, 8.0)
    }

    private suspend fun ProtectedAccess.secondTightrope() {
        arriveDelay()
        stepTo(ROPE_2_START)
        balanceAlong(ROPE_2_END)
        forcedWalk(ROPE_2_EXIT, crossTiles = 1)
        passRooftopObstacle(2, 7.0)
    }

    private suspend fun ProtectedAccess.narrowWall() {
        arriveDelay()
        stepTo(NARROW_WALL_START)
        sidestepAlong(NARROW_WALL_PATH)
        passRooftopObstacle(3, 7.0)
    }

    private suspend fun ProtectedAccess.wall() {
        arriveDelay()
        stepTo(WALL_START)
        delay(1)
        anim(WALL_JUMP, delay = 20)
        exactMove(WALL_START, WALL_LEDGE, delay1 = 25, delay2 = 30, dir = FACE_SOUTH)
        delay(1)
        anim(WALL_JUMP_LAND)
        delay(1)
        exactMove(WALL_LEDGE, WALL_TOP, delay1 = 17, delay2 = 26, dir = FACE_SOUTH)
        delay(1)
        resetAnim()
        passRooftopObstacle(4, 10.0)
    }

    private suspend fun ProtectedAccess.gap() {
        arriveDelay()
        stepTo(GAP_START)
        delay(1)
        leapTo(GAP_LANDING)
        delay(1)
        resetAnim()
        passRooftopObstacle(5, 4.0)
    }

    private suspend fun ProtectedAccess.crate() {
        arriveDelay()
        stepTo(CRATE_START)
        delay(1)
        leapTo(CRATE_MIDAIR)
        delay(1)
        resetAnim()
        delay(1)
        leapTo(FINISH)
        passRooftopObstacle(6, 79.0)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        announceLap("Draynor Village Rooftop", player.laps)
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS)
    }

    private companion object {
        const val LEVEL = 1
        const val OBSTACLES = 7

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
