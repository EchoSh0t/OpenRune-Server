package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.FACE_EAST
import org.rsmod.content.skills.agility.FACE_NORTH
import org.rsmod.content.skills.agility.FACE_NORTH_WEST
import org.rsmod.content.skills.agility.HANDHOLDS_LAST_REVERSE
import org.rsmod.content.skills.agility.HANDHOLDS_REVERSE
import org.rsmod.content.skills.agility.JUMP_DOWN
import org.rsmod.content.skills.agility.JUMP_DOWN_LAND
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
import org.rsmod.content.skills.agility.stepTo
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AlKharidRooftopCourse @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    private var Player.laps by intVarp("varp.agility_laps_kharid")

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_kharid_wallclimb") { roughWall() }
        onOpLoc1("loc.rooftops_kharid_tightrope_1") { firstTightrope() }
        onOpLoc1("loc.rooftops_kharid_rope_swing") { cable() }
        onOpLoc1("loc.rooftops_kharid_slide_side") { zipLine() }
        onOpLoc1("loc.rooftops_kharid_bamboo_tree_top") { tropicalTree() }
        onOpLoc1("loc.rooftops_kharid_wallclimb_2") { roofBeams() }
        onOpLoc1("loc.rooftops_kharid_tightrope_4") { secondTightrope() }
        onOpLoc1("loc.rooftops_kharid_leapdown") { gap() }
    }

    private suspend fun ProtectedAccess.roughWall() {
        arriveDelay()
        if (!meetsLevel(LEVEL)) {
            return
        }
        climbTo(ROUGH_WALL_TOP)
        passRooftopObstacle(0, 12.0)
    }

    private suspend fun ProtectedAccess.firstTightrope() {
        arriveDelay()
        stepTo(ROPE_1_START)
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(ROPE_1_FALL, FALL_DAMAGE)
            return
        }
        balanceAlong(ROPE_1_END)
        passRooftopObstacle(1, 36.0)
    }

    private suspend fun ProtectedAccess.cable() {
        arriveDelay()
        stepTo(CABLE_START)
        mes("You begin an almighty run-up...")
        anim(RUN_UP)
        glide(3, 0, delay1 = 0, delay2 = 30, dir = FACE_EAST)
        delay(1)
        mes("You gained enough momentum to swing to the other side!")
        anim("seq.human_ropeswing_long")
        glide(15, 0, delay1 = 0, delay2 = 60, dir = FACE_EAST)
        delay(2)
        passRooftopObstacle(2, 48.0)
    }

    private suspend fun ProtectedAccess.zipLine() {
        arriveDelay()
        stepTo(ZIP_LINE_START)
        anim(JUMP_DOWN, delay = 10)
        delay(1)
        telejump(ZIP_LINE_GRIP, TeleportType.Exempt)
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(ZIP_LINE_FALL, FALL_DAMAGE)
            return
        }
        anim("seq.zipline_bite")
        delay(1)
        anim("seq.zipline_slide")
        glide(12, 0, delay1 = 0, delay2 = 360, dir = FACE_EAST)
        delay(13)
        resetAnim()
        passRooftopObstacle(3, 48.0)
    }

    private suspend fun ProtectedAccess.tropicalTree() {
        arriveDelay()
        stepTo(TREE_START)
        anim(WALL_JUMP)
        glide(0, 4, delay1 = 35, delay2 = 49, dir = FACE_NORTH)
        delay(2)
        anim(HANDHOLDS_REVERSE)
        glide(0, 1, delay1 = 34, delay2 = 42, dir = FACE_NORTH)
        delay(2)
        anim(HANDHOLDS_LAST_REVERSE)
        glide(-1, 4, delay1 = 34, delay2 = 52, dir = FACE_NORTH_WEST)
        delay(1)
        anim(JUMP_DOWN_LAND)
        delay(1)
        telejump(TREE_LANDING, TeleportType.Exempt)
        passRooftopObstacle(4, 12.0)
    }

    private suspend fun ProtectedAccess.roofBeams() {
        arriveDelay()
        climbTo(BEAMS_TOP)
        passRooftopObstacle(5, 6.0)
    }

    private suspend fun ProtectedAccess.secondTightrope() {
        arriveDelay()
        stepTo(ROPE_2_START)
        balanceAlong(listOf(ROPE_2_END, ROPE_2_EXIT))
        passRooftopObstacle(6, 18.0)
    }

    private suspend fun ProtectedAccess.gap() {
        arriveDelay()
        leapTo(FINISH)
        delay(1)
        resetAnim()
        passRooftopObstacle(7, 36.0)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        announceLap("Al Kharid Rooftop", player.laps)
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS)
    }

    private companion object {
        const val LEVEL = 20
        const val SAFE_LEVEL = 40
        const val OBSTACLES = 8
        val FALL_DAMAGE = 1..5

        val ROUGH_WALL_TOP = CoordGrid(3273, 3192, 3)
        val ROPE_1_START = CoordGrid(3272, 3181, 3)
        val ROPE_1_END = CoordGrid(3272, 3172, 3)
        val ROPE_1_FALL = CoordGrid(3272, 3177, 0)
        val CABLE_START = CoordGrid(3266, 3166, 3)
        val ZIP_LINE_START = CoordGrid(3301, 3163, 3)
        val ZIP_LINE_GRIP = CoordGrid(3303, 3163, 1)
        val ZIP_LINE_FALL = CoordGrid(3308, 3163, 0)
        val TREE_START = CoordGrid(3318, 3165, 1)
        val TREE_LANDING = CoordGrid(3317, 3174, 2)
        val BEAMS_TOP = CoordGrid(3316, 3180, 3)
        val ROPE_2_START = CoordGrid(3313, 3186, 3)
        val ROPE_2_END = CoordGrid(3302, 3186, 3)
        val ROPE_2_EXIT = CoordGrid(3302, 3187, 3)
        val FINISH = CoordGrid(3299, 3194, 0)

        val MARK_SPAWNS =
            listOf(
                CoordGrid(3276, 3188, 3),
                CoordGrid(3271, 3191, 3),
                CoordGrid(3273, 3182, 3),
                CoordGrid(3267, 3171, 3),
                CoordGrid(3271, 3170, 3),
                CoordGrid(3268, 3163, 3),
                CoordGrid(3266, 3166, 3),
                CoordGrid(3291, 3163, 3),
                CoordGrid(3297, 3168, 3),
                CoordGrid(3301, 3164, 3),
                CoordGrid(3316, 3161, 1),
                CoordGrid(3318, 3163, 1),
                CoordGrid(3315, 3176, 2),
                CoordGrid(3317, 3178, 2),
                CoordGrid(3315, 3183, 3),
                CoordGrid(3313, 3181, 3),
                CoordGrid(3302, 3189, 3),
                CoordGrid(3300, 3190, 3),
            )
    }
}
