package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.CLIMB_WALL
import org.rsmod.content.skills.agility.FACE_EAST
import org.rsmod.content.skills.agility.JUMP_DOWN_LAND
import org.rsmod.content.skills.agility.MarkRate
import org.rsmod.content.skills.agility.SPOT_JUMP
import org.rsmod.content.skills.agility.WALL_JUMP
import org.rsmod.content.skills.agility.announceLap
import org.rsmod.content.skills.agility.balanceAlong
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

class ArdougneRooftopCourse @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    private var Player.laps by intVarp("varp.agility_laps_ardougne")

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_ardy_wallclimb") { woodenBeams() }
        onOpLoc1("loc.rooftops_ardy_jump") { firstGap() }
        onOpLoc1("loc.rooftops_ardy_plank") { plank() }
        onOpLoc1("loc.rooftops_ardy_jump_2") { secondGap() }
        onOpLoc1("loc.rooftops_ardy_jump_3") { thirdGap() }
        onOpLoc1("loc.rooftops_ardy_wallcrossing") { steepRoof() }
        onOpLoc1("loc.rooftops_ardy_jump_4") { lastGap() }
    }

    private suspend fun ProtectedAccess.woodenBeams() {
        arriveDelay()
        if (!meetsLevel(LEVEL)) {
            return
        }
        anim(CLIMB_WALL, delay = 15)
        for (step in BEAMS_CLIMB) {
            delay(1)
            telejump(step, TeleportType.Exempt)
            anim(CLIMB_WALL)
        }
        delay(1)
        telejump(BEAMS_TOP, TeleportType.Exempt)
        anim(JUMP_DOWN_LAND)
        delay(1)
        passRooftopObstacle(0, 43.0)
    }

    private suspend fun ProtectedAccess.firstGap() {
        arriveDelay()
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(FIRST_GAP_FALL, FALL_DAMAGE)
            return
        }
        leapTo(FIRST_GAP_HOP_1)
        delay(1)
        leapTo(FIRST_GAP_HOP_2)
        delay(1)
        leapTo(FIRST_GAP_LANDING, seq = WALL_JUMP)
        delay(1)
        passRooftopObstacle(1, 65.0)
    }

    private suspend fun ProtectedAccess.plank() {
        arriveDelay()
        stepTo(PLANK_START)
        balanceAlong(PLANK_END)
        delay(1)
        passRooftopObstacle(2, 50.0)
    }

    private suspend fun ProtectedAccess.secondGap() {
        arriveDelay()
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(SECOND_GAP_FALL, FALL_DAMAGE)
            return
        }
        leapTo(SECOND_GAP_LANDING)
        delay(1)
        passRooftopObstacle(3, 21.0)
    }

    private suspend fun ProtectedAccess.thirdGap() {
        arriveDelay()
        leapTo(THIRD_GAP_LANDING, seq = "seq.agility_shortcut_wall_jumpdown_noreachforward")
        delay(1)
        passRooftopObstacle(4, 28.0)
    }

    private suspend fun ProtectedAccess.steepRoof() {
        arriveDelay()
        sidestepAlong(STEEP_ROOF_PATH)
        delay(1)
        passRooftopObstacle(5, 57.0)
    }

    private suspend fun ProtectedAccess.lastGap() {
        arriveDelay()
        leapTo(LAST_GAP_LANDING)
        delay(2)
        forcedWalk(LAST_GAP_RUN_1, crossTiles = 3)
        delay(1)
        anim(SPOT_JUMP)
        glide(2, -1, delay1 = 15, delay2 = 30, dir = FACE_EAST)
        delay(1)
        forcedWalk(LAST_GAP_RUN_2, crossTiles = 3)
        delay(1)
        anim(SPOT_JUMP)
        glide(1, 0, delay1 = 15, delay2 = 30, dir = FACE_EAST)
        delay(1)
        leapTo(FINISH)
        passRooftopObstacle(6, 625.0)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        announceLap("Ardougne Rooftop", player.laps)
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS, MARK_RATE)
    }

    private companion object {
        const val LEVEL = 90
        const val SAFE_LEVEL = 95
        const val OBSTACLES = 7
        val FALL_DAMAGE = 2..6
        val MARK_RATE = MarkRate(2, 3)

        val BEAMS_CLIMB = listOf(CoordGrid(2673, 3298, 1), CoordGrid(2673, 3298, 2))
        val BEAMS_TOP = CoordGrid(2671, 3299, 3)
        val FIRST_GAP_FALL = CoordGrid(2668, 3310, 0)
        val FIRST_GAP_HOP_1 = CoordGrid(2667, 3311, 1)
        val FIRST_GAP_HOP_2 = CoordGrid(2665, 3315, 1)
        val FIRST_GAP_LANDING = CoordGrid(2665, 3318, 3)
        val PLANK_START = CoordGrid(2661, 3318, 3)
        val PLANK_END = CoordGrid(2656, 3318, 3)
        val SECOND_GAP_FALL = CoordGrid(2653, 3316, 0)
        val SECOND_GAP_LANDING = CoordGrid(2653, 3314, 3)
        val THIRD_GAP_LANDING = CoordGrid(2651, 3309, 3)
        val STEEP_ROOF_PATH = listOf(CoordGrid(2654, 3299, 3), CoordGrid(2656, 3297, 3))
        val LAST_GAP_LANDING = CoordGrid(2658, 3298, 1)
        val LAST_GAP_RUN_1 = CoordGrid(2661, 3298, 1)
        val LAST_GAP_RUN_2 = CoordGrid(2666, 3297, 1)
        val FINISH = CoordGrid(2668, 3297, 0)

        val MARK_SPAWNS = listOf(CoordGrid(2663, 3318, 3))
    }
}
