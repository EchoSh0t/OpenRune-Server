package org.rsmod.content.skills.agility

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.BasType
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

internal const val FACE_SOUTH = 0
internal const val FACE_WEST = 512
internal const val FACE_NORTH_WEST = 768
internal const val FACE_NORTH = 1024
internal const val FACE_EAST = 1536
internal const val FACE_SOUTH_EAST = 1792

internal const val BALANCE_READY = "seq.human_walk_logbalance_ready"
internal const val BALANCE_WALK = "seq.human_walk_logbalance"
internal const val SIDESTEP_READY = "seq.human_ready_sidestepl"
internal const val SIDESTEP_WALK = "seq.human_walk_sidestepl"
internal const val CLIMB = "seq.human_reachforladder"
internal const val CLIMB_WALL = "seq.human_climbing"
internal const val RUN_UP = "seq.ic_running"
internal const val SPOT_JUMP = "seq.human_spot_jump"
internal const val HURDLE = "seq.human_jump_hurdle"
internal const val WALL_JUMP = "seq.agility_shortcut_wall_jump"
internal const val WALL_JUMP_LAND = "seq.agility_shortcut_wall_jump2"
internal const val JUMP_DOWN = "seq.agility_shortcut_wall_jumpdown"
internal const val JUMP_DOWN_LAND = "seq.agility_shortcut_wall_jumpdown2"
internal const val HANDHOLDS = "seq.agilityarena_handholds_middle"
internal const val HANDHOLDS_LAST = "seq.agilityarena_handholds_last"
internal const val HANDHOLDS_REVERSE = "seq.agilityarena_handholds_middlereverse"
internal const val HANDHOLDS_LAST_REVERSE = "seq.agilityarena_handholds_lastreverse"

private const val NO_ANIM = -1
private const val FALL = "seq.human_wobbleandfall_short_l"
private val ROOFTOP_ENERGY_PERCENT = 1..2

/** Chance to pass a failable obstacle at the course's level, from the Canifis wiki curve. */
internal const val BASE_OBSTACLE_SUCCESS = 0.8828

internal var Player.courseStage by intVarBit("varbit.agility_course_stage")
internal var Player.lapMessagesOff by boolVarBit("varbit.agility_lap_messages_off")

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

internal fun ProtectedAccess.announceLap(course: String, laps: Int) {
    if (!player.lapMessagesOff) {
        mes("Your $course lap count is: <col=ff0000>$laps</col>.")
    }
}

internal fun ProtectedAccess.agilityXp(xp: Double) {
    statAdvance("stat.agility", xp)
}

/** Rooftop obstacles award [xp], restore 1-2% run energy and advance the lap. */
internal fun ProtectedAccess.passRooftopObstacle(index: Int, xp: Double) {
    agilityXp(xp)
    val restore = random.of(ROOFTOP_ENERGY_PERCENT) * (constants.run_max_energy / 100)
    val energy = (player.runEnergy + restore).coerceAtMost(constants.run_max_energy)
    if (energy != player.runEnergy) {
        player.runEnergy = energy
        UpdateRun.energy(player, energy)
    }
    progressLap(index)
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

/** Walks [path] with the balancing animations, as on tightropes, planks and logs. */
internal suspend fun ProtectedAccess.balanceAlong(path: List<CoordGrid>) {
    withMoveAnims(BALANCE_READY, BALANCE_WALK, BALANCE_WALK) { walkPath(path) }
}

internal suspend fun ProtectedAccess.balanceAlong(dest: CoordGrid) = balanceAlong(listOf(dest))

/** Shuffles along [path] sideways with the player's back to a wall. */
internal suspend fun ProtectedAccess.sidestepAlong(path: List<CoordGrid>) {
    withMoveAnims(SIDESTEP_READY, SIDESTEP_READY, SIDESTEP_WALK) {
        anim("seq.human_into_sidestepl")
        walkPath(path)
    }
    anim("seq.human_outof_sidestepl")
}

internal suspend fun ProtectedAccess.sidestepAlong(dest: CoordGrid) = sidestepAlong(listOf(dest))

private suspend fun ProtectedAccess.walkPath(path: List<CoordGrid>) {
    var from = coords
    var tiles = 0
    for (tile in path) {
        tiles += from.chebyshevDistance(tile)
        from = tile
    }
    forcedWalk(path, crossTiles = tiles)
}

/** Reaches up a wall and appears at [dest] two ticks later. */
internal suspend fun ProtectedAccess.climbTo(dest: CoordGrid, seq: String = CLIMB) {
    anim(seq, delay = 15)
    delay(2)
    telejump(dest, TeleportType.Exempt)
    resetAnim()
}

/** Jumps across or down a gap, landing on [dest] a tick later. */
internal suspend fun ProtectedAccess.leapTo(dest: CoordGrid, seq: String = JUMP_DOWN) {
    anim(seq, delay = 15)
    delay(1)
    telejump(dest, TeleportType.Exempt)
    anim(JUMP_DOWN_LAND)
}

/** Glides the player [dx]/[dz] tiles from where they stand, as one `exactmove`. */
internal fun ProtectedAccess.glide(dx: Int, dz: Int, delay1: Int, delay2: Int, dir: Int) {
    exactMove(coords, coords.translate(dx, dz), delay1, delay2, dir, TeleportType.Exempt)
}

internal fun obstacleSuccessChance(level: Int, courseLevel: Int, safeLevel: Int): Double {
    if (level >= safeLevel) {
        return 1.0
    }
    val progress = (level - courseLevel).coerceAtLeast(0).toDouble() / (safeLevel - courseLevel)
    return BASE_OBSTACLE_SUCCESS + (1.0 - BASE_OBSTACLE_SUCCESS) * progress
}

internal fun ProtectedAccess.failsObstacle(courseLevel: Int, safeLevel: Int): Boolean =
    random.randomDouble() >= obstacleSuccessChance(player.agilityLvl, courseLevel, safeLevel)

/** Drops the player to [landing] for [damage], ending the current lap. */
internal suspend fun ProtectedAccess.fallTo(landing: CoordGrid, damage: IntRange) {
    anim(FALL)
    delay(2)
    telejump(landing, TeleportType.Exempt)
    resetAnim()
    player.courseStage = 0
    queueHit(delay = 1, type = HitType.Typeless, damage = random.of(damage))
    mes("You lose your footing and fall to the ground.")
}
