package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.FACE_EAST
import org.rsmod.content.skills.agility.agilityXp
import org.rsmod.content.skills.agility.balanceAcross
import org.rsmod.content.skills.agility.climbTo
import org.rsmod.content.skills.agility.lapComplete
import org.rsmod.content.skills.agility.leapTo
import org.rsmod.content.skills.agility.meetsLevel
import org.rsmod.content.skills.agility.progressLap
import org.rsmod.content.skills.agility.rollMarkOfGrace
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
        climbTo(ROUGH_WALL_TOP, "seq.human_reachforladder")
        agilityXp(5.0)
        progressLap(0)
    }

    private suspend fun ProtectedAccess.firstTightrope() {
        arriveDelay()
        balanceAcross(ROPE_1_START, ROPE_1_END, BALANCE_SEQ, BALANCE_READY_SEQ)
        agilityXp(8.0)
        progressLap(1)
    }

    private suspend fun ProtectedAccess.secondTightrope() {
        arriveDelay()
        balanceAcross(ROPE_2_START, ROPE_2_END, BALANCE_SEQ, BALANCE_READY_SEQ)
        agilityXp(7.0)
        progressLap(2)
    }

    private suspend fun ProtectedAccess.narrowWall() {
        arriveDelay()
        balanceAcross(
            NARROW_WALL_START,
            NARROW_WALL_END,
            walkSeq = "seq.human_walk_sidestepl",
            readySeq = "seq.human_ready_sidestepl",
            introSeq = "seq.human_into_sidestepl",
            outroSeq = "seq.human_outof_sidestepl",
        )
        forcedWalk(NARROW_WALL_EXIT, crossTiles = 1)
        agilityXp(7.0)
        progressLap(3)
    }

    private suspend fun ProtectedAccess.wall() {
        arriveDelay()
        anim("seq.agility_shortcut_wall_jump")
        delay(1)
        climbTo(WALL_TOP, "seq.agility_shortcut_wall_jump2", ticks = 1)
        agilityXp(10.0)
        progressLap(4)
    }

    private suspend fun ProtectedAccess.gap() {
        arriveDelay()
        leapTo(
            GAP_LANDING,
            "seq.agility_shortcut_wall_jumpdown",
            FACE_EAST,
            landSeq = "seq.agility_shortcut_wall_jumpdown2",
        )
        agilityXp(4.0)
        progressLap(5)
    }

    private suspend fun ProtectedAccess.crate() {
        arriveDelay()
        anim("seq.agility_shortcut_wall_jumpdown")
        delay(1)
        telejump(FINISH, TeleportType.Exempt)
        anim("seq.agility_shortcut_wall_jumpdown2")
        agilityXp(79.0)
        progressLap(6)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        player.laps += 1
        mes("Your Draynor Rooftop lap count is: <col=ff0000>${player.laps}</col>.")
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS)
    }

    private companion object {
        const val LEVEL = 1
        const val OBSTACLES = 7
        const val BALANCE_SEQ = "seq.human_walk_logbalance"
        const val BALANCE_READY_SEQ = "seq.human_walk_logbalance_ready"

        val ROUGH_WALL_TOP = CoordGrid(3102, 3279, 3)
        val ROPE_1_START = CoordGrid(3099, 3277, 3)
        val ROPE_1_END = CoordGrid(3090, 3277, 3)
        val ROPE_2_START = CoordGrid(3092, 3276, 3)
        val ROPE_2_END = CoordGrid(3092, 3267, 3)
        val NARROW_WALL_START = CoordGrid(3089, 3265, 3)
        val NARROW_WALL_END = CoordGrid(3089, 3262, 3)
        val NARROW_WALL_EXIT = CoordGrid(3088, 3261, 3)
        val WALL_TOP = CoordGrid(3088, 3255, 3)
        val GAP_LANDING = CoordGrid(3096, 3256, 3)
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
