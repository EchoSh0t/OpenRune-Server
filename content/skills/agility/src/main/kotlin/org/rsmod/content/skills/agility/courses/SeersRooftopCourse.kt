package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.CLIMB_WALL
import org.rsmod.content.skills.agility.FACE_SOUTH
import org.rsmod.content.skills.agility.HANDHOLDS
import org.rsmod.content.skills.agility.WALL_JUMP
import org.rsmod.content.skills.agility.WALL_JUMP_LAND
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
import org.rsmod.content.skills.agility.stepTo
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SeersRooftopCourse @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    private var Player.laps by intVarp("varp.agility_laps_seers")

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_seers_wallclimb") { wall() }
        onOpLoc1("loc.rooftops_seers_jump") { firstGap() }
        onOpLoc1("loc.rooftops_seers_tightrope") { tightrope() }
        onOpLoc1("loc.rooftops_seers_jump_1") { secondGap() }
        onOpLoc1("loc.rooftops_seers_jump_2") { thirdGap() }
        onOpLoc1("loc.rooftops_seers_leapdown") { edge() }
    }

    private suspend fun ProtectedAccess.wall() {
        arriveDelay()
        if (!meetsLevel(LEVEL)) {
            return
        }
        anim(CLIMB_WALL, delay = 15)
        delay(2)
        telejump(WALL_MIDWAY, TeleportType.Exempt)
        anim(HANDHOLDS)
        delay(2)
        telejump(WALL_TOP, TeleportType.Exempt)
        resetAnim()
        passRooftopObstacle(0, 45.0)
    }

    private suspend fun ProtectedAccess.firstGap() {
        arriveDelay()
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(FIRST_GAP_FALL, FALL_DAMAGE)
            return
        }
        leapTo(FIRST_GAP_MIDWAY)
        delay(1)
        leapTo(FIRST_GAP_LANDING)
        delay(1)
        passRooftopObstacle(1, 20.0)
    }

    private suspend fun ProtectedAccess.tightrope() {
        arriveDelay()
        stepTo(ROPE_START)
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(ROPE_FALL, FALL_DAMAGE)
            return
        }
        balanceAlong(ROPE_END)
        delay(1)
        passRooftopObstacle(2, 20.0)
    }

    private suspend fun ProtectedAccess.secondGap() {
        arriveDelay()
        stepTo(SECOND_GAP_START)
        anim(WALL_JUMP, delay = 20)
        glide(0, -3, delay1 = 25, delay2 = 30, dir = FACE_SOUTH)
        delay(1)
        anim(WALL_JUMP_LAND)
        delay(1)
        telejump(SECOND_GAP_LEDGE, TeleportType.Exempt)
        glide(0, -2, delay1 = 17, delay2 = 27, dir = FACE_SOUTH)
        delay(1)
        resetAnim()
        passRooftopObstacle(3, 35.0)
    }

    private suspend fun ProtectedAccess.thirdGap() {
        arriveDelay()
        leapTo(THIRD_GAP_LANDING)
        delay(1)
        resetAnim()
        passRooftopObstacle(4, 15.0)
    }

    private suspend fun ProtectedAccess.edge() {
        arriveDelay()
        leapTo(FINISH)
        delay(1)
        resetAnim()
        passRooftopObstacle(5, 435.0)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        announceLap("Seers' Village Rooftop", player.laps)
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS)
    }

    private companion object {
        const val LEVEL = 60
        const val SAFE_LEVEL = 79
        const val OBSTACLES = 6
        val FALL_DAMAGE = 2..6

        val WALL_MIDWAY = CoordGrid(2729, 3488, 1)
        val WALL_TOP = CoordGrid(2729, 3491, 3)
        val FIRST_GAP_FALL = CoordGrid(2718, 3494, 0)
        val FIRST_GAP_MIDWAY = CoordGrid(2719, 3495, 2)
        val FIRST_GAP_LANDING = CoordGrid(2713, 3494, 2)
        val ROPE_START = CoordGrid(2710, 3489, 2)
        val ROPE_END = CoordGrid(2710, 3481, 2)
        val ROPE_FALL = CoordGrid(2710, 3485, 0)
        val SECOND_GAP_START = CoordGrid(2710, 3477, 2)
        val SECOND_GAP_LEDGE = CoordGrid(2710, 3474, 3)
        val THIRD_GAP_LANDING = CoordGrid(2702, 3465, 2)
        val FINISH = CoordGrid(2704, 3464, 0)

        val MARK_SPAWNS =
            listOf(
                CoordGrid(2726, 3492, 3),
                CoordGrid(2728, 3495, 3),
                CoordGrid(2707, 3493, 2),
                CoordGrid(2708, 3489, 2),
                CoordGrid(2712, 3481, 2),
                CoordGrid(2710, 3478, 2),
                CoordGrid(2710, 3472, 3),
                CoordGrid(2702, 3474, 3),
                CoordGrid(2698, 3462, 2),
            )
    }
}
