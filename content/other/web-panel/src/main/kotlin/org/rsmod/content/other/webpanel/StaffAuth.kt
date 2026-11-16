package org.rsmod.content.other.webpanel

import dev.or2.central.account.Rights
import dev.or2.central.auth.PasswordHasher
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.webauth.LoginThrottle
import org.rsmod.api.webauth.TotpReplayGuard
import org.rsmod.api.webauth.WebTokens

data class StaffAccount(
    val accountId: Int,
    val accountName: String,
    val passwordHash: String,
    val rights: Rights,
    val twoFactorSecret: String?,
    val twoFactorConfirmed: Boolean,
)

interface StaffStore {
    fun findAccount(accountName: String): StaffAccount?

    fun rightsOf(accountId: Int): Rights

    fun enableTwoFactor(accountId: Int, secret: String)
}

interface StaffTotp {
    fun newSecret(): String

    /** [storedSecret] is the `accounts.two_factor_secret` value: plain base32 or Laravel-encrypted. */
    fun verify(storedSecret: String, code: String): Boolean
}

data class StaffSession(val accountId: Int, val name: String, val rights: Rights) {
    val isAdmin: Boolean
        get() = rights.isAtLeast(Rights.ADMINISTRATOR)
}

/**
 * Staff sign-in for the website admin area: a game account password plus an authenticator code.
 * Accounts need at least [Rights.MOD]. An account without two-factor gets a fresh secret to enrol
 * during its first sign-in, which then also protects its in-game login. Sessions live in memory,
 * so a server restart signs every staff member out.
 */
