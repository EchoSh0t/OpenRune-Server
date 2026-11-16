package org.rsmod.content.skills.agility.courses

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.skills.agility.BALANCE_READY
import org.rsmod.content.skills.agility.BALANCE_WALK
import org.rsmod.content.skills.agility.FACE_NORTH
import org.rsmod.content.skills.agility.agilityXp
import org.rsmod.content.skills.agility.announceLap
import org.rsmod.content.skills.agility.lapComplete
import org.rsmod.content.skills.agility.progressLap
import org.rsmod.content.skills.agility.rollMarkOfGrace
import org.rsmod.content.skills.agility.stepTo
import org.rsmod.content.skills.agility.withMoveAnims
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GnomeStrongholdCourse
@Inject
constructor(private val objRepo: ObjRepository, private val npcRepo: NpcRepository) :
    PluginScript() {
    private var Player.laps by intVarp("varp.agility_laps_gnome")

    override fun ScriptContext.startup() {
        onOpLoc1("loc.gnome_log_balance1") { logBalance() }
        onOpLoc1("loc.obstical_net2") { firstNet(it.loc.coords) }
        onOpLoc1("loc.climbing_branch") { branchUp() }
        onOpLoc1("loc.balancing_rope") { rope() }
        onOpLoc1("loc.balancing_rope_mid") { rope() }
        onOpLoc1("loc.climbing_tree") { branchDown() }
        onOpLoc1("loc.climbing_tree2") { branchDown() }
        onOpLoc1("loc.obstical_net3") { secondNet(it.loc.coords) }
        onOpLoc1("loc.obstical_pipe3_1") { pipe(it.loc.coords) }
        onOpLoc1("loc.obstical_pipe3_2") { pipe(it.loc.coords) }
    }

    private suspend fun ProtectedAccess.logBalance() {
        trainerSay("Okay get over that log, quick quick!")
        arriveDelay()
        stepTo(LOG_START)
        mes("You walk carefully across the slippery log...")
        withMoveAnims(BALANCE_READY, BALANCE_WALK, BALANCE_WALK) { stepTo(LOG_END) }
        mes("...You make it safely to the other side.")
        agilityXp(10.0)
        progressLap(0)
    }

    private suspend fun ProtectedAccess.firstNet(net: CoordGrid) {
        if (coords.z <= net.z) {
            mes("You can not do that from here.")
            return
        }
        mes("You climb the netting...")
        trainerSay("Move it, move it, move it!")
        arriveDelay()
        climb(CoordGrid(coords.x, FIRST_NET_LANDING_Z, 1))
        agilityXp(10.0)
        progressLap(1)
    }

    private suspend fun ProtectedAccess.branchUp() {
        mes("You climb the tree..")
        trainerSay("That's it - straight up")
        arriveDelay()
        climb(PLATFORM)
        mes("...To the platform above.")
        agilityXp(6.5)
        progressLap(2)
    }

    private suspend fun ProtectedAccess.rope() {
        arriveDelay()
        stepTo(ROPE_START)
        mes("You carefully cross the tightrope.")
        trainerSay("Come on scaredy cat, get across that rope!")
        withMoveAnims(BALANCE_READY, BALANCE_WALK, BALANCE_WALK) { stepTo(ROPE_END) }
        agilityXp(10.0)
        progressLap(3)
    }

    private suspend fun ProtectedAccess.branchDown() {
        mes("You climb down the tree...")
        arriveDelay()
        climb(BRANCH_LANDING)
        mes("You land on the ground.")
        agilityXp(6.5)
        progressLap(4)
    }

    private suspend fun ProtectedAccess.secondNet(net: CoordGrid) {
        if (coords.z >= net.z) {
            mes("You can not do that from here.")
            return
        }
        mes("You climb the netting...")
        trainerSay("My Granny can move faster than you.")
        arriveDelay()
        climb(CoordGrid(coords.x, SECOND_NET_LANDING_Z, 0))
        agilityXp(10.0)
        progressLap(5)
    }

    private suspend fun ProtectedAccess.pipe(pipe: CoordGrid) {
        if (coords.z > pipe.z) {
            mes("You can't enter the pipe from this side.")
            return
        }
        val entrance = CoordGrid(pipe.x, pipe.z - 1, 0)
        val inside = CoordGrid(pipe.x, pipe.z + 2, 0)
        val halfway = CoordGrid(pipe.x, pipe.z + 3, 0)
        val exit = CoordGrid(pipe.x, pipe.z + 6, 0)
        arriveDelay()
        stepTo(entrance)
        squeeze(entrance, inside)
        teleport(halfway, TeleportType.Exempt)
        delay(1)
        squeeze(halfway, exit)
        agilityXp(7.5)
        progressLap(6)
        if (!lapComplete(OBSTACLES)) {
            return
        }
        agilityXp(LAP_BONUS_XP)
        player.laps += 1
        announceLap("Gnome Stronghold Agility", player.laps)
        rollMarkOfGrace(objRepo, LEVEL, MARK_SPAWNS)
    }

    private suspend fun ProtectedAccess.climb(dest: CoordGrid) {
        anim("seq.human_reachforladder")
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.squeeze(from: CoordGrid, to: CoordGrid) {
        anim("seq.human_doublepipesqueeze", delay = PIPE_ANIM_DELAY)
        exactMove(from, to, delay1 = PIPE_MOVE_START, delay2 = PIPE_MOVE_END, dir = FACE_NORTH)
        delay(PIPE_TICKS)
    }

    private fun ProtectedAccess.trainerSay(line: String) {
        val trainer =
            npcRepo.findAll(ZoneKey.from(coords), zoneRadius = 1).firstOrNull {
                it.isType(TRAINER) &&
                    it.coords.level == coords.level &&
                    it.coords.chebyshevDistance(coords) <= TRAINER_RANGE
            }
        trainer?.say(line)
    }

    private companion object {
        const val LEVEL = 1
        const val OBSTACLES = 7
        const val LAP_BONUS_XP = 50.0
        const val TRAINER = "npc.gnometrainer"
        const val TRAINER_RANGE = 5
        const val FIRST_NET_LANDING_Z = 3424
        const val SECOND_NET_LANDING_Z = 3427
        const val PIPE_ANIM_DELAY = 30
        const val PIPE_MOVE_START = 30
        const val PIPE_MOVE_END = 126
        const val PIPE_TICKS = 4

        val LOG_START = CoordGrid(2474, 3436, 0)
        val LOG_END = CoordGrid(2474, 3429, 0)
        val PLATFORM = CoordGrid(2473, 3420, 2)
        val ROPE_START = CoordGrid(2477, 3420, 2)
        val ROPE_END = CoordGrid(2483, 3420, 2)
        val BRANCH_LANDING = CoordGrid(2487, 3420, 0)

        val MARK_SPAWNS = listOf(CoordGrid(2486, 3422, 2))
    }
}
