package org.rsmod.content.other.websiteapi

import dev.or2.central.auth.PasswordHasher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccountAuthTest {
    private var now = 1_000_000L
    private val store = MemoryStore()
    private val totp = FakeTotp()
    private val auth = AccountAuth(store, { PlainHasher }, totp, "Elysium") { now }

    @Test
    fun `account without authenticator signs straight in`() {
        store.add("Zezima", secret = null)
        val token = signedIn(auth.login("zezima", PASSWORD, IP))
        assertEquals("Zezima", auth.session(token)?.accountName)
    }

    @Test
    fun `wrong password and unknown account look the same`() {
        store.add("Zezima", secret = null)
        assertEquals(AccountAuth.LoginResult.Invalid, auth.login("Zezima", "wrong-password", IP))
        assertEquals(AccountAuth.LoginResult.Invalid, auth.login("Nobody", PASSWORD, IP))
    }

    @Test
    fun `repeated failures lock the account name`() {
        store.add("Zezima", secret = null)
        repeat(AccountAuth.MAX_FAILURES) { auth.login("Zezima", "wrong-password", "10.0.0.$it") }
        assertInstanceOf(AccountAuth.LoginResult.Locked::class.java, auth.login("Zezima", PASSWORD, "10.0.1.1"))
        now += AccountAuth.LOCKOUT_MILLIS + 1
        signedIn(auth.login("Zezima", PASSWORD, IP))
    }

    @Test
    fun `authenticator account needs a valid code`() {
        store.add("Zezima", secret = "S1")
        totp.validCodes["S1"] = "123456"
        val challenge = needCode(auth.login("Zezima", PASSWORD, IP))
        assertEquals(AccountAuth.VerifyResult.Invalid(restart = false), auth.verify(challenge, "000000", IP))
        val result = auth.verify(challenge, "123 456", IP)
        val token = assertInstanceOf(AccountAuth.VerifyResult.Success::class.java, result).token
        assertNotNull(auth.session(token))
    }

    @Test
    fun `a code can't be replayed for a second sign-in`() {
        store.add("Zezima", secret = "S1")
        totp.validCodes["S1"] = "123456"
        auth.verify(needCode(auth.login("Zezima", PASSWORD, IP)), "123456", IP)
        val second = auth.verify(needCode(auth.login("Zezima", PASSWORD, IP)), "123456", IP)
        assertEquals(AccountAuth.VerifyResult.Invalid(restart = false), second)
    }

    @Test
    fun `too many wrong codes end the challenge`() {
        store.add("Zezima", secret = "S1")
        val challenge = needCode(auth.login("Zezima", PASSWORD, IP))
        repeat(AccountAuth.MAX_CODE_ATTEMPTS - 1) { auth.verify(challenge, "000000", "10.0.0.$it") }
        assertEquals(AccountAuth.VerifyResult.Invalid(restart = true), auth.verify(challenge, "000000", "10.0.2.1"))
        assertEquals(AccountAuth.VerifyResult.Expired, auth.verify(challenge, "000000", "10.0.2.2"))
    }

    @Test
    fun `sessions expire when idle and after the absolute limit`() {
        store.add("Zezima", secret = null)
        val idle = signedIn(auth.login("Zezima", PASSWORD, IP))
        now += AccountAuth.IDLE_TIMEOUT_MILLIS + 1
        assertNull(auth.session(idle))

        val busy = signedIn(auth.login("Zezima", PASSWORD, IP))
        repeat(8) {
            now += AccountAuth.IDLE_TIMEOUT_MILLIS - 1
            auth.session(busy)
        }
        assertNull(auth.session(busy))
    }

    @Test
    fun `changing password checks the old one and ends other sessions`() {
        val id = store.add("Zezima", secret = null)
        val mine = signedIn(auth.login("Zezima", PASSWORD, IP))
        val other = signedIn(auth.login("Zezima", PASSWORD, "5.6.7.8"))

        assertEquals(AccountAuth.ChangeResult.WrongPassword, auth.changePassword(mine, "nope-nope", NEW_PASSWORD, IP))
        assertInstanceOf(AccountAuth.ChangeResult.Rejected::class.java, auth.changePassword(mine, PASSWORD, "short", IP))
        assertEquals(AccountAuth.ChangeResult.Changed, auth.changePassword(mine, PASSWORD, NEW_PASSWORD, IP))

        assertEquals("h:$NEW_PASSWORD", store.byId.getValue(id).passwordHash)
        assertNotNull(auth.session(mine))
        assertNull(auth.session(other))
        assertEquals(AccountAuth.LoginResult.Invalid, auth.login("Zezima", PASSWORD, IP))
    }

    @Test
    fun `authenticator enrolment needs a code from the new secret`() {
        val id = store.add("Zezima", secret = null)
        val session = auth.session(signedIn(auth.login("Zezima", PASSWORD, IP)))!!
        val info = auth.beginEnrolment(session)!!
        assertTrue(info.otpauthUri.contains("secret=${info.secret}"))
        assertEquals(info, auth.beginEnrolment(session))

        totp.validCodes[info.secret] = "654321"
        assertFalse(auth.confirmEnrolment(session, "111111"))
        assertTrue(auth.confirmEnrolment(session, "654321"))
        assertEquals(info.secret, store.byId.getValue(id).twoFactorSecret)
        assertNull(auth.beginEnrolment(session))
    }

    @Test
    fun `removing the authenticator needs a current code`() {
        val id = store.add("Zezima", secret = "S1")
        totp.validCodes["S1"] = "123456"
        val token = assertInstanceOf(
            AccountAuth.VerifyResult.Success::class.java,
            auth.verify(needCode(auth.login("Zezima", PASSWORD, IP)), "123456", IP),
        ).token
        val session = auth.session(token)!!
        now += AccountAuth.CODE_REUSE_WINDOW_MILLIS + 1
        assertFalse(auth.disableTwoFactor(session, "000000"))
        assertTrue(auth.disableTwoFactor(session, "123456"))
        assertNull(store.byId.getValue(id).twoFactorSecret)
    }

    private fun signedIn(result: AccountAuth.LoginResult): String =
        assertInstanceOf(AccountAuth.LoginResult.SignedIn::class.java, result).token

    private fun needCode(result: AccountAuth.LoginResult): String =
        assertInstanceOf(AccountAuth.LoginResult.NeedCode::class.java, result).challenge

    private class MemoryStore : AccountAuthStore {
        val byId = HashMap<Int, WebAccount>()

        fun add(name: String, secret: String?): Int {
            val id = byId.size + 1
            byId[id] = WebAccount(id, name, "h:$PASSWORD", secret, secret != null)
            return id
        }

        override fun findAccount(accountName: String): WebAccount? =
            byId.values.firstOrNull { it.accountName.equals(accountName, ignoreCase = true) }

        override fun account(accountId: Int): WebAccount? = byId[accountId]

        override fun updatePassword(accountId: Int, passwordHash: String) {
            byId[accountId] = byId.getValue(accountId).copy(passwordHash = passwordHash)
        }

        override fun setTwoFactor(accountId: Int, secret: String?) {
            byId[accountId] =
                byId.getValue(accountId).copy(twoFactorSecret = secret, twoFactorConfirmed = secret != null)
        }
    }

    private class FakeTotp : AccountTotp {
        val validCodes = HashMap<String, String>()
        private var generated = 0

        override fun newSecret(): String = "NEWSECRET${++generated}"

        override fun verify(storedSecret: String, code: String): Boolean = validCodes[storedSecret] == code
    }

    private object PlainHasher : PasswordHasher {
        override fun hash(plainPassword: String): String = "h:$plainPassword"

        override fun verify(storedHash: String, plainPassword: String): Boolean = storedHash == "h:$plainPassword"

        override fun shouldUpgrade(storedHash: String): Boolean = false
    }

    private companion object {
        const val PASSWORD = "hunter2hunter2"
        const val NEW_PASSWORD = "correct horse battery"
        const val IP = "1.2.3.4"
    }
}
