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
import org.rsmod.content.skills.agility.FACE_SOUTH_EAST
import org.rsmod.content.skills.agility.FACE_WEST
import org.rsmod.content.skills.agility.JUMP_DOWN
import org.rsmod.content.skills.agility.JUMP_DOWN_LAND
import org.rsmod.content.skills.agility.MarkRate
import org.rsmod.content.skills.agility.RUN_UP
import org.rsmod.content.skills.agility.WALL_JUMP
import org.rsmod.content.skills.agility.WALL_JUMP_LAND
import org.rsmod.content.skills.agility.announceLap
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

class CanifisRooftopCourse @Inject constructor(private val objRepo: ObjRepository) :
    PluginScript() {
    private var Player.laps by intVarp("varp.agility_laps_canifis")

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_canifis_start_tree") { tallTree() }
        onOpLoc1("loc.rooftops_canifis_jump") { firstGap() }
        onOpLoc1("loc.rooftops_canifis_jump_2") { secondGap() }
        onOpLoc1("loc.rooftops_canifis_jump_5") { thirdGap() }
        onOpLoc1("loc.rooftops_canifis_jump_3") { fourthGap() }
        onOpLoc1("loc.rooftops_canifis_polevault") { poleVault() }
        onOpLoc1("loc.rooftops_canifis_jump_4") { fifthGap() }
        onOpLoc1("loc.rooftops_canifis_leapdown") { lastGap() }
    }

    private suspend fun ProtectedAccess.tallTree() {
        arriveDelay()
        if (!meetsLevel(LEVEL)) {
            return
        }
        stepTo(TREE_START)
        anim("seq.mdaughter_tree_climb_combi", delay = 60)
        glide(0, 1, delay1 = 30, delay2 = 60, dir = FACE_NORTH)
        delay(2)
        glide(-2, 0, delay1 = 60, delay2 = 115, dir = FACE_EAST)
        delay(4)
        telejump(TREE_TOP, TeleportType.Exempt)
        resetAnim()
        passRooftopObstacle(0, 10.0)
    }

    private suspend fun ProtectedAccess.firstGap() {
        arriveDelay()
        anim(RUN_UP, delay = 20)
        glide(0, 1, delay1 = 20, delay2 = 40, dir = FACE_NORTH)
        delay(1)
        anim(JUMP_DOWN, delay = 10)
        delay(1)
        telejump(FIRST_GAP_LANDING, TeleportType.Exempt)
        anim(JUMP_DOWN_LAND)
        delay(1)
        passRooftopObstacle(1, 8.0)
    }

    private suspend fun ProtectedAccess.secondGap() {
        arriveDelay()
        stepTo(SECOND_GAP_START)
        leapTo(SECOND_GAP_LANDING)
        delay(1)
        forcedWalk(SECOND_GAP_EXIT, crossTiles = 1)
        passRooftopObstacle(2, 8.0)
    }

    private suspend fun ProtectedAccess.thirdGap() {
        arriveDelay()
        stepTo(THIRD_GAP_START)
        if (failsObstacle(LEVEL, SAFE_LEVEL)) {
            fallTo(THIRD_GAP_FALL, FALL_DAMAGE)
            return
        }
        anim(WALL_JUMP, delay = 20)
        glide(-6, 0, delay1 = 25, delay2 = 30, dir = FACE_WEST)
        delay(1)
        anim(WALL_JUMP_LAND)
        delay(1)
        glide(-2, 0, delay1 = 17, delay2 = 26, dir = FACE_WEST)
        delay(1)
        telejump(THIRD_GAP_LANDING, TeleportType.Exempt)
        resetAnim()
        passRooftopObstacle(3, 10.0)
    }

    private suspend fun ProtectedAccess.fourthGap() {
        arriveDelay()
        leapTo(FOURTH_GAP_LANDING)
        delay(1)
        passRooftopObstacle(4, 8.0)
    }

    private suspend fun ProtectedAccess.poleVault() {
        arriveDelay()
        stepTo(VAULT_START)
        anim(RUN_UP)
        delay(1)
        anim("seq.rooftops_pole_vault", delay = 10)
        glide(8, -8, delay1 = 10, delay2 = 150, dir = FACE_SOUTH_EAST)
        delay(5)
        telejump(VAULT_LANDING, TeleportType.Exempt)
        anim(JUMP_DOWN_LAND)
        passRooftopObstacle(5, 10.0)
    }

    private suspend fun ProtectedAccess.fifthGap() {
        arriveDelay()
        stepTo(FIFTH_GAP_START)
        leapTo(FIFTH_GAP_LANDING)
        delay(1)
        passRooftopObstacle(6, 11.0)
    }

    private suspend fun ProtectedAccess.lastGap() {
        arriveDelay()
        leapTo(FINISH)
        passRooftopObstacle(7, 175.0)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        announceLap("Canifis Rooftop", player.laps)
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS, MARK_RATE)
    }

    private companion object {
        const val LEVEL = 40
        const val SAFE_LEVEL = 64
        const val OBSTACLES = 8
        val FALL_DAMAGE = 1..5
        val MARK_RATE = MarkRate(2, 3, ignoresOverLevel = true)

        val TREE_START = CoordGrid(3507, 3488, 0)
        val TREE_TOP = CoordGrid(3506, 3492, 2)
        val FIRST_GAP_LANDING = CoordGrid(3502, 3504, 2)
        val SECOND_GAP_START = CoordGrid(3498, 3504, 2)
        val SECOND_GAP_LANDING = CoordGrid(3493, 3504, 2)
        val SECOND_GAP_EXIT = CoordGrid(3492, 3504, 2)
        val THIRD_GAP_START = CoordGrid(3487, 3499, 2)
        val THIRD_GAP_FALL = CoordGrid(3483, 3499, 0)
        val THIRD_GAP_LANDING = CoordGrid(3479, 3499, 3)
        val FOURTH_GAP_LANDING = CoordGrid(3478, 3486, 2)
        val VAULT_START = CoordGrid(3481, 3484, 2)
        val VAULT_LANDING = CoordGrid(3489, 3476, 3)
        val FIFTH_GAP_START = CoordGrid(3502, 3476, 3)
        val FIFTH_GAP_LANDING = CoordGrid(3510, 3476, 2)
        val FINISH = CoordGrid(3510, 3485, 0)

        val MARK_SPAWNS =
            listOf(
                CoordGrid(3508, 3494, 2),
                CoordGrid(3502, 3506, 2),
                CoordGrid(3499, 3505, 2),
                CoordGrid(3489, 3500, 2),
                CoordGrid(3492, 3499, 2),
                CoordGrid(3476, 3496, 3),
                CoordGrid(3475, 3493, 3),
                CoordGrid(3482, 3486, 2),
                CoordGrid(3478, 3484, 2),
                CoordGrid(3493, 3476, 3),
                CoordGrid(3495, 3472, 3),
                CoordGrid(3491, 3472, 3),
                CoordGrid(3513, 3479, 2),
                CoordGrid(3512, 3481, 2),
                CoordGrid(3510, 3476, 2),
            )
    }
}
