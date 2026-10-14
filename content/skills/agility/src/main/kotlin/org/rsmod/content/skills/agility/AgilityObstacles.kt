package org.rsmod.content.skills.agility

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.BasType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal const val FACE_SOUTH = 0

private const val NO_ANIM = -1

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

/** Walks to [dest] ignoring collision, unless the player is already standing there. */
internal suspend fun ProtectedAccess.stepTo(dest: CoordGrid) {
    if (coords != dest) {
        forcedWalk(dest, crossTiles = coords.chebyshevDistance(dest))
    }
}

/**
 * Runs [block] with the player's stand/walk animations replaced, the way Jagex animates
 * tightropes and ledges: the player walks normally while the client loops [walkSeq].
 */
internal suspend fun ProtectedAccess.withMoveAnims(
    readySeq: String,
    turnSeq: String,
    walkSeq: String,
    block: suspend ProtectedAccess.() -> Unit,
) {
    val walk = walkSeq.asRSCM()
    player.bas = BasType(-1, readySeq.asRSCM(), turnSeq.asRSCM(), walk, walk, walk, walk, NO_ANIM)
    try {
        block()
    } finally {
        player.bas = null
    }
}
