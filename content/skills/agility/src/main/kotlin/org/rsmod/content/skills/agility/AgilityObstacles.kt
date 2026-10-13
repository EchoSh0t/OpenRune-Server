package org.rsmod.content.skills.agility

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.BasType
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal const val FACE_EAST = 1536

private const val CYCLES_PER_TICK = 30

internal var Player.courseStage by intVarBit("varbit.agility_course_stage")

internal fun ProtectedAccess.meetsLevel(level: Int): Boolean {
    if (player.agilityLvl >= level) {
        return true
    }
    mes("You need an Agility level of $level to attempt this obstacle.")
    return false
}

/**
 * Advances lap progress for an obstacle at [index] (0-based). The first obstacle always starts a
 * new lap; any later obstacle only counts when the previous one in the course was completed.
 */
internal fun ProtectedAccess.progressLap(index: Int) {
    player.courseStage =
        when {
            index == 0 -> 1
            player.courseStage == index -> index + 1
            else -> 0
        }
}

internal fun ProtectedAccess.lapComplete(obstacles: Int): Boolean = player.courseStage == obstacles

internal fun ProtectedAccess.agilityXp(xp: Double) {
    statAdvance("stat.agility", xp)
}

/**
 * Walks [start] to [end] ignoring collision, with the player's movement animations swapped for
 * [walkSeq]/[readySeq] so the client plays them as the normal walk cycle (tightropes, ledges).
 */
internal suspend fun ProtectedAccess.balanceAcross(
    start: CoordGrid,
    end: CoordGrid,
    walkSeq: String,
    readySeq: String,
    introSeq: String? = null,
    outroSeq: String? = null,
) {
    if (coords != start) {
        forcedWalk(start, crossTiles = coords.chebyshevDistance(start))
    }
    val walk = walkSeq.asRSCM()
    val ready = readySeq.asRSCM()
    player.bas = BasType(-1, ready, ready, walk, walk, walk, walk, walk)
    try {
        if (introSeq != null) {
            anim(introSeq)
            delay(1)
        }
        forcedWalk(end, crossTiles = start.chebyshevDistance(end))
        if (outroSeq != null) {
            anim(outroSeq)
            delay(1)
        }
    } finally {
        player.bas = null
    }
}

internal suspend fun ProtectedAccess.climbTo(dest: CoordGrid, seq: String, ticks: Int = 2) {
    anim(seq)
    delay(ticks)
    telejump(dest, TeleportType.Exempt)
}

internal suspend fun ProtectedAccess.leapTo(
    dest: CoordGrid,
    seq: String,
    dir: Int,
    ticks: Int = 1,
    landSeq: String? = null,
) {
    anim(seq)
    exactMove(coords, dest, delay1 = 15, delay2 = ticks * CYCLES_PER_TICK + 15, dir = dir)
    delay(ticks + 1)
    if (landSeq != null) {
        anim(landSeq)
        delay(1)
    }
}

private fun CoordGrid.chebyshevDistance(other: CoordGrid): Int =
    maxOf(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))
