package org.rsmod.content.other.webpanel

data class SkillView(val name: String, val level: Int, val base: Int)

data class PlayerView(
    val name: String,
    val combatLevel: Int,
    val totalLevel: Int,
    val x: Int,
    val z: Int,
    val level: Int,
    val runEnergy: Int,
    val skills: List<SkillView>,
)

data class ServerView(
    val uptimeSeconds: Long,
    val cycle: Long,
    val lastTickMs: Double,
    val avgTickMs: Double,
    val maxTickMs: Double,
    val heapUsedMb: Long,
    val heapMaxMb: Long,
    val maintenance: Boolean,
    val globalXpRate: Double,
    val supervised: Boolean,
    val countdownSeconds: Int?,
    val scheduledAction: String?,
    val players: List<PlayerView>,
)

data class BanView(val name: String, val bannedUntil: String, val permanent: Boolean)

data class ActionRequest(
    val type: String = "",
    val player: String? = null,
    val text: String? = null,
    val item: String? = null,
    val count: Int? = null,
    val x: Int? = null,
    val z: Int? = null,
    val level: Int? = null,
    val enabled: Boolean? = null,
    val rate: Double? = null,
    val seconds: Int? = null,
    val days: Int? = null,
    val mode: String? = null,
)

data class ActionResult(val ok: Boolean, val message: String, val data: Any? = null)
