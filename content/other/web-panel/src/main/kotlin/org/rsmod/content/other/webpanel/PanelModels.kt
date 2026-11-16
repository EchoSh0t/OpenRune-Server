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
    val reason: String? = null,
)

data class StaffRequest(
    val type: String = "",
    val player: String? = null,
    val text: String? = null,
    val rights: String? = null,
    val id: Long? = null,
)

data class PunishmentView(
    val kind: String,
    val status: String,
    val reason: String,
    val issuedBy: String,
    val issuedAt: String?,
    val expiresAt: String?,
)

data class NoteView(val author: String, val body: String, val createdAt: String?)

data class AddressView(val ip: String, val logins: Int, val firstSeen: String?, val lastSeen: String?)

data class PlayerHistoryView(
    val punishments: List<PunishmentView>,
    val notes: List<NoteView>,
    val addresses: List<AddressView>,
    val alts: List<String>,
)

data class ReportView(
    val id: Long,
    val reporter: String,
    val offender: String,
    val category: String,
    val details: String,
    val source: String,
    val status: String,
    val createdAt: String?,
    val handledBy: String?,
    val handledAt: String?,
    val staffComment: String?,
)

data class StaffMemberView(
    val accountName: String,
    val rights: String,
    val twoFactor: Boolean,
    val characters: List<String>,
)

data class SearchResultView(val name: String, val accountName: String, val email: String?, val matched: String)

data class AuditEntryView(val time: String, val user: String, val client: String, val event: String)

data class ActionResult(val ok: Boolean, val message: String, val data: Any? = null)

data class StaffLoginRequest(val username: String = "", val password: String = "") {
    override fun toString(): String = "StaffLoginRequest(username=$username)"
}

data class StaffVerifyRequest(val challenge: String = "", val code: String = "")

data class StaffLoginView(
    val ok: Boolean,
    val message: String,
    val step: String? = null,
    val challenge: String? = null,
)

data class StaffVerifyView(
    val ok: Boolean,
    val message: String,
    val token: String? = null,
    val restart: Boolean = false,
)

data class StaffView(val name: String, val rights: String, val admin: Boolean)

data class LookupSkill(val name: String, val level: Int, val xp: Long)

data class LookupItem(val slot: Int, val name: String, val count: Int)

data class LookupInventory(val id: String, val label: String, val items: List<LookupItem>)

data class PlayerLookupView(
    val name: String,
    val characterId: Int,
    val accountId: Int,
    val accountName: String,
    val rights: String,
    val email: String?,
    val members: Boolean,
    val twoFactor: Boolean,
    val createdAt: String?,
    val lastLogin: String?,
    val lastLogout: String?,
    val bannedUntil: String?,
    val mutedUntil: String?,
    val x: Int?,
    val z: Int?,
    val level: Int?,
    val online: Boolean = false,
    val skills: List<LookupSkill> = emptyList(),
    val inventories: List<LookupInventory> = emptyList(),
    val history: PlayerHistoryView? = null,
)
