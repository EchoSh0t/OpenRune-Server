package org.rsmod.content.other.websiteapi

import dev.or2.central.auth.PasswordHasher
import java.util.concurrent.ConcurrentHashMap
import org.rsmod.api.webauth.LoginThrottle
import org.rsmod.api.webauth.TotpReplayGuard
import org.rsmod.api.webauth.WebTokens

data class WebAccount(
    val accountId: Int,
    val accountName: String,
    val passwordHash: String,
    val twoFactorSecret: String?,
    val twoFactorConfirmed: Boolean,
)

interface AccountAuthStore {
    fun findAccount(accountName: String): WebAccount?

    fun account(accountId: Int): WebAccount?

    fun updatePassword(accountId: Int, passwordHash: String)

    /** Stores a confirmed authenticator secret, or removes two-factor when [secret] is null. */
    fun setTwoFactor(accountId: Int, secret: String?)
}

interface AccountTotp {
    fun newSecret(): String

    /** [storedSecret] is the `accounts.two_factor_secret` value: plain base32 or Laravel-encrypted. */
    fun verify(storedSecret: String, code: String): Boolean
}

data class AccountSession(val accountId: Int, val accountName: String)

/**
 * Player sign-in for the website's account pages, using the game account's password and, when the
 * account has one, its authenticator. Sessions live in memory, so a server restart signs everyone
 * out; changing the password ends every other session for the account.
 */