class StaffAuth(
    private val store: StaffStore,
    private val hasher: () -> PasswordHasher,
    private val totp: StaffTotp,
    private val issuer: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val challenges = ConcurrentHashMap<String, Challenge>()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val throttle = LoginThrottle(MAX_FAILURES, LOCKOUT_MILLIS, clock)
    private val replayGuard = TotpReplayGuard(CODE_REUSE_WINDOW_MILLIS, clock)
    private val dummyHash by lazy { hasher().hash("dummy-password-for-timing") }

    /**
     * Checks the password. Unknown accounts still pay the full hashing cost so response time doesn't
     * reveal which names exist, and both the name and client address are locked out after
     * [MAX_FAILURES] failed passwords or codes inside [LOCKOUT_MILLIS].
     */
    fun login(username: String, password: String, client: String): LoginResult {
        val keys = LoginThrottle.keys(username, client)
        val lockedFor = throttle.lockedFor(keys)
        if (lockedFor > 0) {
            return LoginResult.Locked(lockedFor)
        }
        val account = store.findAccount(username.trim())
        val matches = hasher().verify(account?.passwordHash ?: dummyHash, password) && account != null
        if (!matches) {
            throttle.recordFailure(keys)
            return LoginResult.Invalid
        }
        if (!account.rights.isAtLeast(Rights.MOD)) {
            return LoginResult.NotStaff
        }
        val now = clock()
        pruneChallenges(now)
        val enrolSecret = if (account.twoFactorConfirmed) null else totp.newSecret()
        val id = WebTokens.newToken()
        challenges[id] = Challenge(account.accountId, account.accountName, enrolSecret, createdAt = now)
        return if (enrolSecret == null) LoginResult.NeedCode(id) else LoginResult.NeedEnrolment(id)
    }

    fun challenge(id: String?): ChallengeInfo? {
        val challenge = activeChallenge(id) ?: return null
        val secret = challenge.enrolSecret
        return ChallengeInfo(
            accountName = challenge.accountName,
            enrol = secret != null,
            secret = secret,
            otpauthUri = secret?.let { WebTokens.otpauthUri(issuer, challenge.accountName, it) },
        )
    }

    fun verify(id: String, code: String, client: String): VerifyResult {
        val challenge = activeChallenge(id) ?: return VerifyResult.Expired
        val keys = LoginThrottle.keys(challenge.accountName, client)
        val lockedFor = throttle.lockedFor(keys)
        if (lockedFor > 0) {
            challenges.remove(id)
            return VerifyResult.Locked(lockedFor)
        }
        if (!codeMatches(challenge, code)) {
            throttle.recordFailure(keys)
            challenge.attempts++
            if (challenge.attempts >= MAX_CODE_ATTEMPTS) {
                challenges.remove(id)
                return VerifyResult.Invalid(restart = true)
            }
            return VerifyResult.Invalid(restart = false)
        }
        challenge.enrolSecret?.let { store.enableTwoFactor(challenge.accountId, it) }
        challenges.remove(id)
        throttle.clear(keys)
        val rights = store.rightsOf(challenge.accountId)
        if (!rights.isAtLeast(Rights.MOD)) {
            return VerifyResult.Expired
        }
        val now = clock()
        val token = WebTokens.newToken()
        val session = StaffSession(challenge.accountId, challenge.accountName, rights)
        sessions[token] = Session(session, createdAt = now, lastSeen = now, rightsCheckedAt = now)
        return VerifyResult.Success(token, session)
    }

    /** Re-reads the account's rights every [RIGHTS_RECHECK_MILLIS] so a demotion ends the session. */
    fun session(token: String?): StaffSession? {
        if (token == null) {
            return null
        }
        val session = sessions[token] ?: return null
        val now = clock()
        val expired =
            now - session.lastSeen > IDLE_TIMEOUT_MILLIS ||
                now - session.createdAt > ABSOLUTE_TIMEOUT_MILLIS
        if (expired) {
            sessions.remove(token)
            return null
        }
        if (now - session.rightsCheckedAt > RIGHTS_RECHECK_MILLIS) {
            val rights = store.rightsOf(session.staff.accountId)
            if (!rights.isAtLeast(Rights.MOD)) {
                sessions.remove(token)
                return null
            }
            session.staff = session.staff.copy(rights = rights)
            session.rightsCheckedAt = now
        }
        session.lastSeen = now
        return session.staff
    }

    fun logout(token: String?) {
        if (token != null) {
            sessions.remove(token)
        }
    }

    /** Ends every session for [accountId], e.g. after its rights or authenticator change. */
    fun endSessions(accountId: Int) {
        sessions.values.removeIf { it.staff.accountId == accountId }
    }

    private fun codeMatches(challenge: Challenge, code: String): Boolean {
        val secret =
            challenge.enrolSecret
                ?: store.findAccount(challenge.accountName)?.twoFactorSecret
                ?: return false
        return replayGuard.accept(challenge.accountId, code) { totp.verify(secret, it) }
    }

    private fun activeChallenge(id: String?): Challenge? {
        if (id == null) {
            return null
        }
        val challenge = challenges[id] ?: return null
        if (clock() - challenge.createdAt > CHALLENGE_TTL_MILLIS) {
            challenges.remove(id)
            return null
        }
        return challenge
    }

    private fun pruneChallenges(now: Long) {
        if (challenges.size > MAX_TRACKED) {
            challenges.values.removeIf { now - it.createdAt > CHALLENGE_TTL_MILLIS }
        }
    }

    sealed interface LoginResult {
        data class NeedCode(val challenge: String) : LoginResult

        data class NeedEnrolment(val challenge: String) : LoginResult

        data object Invalid : LoginResult

        data object NotStaff : LoginResult

        data class Locked(val remainingMillis: Long) : LoginResult
    }

    sealed interface VerifyResult {
        data class Success(val token: String, val staff: StaffSession) : VerifyResult

        data class Invalid(val restart: Boolean) : VerifyResult

        data object Expired : VerifyResult

        data class Locked(val remainingMillis: Long) : VerifyResult
    }

    data class ChallengeInfo(
        val accountName: String,
        val enrol: Boolean,
        val secret: String?,
        val otpauthUri: String?,
    )

    private class Challenge(
        val accountId: Int,
        val accountName: String,
        val enrolSecret: String?,
        val createdAt: Long,
    ) {
        @Volatile var attempts: Int = 0
    }

    private class Session(
        @Volatile var staff: StaffSession,
        val createdAt: Long,
        @Volatile var lastSeen: Long,
        @Volatile var rightsCheckedAt: Long,
    )

    companion object {
        const val MAX_FAILURES = 5
        const val MAX_CODE_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 15 * 60 * 1000L
        const val CHALLENGE_TTL_MILLIS = 5 * 60 * 1000L
        const val IDLE_TIMEOUT_MILLIS = 2 * 60 * 60 * 1000L
        const val ABSOLUTE_TIMEOUT_MILLIS = 12 * 60 * 60 * 1000L
        const val RIGHTS_RECHECK_MILLIS = 60 * 1000L
        const val CODE_REUSE_WINDOW_MILLIS = 2 * 60 * 1000L

        private const val MAX_TRACKED = 10_000
    }
}
