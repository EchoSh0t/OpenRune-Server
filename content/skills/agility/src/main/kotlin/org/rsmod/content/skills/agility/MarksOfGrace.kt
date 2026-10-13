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

private var Player.lastMarkMinute by intVarp("varp.agility_mark_minute")

/**
 * Rolls a Mark of Grace at the end of a lap. Marks need three real-time minutes since the last
 * spawn, then appear with a 1/3 chance (1/15 when the player's base level is 20+ above the course
 * requirement) at a random roof spot, visible only to that player.
 */
internal fun ProtectedAccess.rollMarkOfGrace(
    objRepo: ObjRepository,
    courseLevel: Int,
    spawns: List<CoordGrid>,
) {
    val minute = (System.currentTimeMillis() / 60_000).toInt()
    if (minute - player.lastMarkMinute < MARK_COOLDOWN_MINUTES) {
        return
    }
    val overLevelled = player.baseAgilityLvl >= courseLevel + OVERLEVELLED_MARGIN
    val chance = if (overLevelled) 15 else 3
    if (random.of(chance) != 0) {
        return
    }
    player.lastMarkMinute = minute
    val tile = spawns[random.of(spawns.size)]
    objRepo.add(MARK, tile, MARK_DURATION_TICKS, receiver = player)
}
