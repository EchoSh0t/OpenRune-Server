package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.FACE_EAST
import org.rsmod.content.skills.agility.FACE_NORTH
import org.rsmod.content.skills.agility.FACE_SOUTH
import org.rsmod.content.skills.agility.FACE_WEST
import org.rsmod.content.skills.agility.HANDHOLDS_REVERSE
import org.rsmod.content.skills.agility.HURDLE
import org.rsmod.content.skills.agility.RUN_UP
import org.rsmod.content.skills.agility.SPOT_JUMP
import org.rsmod.content.skills.agility.WALL_JUMP
import org.rsmod.content.skills.agility.WALL_JUMP_LAND
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
import org.rsmod.content.skills.agility.sidestepAlong
import org.rsmod.content.skills.agility.stepTo
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VarrockRooftopCourse @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    private var Player.laps by intVarp("varp.agility_laps_varrock")

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_varrock_wallclimb") { roughWall() }
        onOpLoc1("loc.rooftops_varrock_clothesline") { clothesLine() }
        onOpLoc1("loc.rooftops_varrock_leaptoruins") { firstGap() }
        onOpLoc1("loc.rooftops_varrock_wallswing") { wall() }
        onOpLoc1("loc.rooftops_varrock_wallscramble") { secondGap() }
        onOpLoc1("loc.rooftops_varrock_leaptobalcony") { thirdGap() }
        onOpLoc1("loc.rooftops_varrock_leapdown") { fourthGap() }
        onOpLoc1("loc.rooftops_varrock_stepuproof") { ledge() }
        onOpLoc1("loc.rooftops_varrock_finish") { edge() }
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsLevel(LEVEL)) {
            return
        }
        climbTo(ROUGH_WALL_TOP)
        anim(WALL_JUMP_LAND)
        delay(2)
        telejump(ROUGH_WALL_LANDING, TeleportType.Exempt)
        resetAnim()
        passRooftopObstacle(0, 13.5)
    }

    private suspend fun ProtectedAccess.clothesLine() {
        arriveDelay()
        stepTo(CLOTHES_LINE_START)
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(CLOTHES_LINE_FALL, CLOTHES_LINE_DAMAGE)
            return
        }
        repeat(CLOTHES_LINE_HOPS) { hop ->
            val middleHop = hop == 1
            anim(SPOT_JUMP, delay = if (middleHop) 15 else 0)
            glide(
                -2,
                0,
                delay1 = if (middleHop) 30 else 15,
                delay2 = if (middleHop) 45 else 30,
                dir = FACE_WEST,
            )
            delay(2)
        }
        passRooftopObstacle(1, 23.0)
    }

    private suspend fun ProtectedAccess.firstGap() {
        arriveDelay()
        leapTo(RUINS_LANDING)
        delay(1)
        passRooftopObstacle(2, 19.0)
    }

    private suspend fun ProtectedAccess.wall() {
        arriveDelay()
        stepTo(WALL_START)
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(WALL_FALL, WALL_DAMAGE)
            return
        }
        anim(RUN_UP, delay = 15)
        glide(-1, 0, delay1 = 15, delay2 = 45, dir = FACE_WEST)
        delay(1)
        anim(WALL_JUMP, delay = 30)
        glide(-3, -2, delay1 = 25, delay2 = 30, dir = FACE_WEST)
        delay(1)
        repeat(WALL_HANDHOLDS) {
            anim(HANDHOLDS_REVERSE)
            glide(0, -1, delay1 = 34, delay2 = 52, dir = FACE_WEST)
            delay(2)
        }
        sidestepAlong(WALL_LEDGE_END)
        anim(SPOT_JUMP)
        glide(2, -1, delay1 = 5, delay2 = 30, dir = FACE_EAST)
        delay(1)
        telejump(WALL_LANDING, TeleportType.Exempt)
        resetAnim()
        passRooftopObstacle(3, 28.0)
    }

    private suspend fun ProtectedAccess.secondGap() {
        arriveDelay()
        delay(1)
        anim(WALL_JUMP, delay = 10)
        glide(0, -3, delay1 = 25, delay2 = 30, dir = FACE_SOUTH)
        delay(1)
        anim(WALL_JUMP_LAND)
        glide(0, -1, delay1 = 17, delay2 = 26, dir = FACE_SOUTH)
        delay(1)
        resetAnim()
        passRooftopObstacle(4, 10.0)
    }

    private suspend fun ProtectedAccess.thirdGap() {
        arriveDelay()
        stepTo(BALCONY_START)
        anim("seq.seaslug_ken_leap", delay = 15)
        glide(7, 2, delay1 = 20, delay2 = 50, dir = FACE_EAST)
        delay(3)
        anim(WALL_JUMP)
        glide(2, 0, delay1 = 5, delay2 = 10, dir = FACE_EAST)
        delay(1)
        anim(WALL_JUMP_LAND)
        glide(1, 0, delay1 = 18, delay2 = 27, dir = FACE_EAST)
        delay(1)
        resetAnim()
        passRooftopObstacle(5, 24.5)
    }

    private suspend fun ProtectedAccess.fourthGap() {
        arriveDelay()
        leapTo(LEAP_DOWN_LANDING)
        delay(1)
        resetAnim()
        passRooftopObstacle(6, 4.5)
    }

    private suspend fun ProtectedAccess.ledge() {
        arriveDelay()
        stepTo(LEDGE_START)
        anim(HURDLE)
        glide(0, 2, delay1 = 8, delay2 = 50, dir = FACE_NORTH)
        delay(1)
        passRooftopObstacle(7, 3.5)
    }

    private suspend fun ProtectedAccess.edge() {
        arriveDelay()
        stepTo(EDGE_START)
        anim(SPOT_JUMP)
        glide(0, 1, delay1 = 15, delay2 = 30, dir = FACE_NORTH)
        delay(1)
        leapTo(FINISH)
        passRooftopObstacle(8, 143.7)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        announceLap("Varrock Rooftop", player.laps)
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS)
    }

    private companion object {
        const val LEVEL = 30
        const val SAFE_LEVEL = 50
        const val OBSTACLES = 9
        const val CLOTHES_LINE_HOPS = 3
        const val WALL_HANDHOLDS = 5
        val CLOTHES_LINE_DAMAGE = 3..8
        val WALL_DAMAGE = 2..5

        val ROUGH_WALL_TOP = CoordGrid(3220, 3414, 3)
        val ROUGH_WALL_LANDING = CoordGrid(3219, 3414, 3)
        val CLOTHES_LINE_START = CoordGrid(3214, 3414, 3)
        val CLOTHES_LINE_FALL = CoordGrid(3211, 3414, 0)
        val RUINS_LANDING = CoordGrid(3197, 3416, 1)
        val WALL_START = CoordGrid(3194, 3416, 1)
        val WALL_FALL = CoordGrid(3189, 3413, 0)
        val WALL_LEDGE_END = CoordGrid(3190, 3407, 1)
        val WALL_LANDING = CoordGrid(3192, 3406, 3)
        val BALCONY_START = CoordGrid(3208, 3397, 3)
        val LEAP_DOWN_LANDING = CoordGrid(3236, 3403, 3)
        val LEDGE_START = CoordGrid(3236, 3408, 3)
        val EDGE_START = CoordGrid(3236, 3415, 3)
        val FINISH = CoordGrid(3236, 3417, 0)

        val MARK_SPAWNS =
            listOf(
                CoordGrid(3214, 3417, 3),
                CoordGrid(3202, 3417, 3),
                CoordGrid(3194, 3416, 1),
                CoordGrid(3194, 3404, 3),
                CoordGrid(3196, 3394, 3),
                CoordGrid(3205, 3395, 3),
                CoordGrid(3226, 3402, 3),
                CoordGrid(3236, 3407, 3),
            )
    }
}
