package org.rsmod.content.other.websiteapi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(ignoreUnknown = true)
data class RegisterRequest(
    val username: String = "",
    val password: String = "",
    val email: String? = null,
) {
    override fun toString(): String = "RegisterRequest(username=$username, email=$email)"
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegisterResult(
    val ok: Boolean,
    val message: String,
    val code: String? = null,
    val accountName: String? = null,
)

object ErrorCode {
    const val NAME_INVALID = "name_invalid"
    const val NAME_TAKEN = "name_taken"
    const val PASSWORD_WEAK = "password_weak"
    const val EMAIL_INVALID = "email_invalid"
    const val RATE_LIMITED = "rate_limited"
    const val SERVER_ERROR = "server_error"
}

data class WorldCount(val id: Int, val count: Int)

data class OnlineView(val total: Int, val worlds: List<WorldCount>)

data class SkillInfo(val id: Int, val name: String)

data class HiscoreRow(val rank: Int, val name: String, val level: Int, val xp: Long)

data class HiscoresView(
    val skill: String,
    val page: Int,
    val pageSize: Int,
    val rows: List<HiscoreRow>,
    val skills: List<SkillInfo>,
)

data class PlayerSkillView(val skill: String, val rank: Int?, val level: Int, val xp: Long)

data class PlayerHiscoreView(val name: String, val skills: List<PlayerSkillView>)

data class StatusView(
    val online: Boolean,
    val players: Int,
    val maintenance: Boolean,
    val countdownSeconds: Long?,
    val uptimeSeconds: Long,
)

data class GainRow(val rank: Int, val name: String, val gained: Long)

data class GainsView(
    val period: String,
    val skill: String,
    val page: Int,
    val pageSize: Int,
    val rows: List<GainRow>,
    val skills: List<SkillInfo>,
)

data class SkillGainView(val skill: String, val xp: Long, val day: Long, val week: Long, val month: Long)

data class HistoryPoint(val day: String, val xp: Long)

data class PlayerGainsView(val name: String, val skills: List<SkillGainView>, val history: List<HistoryPoint>)

data class CharacterView(
    val name: String,
    val members: Boolean,
    val totalLevel: Int,
    val createdAt: String?,
    val banned: Boolean,
    val muted: Boolean,
)

data class AccountView(
    val name: String,
    val email: String?,
    val twoFactor: Boolean,
    val discordLinked: Boolean,
    val createdAt: String?,
    val staff: Boolean,
    val characters: List<CharacterView>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginRequest(val username: String = "", val password: String = "") {
    override fun toString(): String = "LoginRequest(username=$username)"
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CodeRequest(val challenge: String = "", val code: String = "")

@JsonIgnoreProperties(ignoreUnknown = true)
data class PasswordRequest(val current: String = "", val next: String = "") {
    override fun toString(): String = "PasswordRequest(***)"
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiscordLinkRequest(val discordId: String = "")

@JsonIgnoreProperties(ignoreUnknown = true)
data class ReportRequest(val offender: String = "", val category: String = "", val details: String = "")

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuthResponse(
    val ok: Boolean,
    val message: String? = null,
    val token: String? = null,
    val challenge: String? = null,
    val restart: Boolean? = null,
    val secret: String? = null,
    val otpauthUri: String? = null,
)
