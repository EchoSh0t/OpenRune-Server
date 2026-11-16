package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.FACE_EAST
import org.rsmod.content.skills.agility.FACE_NORTH
import org.rsmod.content.skills.agility.FACE_NORTH_WEST
import org.rsmod.content.skills.agility.HANDHOLDS_LAST_REVERSE
import org.rsmod.content.skills.agility.HANDHOLDS_REVERSE
import org.rsmod.content.skills.agility.HURDLE
import org.rsmod.content.skills.agility.JUMP_DOWN_LAND
import org.rsmod.content.skills.agility.RUN_UP
import org.rsmod.content.skills.agility.WALL_JUMP
import org.rsmod.content.skills.agility.announceLap
import org.rsmod.content.skills.agility.climbTo
import org.rsmod.content.skills.agility.failsObstacle
import org.rsmod.content.skills.agility.fallTo
import org.rsmod.content.skills.agility.glide
import org.rsmod.content.skills.agility.lapComplete
import org.rsmod.content.skills.agility.leapTo
import org.rsmod.content.skills.agility.meetsLevel
import org.rsmod.content.skills.agility.passRooftopObstacle
import org.rsmod.content.skills.agility.rollMarkOfGrace
import org.rsmod.content.skills.agility.stepTo
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PollnivneachRooftopCourse @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    private var Player.laps by intVarp("varp.agility_laps_pollnivneach")

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_pollnivneach_basket") { basket() }
        onOpLoc1("loc.rooftops_pollnivneach_marketstall") { marketStall() }
        onOpLoc1("loc.rooftops_pollnivneach_hangingbanner") { banner() }
        onOpLoc1("loc.rooftops_pollnivneach_gap") { gap() }
        onOpLoc1("loc.rooftops_pollnivneach_tree") { firstTree() }
        onOpLoc1("loc.rooftops_pollnivneach_wallclimb") { roughWall() }
        onOpLoc1("loc.rooftops_pollnivneach_monkeybars_start") { monkeyBars() }
        onOpLoc1("loc.rooftops_pollnivneach_treetop") { secondTree() }
        onOpLoc1("loc.rooftops_pollnivneach_line") { dryingLine() }
    }

    private suspend fun ProtectedAccess.basket() {
        arriveDelay()
        if (!meetsLevel(LEVEL)) {
            return
        }
        climbTo(BASKET_TOP)
        passRooftopObstacle(0, 10.0)
    }

    private suspend fun ProtectedAccess.marketStall() {
        arriveDelay()
        stepTo(STALL_START)
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(STALL_FALL, FALL_DAMAGE)
            return
        }
        leapTo(STALL_TOP)
        delay(3)
        leapTo(STALL_LANDING)
        passRooftopObstacle(1, 45.0)
    }

    private suspend fun ProtectedAccess.banner() {
        arriveDelay()
        stepTo(BANNER_START)
        anim("seq.human_ropeswing_long")
        glide(5, 2, delay1 = 15, delay2 = 60, dir = FACE_EAST)
        delay(2)
        resetAnim()
        passRooftopObstacle(2, 65.0)
    }

    private suspend fun ProtectedAccess.gap() {
        arriveDelay()
        stepTo(GAP_START)
        anim(RUN_UP, delay = 15)
        delay(1)
        anim(HURDLE)
        glide(4, -1, delay1 = 8, delay2 = 50, dir = FACE_EAST)
        delay(2)
        passRooftopObstacle(3, 35.0)
    }

    private suspend fun ProtectedAccess.firstTree() {
        arriveDelay()
        stepTo(TREE_START)
        anim(WALL_JUMP)
        glide(0, 3, delay1 = 35, delay2 = 49, dir = FACE_NORTH)
        delay(2)
        anim(HANDHOLDS_REVERSE)
        glide(0, 1, delay1 = 34, delay2 = 42, dir = FACE_NORTH)
        delay(2)
        anim(HANDHOLDS_LAST_REVERSE)
        glide(-1, 2, delay1 = 34, delay2 = 52, dir = FACE_NORTH_WEST)
        delay(1)
        anim(JUMP_DOWN_LAND)
        delay(1)
        passRooftopObstacle(4, 75.0)
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        climbTo(WALL_TOP)
        passRooftopObstacle(5, 5.0)
    }

    private suspend fun ProtectedAccess.monkeyBars() {
        arriveDelay()
        stepTo(MONKEY_BARS_START)
        anim("seq.human_monkeybars_on")
        delay(1)
        repeat(MONKEY_BARS) {
            anim("seq.human_monkeybars_walk")
            glide(0, 1, delay1 = 0, delay2 = 52, dir = FACE_NORTH)
            delay(2)
        }
        anim("seq.human_monkeybars_off")
        passRooftopObstacle(6, 55.0)
    }

    private suspend fun ProtectedAccess.secondTree() {
        arriveDelay()
        stepTo(TREETOP_START)
        anim(HURDLE, delay = 10)
        glide(0, 2, delay1 = 35, delay2 = 49, dir = FACE_NORTH)
        delay(3)
        anim(HURDLE, delay = 10)
        glide(0, 3, delay1 = 34, delay2 = 42, dir = FACE_NORTH)
        delay(2)
        passRooftopObstacle(7, 60.0)
    }

    private suspend fun ProtectedAccess.dryingLine() {
        arriveDelay()
        stepTo(LINE_START)
        leapTo(LINE_GRIP)
        delay(4)
        leapTo(FINISH)
        passRooftopObstacle(8, 540.0)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        announceLap("Pollnivneach Rooftop", player.laps)
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS)
    }

    private companion object {
        const val LEVEL = 70
        const val SAFE_LEVEL = 85
        const val OBSTACLES = 9
        const val MONKEY_BARS = 7
        val FALL_DAMAGE = 2..6

        val BASKET_TOP = CoordGrid(3351, 2964, 1)
        val STALL_START = CoordGrid(3350, 2968, 1)
        val STALL_FALL = CoordGrid(3351, 2969, 0)
        val STALL_TOP = CoordGrid(3350, 2972, 1)
        val STALL_LANDING = CoordGrid(3352, 2973, 1)
        val BANNER_START = CoordGrid(3355, 2976, 1)
        val GAP_START = CoordGrid(3362, 2977, 1)
        val TREE_START = CoordGrid(3367, 2976, 1)
        val WALL_TOP = CoordGrid(3365, 2983, 2)
        val MONKEY_BARS_START = CoordGrid(3358, 2984, 2)
        val TREETOP_START = CoordGrid(3359, 2995, 2)
        val LINE_START = CoordGrid(3362, 3002, 2)
        val LINE_GRIP = CoordGrid(3364, 3000, 2)
        val FINISH = CoordGrid(3364, 2998, 0)

        val MARK_SPAWNS =
            listOf(
                CoordGrid(3349, 2967, 1),
                CoordGrid(3354, 2974, 1),
                CoordGrid(3362, 2979, 1),
                CoordGrid(3369, 2974, 1),
                CoordGrid(3365, 2985, 1),
                CoordGrid(3361, 2981, 2),
                CoordGrid(3362, 2994, 2),
                CoordGrid(3356, 3004, 2),
            )
    }
}
