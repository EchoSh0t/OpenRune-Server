package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.FACE_EAST
import org.rsmod.content.skills.agility.FACE_NORTH
import org.rsmod.content.skills.agility.FACE_SOUTH
import org.rsmod.content.skills.agility.FACE_WEST
import org.rsmod.content.skills.agility.HANDHOLDS
import org.rsmod.content.skills.agility.HANDHOLDS_LAST
import org.rsmod.content.skills.agility.HURDLE
import org.rsmod.content.skills.agility.SPOT_JUMP
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
import org.rsmod.content.skills.agility.stepTo
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class FaladorRooftopCourse @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    private var Player.laps by intVarp("varp.agility_laps_falador")

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_falador_wallclimb") { roughWall() }
        onOpLoc1("loc.rooftops_falador_tightrope_1") { firstTightrope() }
        onOpLoc1("loc.rooftops_falador_handholds_start") { handHolds() }
        onOpLoc1("loc.rooftops_falador_gap_1") { firstGap() }
        onOpLoc1("loc.rooftops_falador_gap_2") { secondGap() }
        onOpLoc1("loc.rooftops_falador_tightrope_2") { secondTightrope() }
        onOpLoc1("loc.rooftops_falador_tightrope_3") { thirdTightrope() }
        onOpLoc1("loc.rooftops_falador_gap_3") { thirdGap() }
        onOpLoc1("loc.rooftops_falador_ledge_1") { ledge(8, -2, 0, FACE_WEST, 14.0) }
        onOpLoc1("loc.rooftops_falador_ledge_2") { ledge(9, 0, -2, FACE_SOUTH, 13.0) }
        onOpLoc1("loc.rooftops_falador_ledge_3a") { ledge(10, 0, -2, FACE_SOUTH, 13.0) }
        onOpLoc1("loc.rooftops_falador_ledge_4") { ledge(11, 2, 0, FACE_EAST, 14.0) }
        onOpLoc1("loc.rooftops_falador_edge") { edge() }
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsLevel(LEVEL)) {
            return
        }
        climbTo(ROUGH_WALL_TOP)
        passRooftopObstacle(0, 11.0)
    }

    private suspend fun ProtectedAccess.firstTightrope() {
        arriveDelay()
        stepTo(ROPE_1_START)
        balanceAlong(ROPE_1_END)
        passRooftopObstacle(1, 22.0)
    }

    private suspend fun ProtectedAccess.handHolds() {
        arriveDelay()
        stepTo(HANDHOLDS_START)
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(HANDHOLDS_FALL, FALL_DAMAGE)
            return
        }
        anim(WALL_JUMP, delay = 20)
        glide(0, 2, delay1 = 25, delay2 = 30, dir = FACE_NORTH)
        delay(1)
        telejump(HANDHOLDS_GRIP, TeleportType.Exempt)
        anim(HANDHOLDS)
        glide(1, 1, delay1 = 13, delay2 = 22, dir = FACE_WEST)
        delay(1)
        repeat(HANDHOLDS_CLIMBS) {
            anim(HANDHOLDS)
            glide(0, 1, delay1 = 34, delay2 = 52, dir = FACE_WEST)
            delay(2)
        }
        anim(HANDHOLDS_LAST)
        glide(-1, 2, delay1 = 34, delay2 = 60, dir = FACE_WEST)
        delay(2)
        resetAnim()
        telejump(HANDHOLDS_TOP, TeleportType.Exempt)
        passRooftopObstacle(2, 61.0)
    }

    private suspend fun ProtectedAccess.firstGap() {
        arriveDelay()
        stepTo(GAP_1_START)
        anim(SPOT_JUMP)
        glide(0, 3, delay1 = 15, delay2 = 30, dir = FACE_NORTH)
        delay(1)
        passRooftopObstacle(3, 27.0)
    }

    private suspend fun ProtectedAccess.secondGap() {
        arriveDelay()
        stepTo(GAP_2_START)
        anim(SPOT_JUMP)
        glide(-4, 0, delay1 = 15, delay2 = 30, dir = FACE_WEST)
        delay(1)
        passRooftopObstacle(4, 26.0)
    }

    private suspend fun ProtectedAccess.secondTightrope() {
        arriveDelay()
        stepTo(ROPE_2_START)
        balanceAlong(ROPE_2_PATH)
        delay(1)
        passRooftopObstacle(5, 61.0)
    }

    private suspend fun ProtectedAccess.thirdTightrope() {
        arriveDelay()
        stepTo(ROPE_3_START)
        delay(1)
        anim("seq.human_walk_logbalance_loop")
        glide(-5, 0, delay1 = 0, delay2 = 89, dir = FACE_WEST)
        delay(3)
        resetAnim()
        forcedWalk(ROPE_3_EXIT, crossTiles = 1)
        passRooftopObstacle(6, 53.0)
    }

    private suspend fun ProtectedAccess.thirdGap() {
        arriveDelay()
        stepTo(GAP_3_START)
        anim(HURDLE)
        glide(0, -4, delay1 = 15, delay2 = 30, dir = FACE_SOUTH)
        delay(1)
        passRooftopObstacle(7, 30.0)
    }

    private suspend fun ProtectedAccess.ledge(index: Int, dx: Int, dz: Int, dir: Int, xp: Double) {
        arriveDelay()
        anim(HURDLE)
        glide(dx, dz, delay1 = 15, delay2 = 30, dir = dir)
        delay(1)
        resetAnim()
        passRooftopObstacle(index, xp)
    }

    private suspend fun ProtectedAccess.edge() {
        arriveDelay()
        anim(HURDLE)
        glide(3, 0, delay1 = 15, delay2 = 30, dir = FACE_EAST)
        delay(1)
        resetAnim()
        delay(1)
        leapTo(FINISH)
        passRooftopObstacle(12, 241.0)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        announceLap("Falador Rooftop", player.laps)
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS)
    }

    private companion object {
        const val LEVEL = 50
        const val SAFE_LEVEL = 66
        const val OBSTACLES = 13
        const val HANDHOLDS_CLIMBS = 3
        val FALL_DAMAGE = 3..8

        val ROUGH_WALL_TOP = CoordGrid(3036, 3342, 3)
        val ROPE_1_START = CoordGrid(3040, 3343, 3)
        val ROPE_1_END = CoordGrid(3047, 3343, 3)
        val HANDHOLDS_START = CoordGrid(3050, 3349, 3)
        val HANDHOLDS_FALL = CoordGrid(3052, 3352, 0)
        val HANDHOLDS_GRIP = CoordGrid(3050, 3351, 2)
        val HANDHOLDS_TOP = CoordGrid(3050, 3357, 3)
        val GAP_1_START = CoordGrid(3048, 3358, 3)
        val GAP_2_START = CoordGrid(3045, 3361, 3)
        val ROPE_2_START = CoordGrid(3034, 3361, 3)
        val ROPE_2_PATH =
            listOf(CoordGrid(3033, 3361, 3), CoordGrid(3028, 3356, 3), CoordGrid(3028, 3354, 3))
        val ROPE_3_START = CoordGrid(3026, 3353, 3)
        val ROPE_3_EXIT = CoordGrid(3020, 3353, 3)
        val GAP_3_START = CoordGrid(3016, 3353, 3)
        val FINISH = CoordGrid(3029, 3333, 0)

        val MARK_SPAWNS =
            listOf(
                CoordGrid(3038, 3343, 3),
                CoordGrid(3049, 3348, 3),
                CoordGrid(3049, 3357, 3),
                CoordGrid(3045, 3365, 3),
                CoordGrid(3035, 3362, 3),
                CoordGrid(3028, 3353, 3),
                CoordGrid(3017, 3345, 3),
                CoordGrid(3011, 3339, 3),
                CoordGrid(3016, 3333, 3),
            )
    }
}