class AccountAuth(
    private val store: AccountAuthStore,
    private val hasher: () -> PasswordHasher,
    private val totp: AccountTotp,
    private val issuer: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val challenges = ConcurrentHashMap<String, Challenge>()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val enrolments = ConcurrentHashMap<Int, Enrolment>()
    private val throttle = LoginThrottle(MAX_FAILURES, LOCKOUT_MILLIS, clock)
    private val replayGuard = TotpReplayGuard(CODE_REUSE_WINDOW_MILLIS, clock)
    private val dummyHash by lazy { hasher().hash("dummy-password-for-timing") }

    /** Unknown accounts still pay the full hashing cost so timing doesn't reveal which names exist. */
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
        if (account.twoFactorConfirmed) {
            val now = clock()
            pruneChallenges(now)
            val id = WebTokens.newToken()
            challenges[id] = Challenge(account.accountId, account.accountName, now)
            return LoginResult.NeedCode(id)
        }
        throttle.clear(keys)
        return LoginResult.SignedIn(openSession(account))
    }

    fun verify(id: String, code: String, client: String): VerifyResult {
        val challenge = activeChallenge(id) ?: return VerifyResult.Expired
        val keys = LoginThrottle.keys(challenge.accountName, client)
        val lockedFor = throttle.lockedFor(keys)
        if (lockedFor > 0) {
            challenges.remove(id)
            return VerifyResult.Locked(lockedFor)
        }
        val account = store.account(challenge.accountId) ?: return VerifyResult.Expired
        val secret = account.twoFactorSecret
        val matches = secret != null && replayGuard.accept(account.accountId, code) { totp.verify(secret, it) }
        if (!matches) {
            throttle.recordFailure(keys)
            challenge.attempts++
            if (challenge.attempts >= MAX_CODE_ATTEMPTS) {
                challenges.remove(id)
                return VerifyResult.Invalid(restart = true)
            }
            return VerifyResult.Invalid(restart = false)
        }
        challenges.remove(id)
        throttle.clear(keys)
        return VerifyResult.Success(openSession(account))
    }

    fun session(token: String?): AccountSession? {
        if (token == null) {
            return null
        }
        val session = sessions[token] ?: return null
        val now = clock()
        if (now - session.lastSeen > IDLE_TIMEOUT_MILLIS || now - session.createdAt > ABSOLUTE_TIMEOUT_MILLIS) {
            sessions.remove(token)
            return null
        }
        session.lastSeen = now
        return session.account
    }

    fun logout(token: String?) {
        if (token != null) {
            sessions.remove(token)
        }
    }

    fun changePassword(token: String, current: String, next: String, client: String): ChangeResult {
        val session = session(token) ?: return ChangeResult.SignedOut
        val keys = LoginThrottle.keys(session.accountName, client)
        val lockedFor = throttle.lockedFor(keys)
        if (lockedFor > 0) {
            return ChangeResult.Locked(lockedFor)
        }
        val account = store.account(session.accountId) ?: return ChangeResult.SignedOut
        if (!hasher().verify(account.passwordHash, current)) {
            throttle.recordFailure(keys)
            return ChangeResult.WrongPassword
        }
        AccountRegistrar.passwordProblem(account.accountName, next)?.let {
            return ChangeResult.Rejected(it)
        }
        if (next == current) {
            return ChangeResult.Rejected("Your new password must be different from the old one.")
        }
        store.updatePassword(account.accountId, hasher().hash(next))
        sessions.entries.removeIf { it.value.account.accountId == account.accountId && it.key != token }
        throttle.clear(keys)
        return ChangeResult.Changed
    }

    /** Starts authenticator set-up; null when the account already has one. */
    fun beginEnrolment(session: AccountSession): EnrolInfo? {
        val account = store.account(session.accountId) ?: return null
        if (account.twoFactorConfirmed) {
            return null
        }
        val now = clock()
        val existing = enrolments[account.accountId]?.takeIf { now - it.createdAt <= ENROL_TTL_MILLIS }
        val secret = existing?.secret ?: totp.newSecret()
        if (existing == null) {
            enrolments[account.accountId] = Enrolment(secret, now)
        }
        return EnrolInfo(secret, WebTokens.otpauthUri(issuer, account.accountName, secret))
    }

    fun confirmEnrolment(session: AccountSession, code: String): Boolean {
        val enrolment = enrolments[session.accountId] ?: return false
        if (clock() - enrolment.createdAt > ENROL_TTL_MILLIS) {
            enrolments.remove(session.accountId)
            return false
        }
        if (!replayGuard.accept(session.accountId, code) { totp.verify(enrolment.secret, it) }) {
            return false
        }
        store.setTwoFactor(session.accountId, enrolment.secret)
        enrolments.remove(session.accountId)
        return true
    }

    /** Removing the authenticator needs a current code, so a stolen session alone can't do it. */
    fun disableTwoFactor(session: AccountSession, code: String): Boolean {
        val account = store.account(session.accountId) ?: return false
        val secret = account.twoFactorSecret?.takeIf { account.twoFactorConfirmed } ?: return false
        if (!replayGuard.accept(account.accountId, code) { totp.verify(secret, it) }) {
            return false
        }
        store.setTwoFactor(account.accountId, null)
        return true
    }

    private fun openSession(account: WebAccount): String {
        val now = clock()
        if (sessions.size > MAX_TRACKED) {
            sessions.values.removeIf {
                now - it.lastSeen > IDLE_TIMEOUT_MILLIS || now - it.createdAt > ABSOLUTE_TIMEOUT_MILLIS
            }
        }
        val token = WebTokens.newToken()
        sessions[token] = Session(AccountSession(account.accountId, account.accountName), now, now)
        return token
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
        data class SignedIn(val token: String) : LoginResult

        data class NeedCode(val challenge: String) : LoginResult

        data object Invalid : LoginResult

        data class Locked(val remainingMillis: Long) : LoginResult
    }

    sealed interface VerifyResult {
        data class Success(val token: String) : VerifyResult

        data class Invalid(val restart: Boolean) : VerifyResult

        data object Expired : VerifyResult

        data class Locked(val remainingMillis: Long) : VerifyResult
    }

    sealed interface ChangeResult {
        data object Changed : ChangeResult

        data object WrongPassword : ChangeResult

        data object SignedOut : ChangeResult

        data class Rejected(val message: String) : ChangeResult

        data class Locked(val remainingMillis: Long) : ChangeResult
    }

    data class EnrolInfo(val secret: String, val otpauthUri: String)

    private class Challenge(val accountId: Int, val accountName: String, val createdAt: Long) {
        @Volatile var attempts: Int = 0
    }

    private class Session(val account: AccountSession, val createdAt: Long, @Volatile var lastSeen: Long)

    private class Enrolment(val secret: String, val createdAt: Long)

    companion object {
        const val MAX_FAILURES = 5
        const val MAX_CODE_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 15 * 60 * 1000L
        const val CHALLENGE_TTL_MILLIS = 5 * 60 * 1000L
        const val ENROL_TTL_MILLIS = 15 * 60 * 1000L
        const val IDLE_TIMEOUT_MILLIS = 24 * 60 * 60 * 1000L
        const val ABSOLUTE_TIMEOUT_MILLIS = 7 * 24 * 60 * 60 * 1000L
        const val CODE_REUSE_WINDOW_MILLIS = 2 * 60 * 1000L

        private const val MAX_TRACKED = 10_000
    }
}
