package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.FACE_NORTH
import org.rsmod.content.skills.agility.FACE_SOUTH
import org.rsmod.content.skills.agility.HURDLE
import org.rsmod.content.skills.agility.MarkRate
import org.rsmod.content.skills.agility.RUN_UP
import org.rsmod.content.skills.agility.WALL_JUMP
import org.rsmod.content.skills.agility.announceLap
import org.rsmod.content.skills.agility.balanceAlong
import org.rsmod.content.skills.agility.climbTo
import org.rsmod.content.skills.agility.failsObstacle
import org.rsmod.content.skills.agility.fallTo
import org.rsmod.content.skills.agility.glide
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

class RellekkaRooftopCourse @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    private var Player.laps by intVarp("varp.agility_laps_rellekka")

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_rellekka_wallclimb") { roughWall() }
        onOpLoc1("loc.rooftops_rellekka_gap_1") { firstGap() }
        onOpLoc1("loc.rooftops_rellekka_tightrope_1") { firstTightrope() }
        onOpLoc1("loc.rooftops_rellekka_gap_2") { secondGap() }
        onOpLoc1("loc.rooftops_rellekka_gap_3") { thirdGap() }
        onOpLoc1("loc.rooftops_rellekka_tightrope_3") { secondTightrope() }
        onOpLoc1("loc.rooftops_rellekka_dropoff") { pileOfFish() }
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsLevel(LEVEL)) {
            return
        }
        climbTo(ROUGH_WALL_TOP)
        passRooftopObstacle(0, 20.0)
    }

    private suspend fun ProtectedAccess.firstGap() {
        arriveDelay()
        anim(RUN_UP, delay = 15)
        delay(1)
        anim(HURDLE)
        glide(-1, -4, delay1 = 8, delay2 = 50, dir = FACE_SOUTH)
        delay(2)
        passRooftopObstacle(1, 30.0)
    }

    private suspend fun ProtectedAccess.firstTightrope() {
        arriveDelay()
        stepTo(ROPE_1_START)
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(ROPE_1_FALL, FALL_DAMAGE)
            return
        }
        balanceAlong(ROPE_1_PATH)
        delay(1)
        passRooftopObstacle(2, 40.0)
    }

    private suspend fun ProtectedAccess.secondGap() {
        arriveDelay()
        stepTo(SECOND_GAP_START)
        anim(WALL_JUMP)
        glide(0, 3, delay1 = 25, delay2 = 30, dir = FACE_NORTH)
        delay(1)
        sidestepAlong(SECOND_GAP_LEDGE_END)
        balanceAlong(SECOND_GAP_ROPE)
        passRooftopObstacle(3, 85.0)
    }

    private suspend fun ProtectedAccess.thirdGap() {
        arriveDelay()
        stepTo(THIRD_GAP_START)
        anim(RUN_UP, delay = 15)
        delay(1)
        anim(HURDLE)
        glide(0, 4, delay1 = 8, delay2 = 50, dir = FACE_NORTH)
        delay(2)
        passRooftopObstacle(4, 25.0)
    }

    private suspend fun ProtectedAccess.secondTightrope() {
        arriveDelay()
        stepTo(ROPE_2_START)
        balanceAlong(ROPE_2_PATH)
        delay(1)
        passRooftopObstacle(5, 105.0)
    }

    private suspend fun ProtectedAccess.pileOfFish() {
        arriveDelay()
        leapTo(FINISH)
        delay(1)
        forcedWalk(FINISH_EXIT, crossTiles = 1)
        passRooftopObstacle(6, 475.0)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        announceLap("Rellekka Rooftop", player.laps)
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS, MARK_RATE)
    }

    private companion object {
        const val LEVEL = 80
        const val SAFE_LEVEL = 85
        const val OBSTACLES = 7
        val FALL_DAMAGE = 2..6
        val MARK_RATE = MarkRate(2, 5)

        val ROUGH_WALL_TOP = CoordGrid(2626, 3676, 3)
        val ROPE_1_START = CoordGrid(2623, 3658, 3)
        val ROPE_1_PATH =
            listOf(CoordGrid(2626, 3655, 3), CoordGrid(2626, 3654, 3), CoordGrid(2627, 3654, 3))
        val ROPE_1_FALL = CoordGrid(2624, 3656, 0)
        val SECOND_GAP_START = CoordGrid(2627, 3654, 3)
        val SECOND_GAP_LEDGE_END = CoordGrid(2635, 3658, 3)
        val SECOND_GAP_ROPE = listOf(CoordGrid(2639, 3654, 3), CoordGrid(2639, 3653, 3))
        val THIRD_GAP_START = CoordGrid(2643, 3653, 3)
        val ROPE_2_START = CoordGrid(2647, 3663, 3)
        val ROPE_2_PATH = listOf(CoordGrid(2654, 3670, 3), CoordGrid(2655, 3670, 3))
        val FINISH = CoordGrid(2653, 3676, 0)
        val FINISH_EXIT = CoordGrid(2652, 3676, 0)

        val MARK_SPAWNS =
            listOf(
                CoordGrid(2622, 3676, 3),
                CoordGrid(2617, 3664, 3),
                CoordGrid(2618, 3660, 3),
                CoordGrid(2628, 3652, 3),
                CoordGrid(2628, 3655, 3),
                CoordGrid(2641, 3649, 3),
                CoordGrid(2643, 3651, 3),
                CoordGrid(2649, 3659, 3),
                CoordGrid(2644, 3662, 3),
                CoordGrid(2658, 3674, 3),
                CoordGrid(2656, 3681, 3),
            )
    }
}
