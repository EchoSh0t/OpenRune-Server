package org.rsmod.content.skills.agility

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseAgilityLvl
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

private const val MARK = "obj.grace"
private const val MARK_DURATION_TICKS = 1000
private const val MARK_COOLDOWN_MINUTES = 3
private const val OVERLEVELLED_MARGIN = 20
private const val OVERLEVELLED_DIVISOR = 5

private var Player.lastMarkMinute by intVarp("varp.agility_mark_minute")

/**
 * A course's Mark of Grace odds per completed lap: [numerator] in [denominator], cut to a fifth
 * when the player's base level is 20+ above the course requirement unless [ignoresOverLevel].
 */
internal class MarkRate(
    val numerator: Int,
    val denominator: Int,
    val ignoresOverLevel: Boolean = false,
)

internal val STANDARD_MARK_RATE = MarkRate(2, 6)

/**
 * Rolls a Mark of Grace at the end of a lap. Marks need three real-time minutes since the last
 * spawn, then appear at [rate] on a random spot from [spawns], visible only to that player.
 */
internal fun ProtectedAccess.rollMarkOfGrace(
    objRepo: ObjRepository,
    courseLevel: Int,
    spawns: List<CoordGrid>,
    rate: MarkRate = STANDARD_MARK_RATE,
) {
    val minute = (System.currentTimeMillis() / 60_000).toInt()
    if (minute - player.lastMarkMinute < MARK_COOLDOWN_MINUTES) {
        return
    }
    val roll = random.of(rate.denominator * OVERLEVELLED_DIVISOR)
    if (!rollsMark(rate, player.baseAgilityLvl, courseLevel, roll)) {
        return
    }
    player.lastMarkMinute = minute
    val tile = spawns[random.of(spawns.size)]
    objRepo.add(MARK, tile, MARK_DURATION_TICKS, receiver = player)
}

/** [roll] is uniform in `0 until rate.denominator * 5`. */
internal fun rollsMark(rate: MarkRate, baseLevel: Int, courseLevel: Int, roll: Int): Boolean {
    val overLevelled = !rate.ignoresOverLevel && baseLevel >= courseLevel + OVERLEVELLED_MARGIN
    val successes =
        if (overLevelled) rate.numerator else rate.numerator * OVERLEVELLED_DIVISOR
    return roll < successes
}
