package org.rsmod.content.other.webpanel

import dev.or2.central.account.Rights
import dev.or2.central.auth.PasswordHasher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StaffAuthTest {
    private var now = 1_000_000L
    private val store = MemoryStore()
    private val totp = FakeTotp()
    private val auth = StaffAuth(store, { PlainHasher }, totp, "Elysium", clock = { now })

    @Test
    fun `wrong password and unknown accounts are both rejected`() {
        store.add("Zezima", Rights.ADMINISTRATOR, secret = "S1")

        assertEquals(StaffAuth.LoginResult.Invalid, auth.login("zezima", "wrong", IP))
        assertEquals(StaffAuth.LoginResult.Invalid, auth.login("nobody", PASSWORD, IP))
    }

    @Test
    fun `players without staff rights cannot sign in`() {
        store.add("Player", Rights.NONE, secret = "S1")

        assertEquals(StaffAuth.LoginResult.NotStaff, auth.login("player", PASSWORD, IP))
    }

    @Test
    fun `staff with two-factor sign in with their existing authenticator`() {
        val id = store.add("Mod Ash", Rights.MOD, secret = "S1")
        totp.validCodes["S1"] = "123456"

        val challenge = needCode(auth.login("mod ash", PASSWORD, IP))
        assertFalse(auth.challenge(challenge)!!.enrol)

        val success = assertInstanceOf(StaffAuth.VerifyResult.Success::class.java, auth.verify(challenge, "123 456", IP))
        assertEquals(StaffSession(id, "Mod Ash", Rights.MOD), auth.session(success.token))
        assertNull(store.enabled[id])
    }

    @Test
    fun `staff without two-factor enrol a new secret on first sign in`() {
        val id = store.add("Admin", Rights.ADMINISTRATOR, secret = null)

        val result = auth.login("admin", PASSWORD, IP)
        val challenge = assertInstanceOf(StaffAuth.LoginResult.NeedEnrolment::class.java, result).challenge
        val info = checkNotNull(auth.challenge(challenge))
        val secret = checkNotNull(info.secret)
        assertTrue(info.enrol)
        assertEquals("otpauth://totp/Elysium%3AAdmin?secret=$secret&issuer=Elysium&algorithm=SHA1&digits=6&period=30", info.otpauthUri)

        totp.validCodes[secret] = "654321"
        assertEquals(StaffAuth.VerifyResult.Invalid(restart = false), auth.verify(challenge, "000000", IP))
        assertNull(store.enabled[id])

        assertInstanceOf(StaffAuth.VerifyResult.Success::class.java, auth.verify(challenge, "654321", IP))
        assertEquals(secret, store.enabled[id])
    }

    @Test
    fun `codes must be six digits`() {
        store.add("Mod", Rights.MOD, secret = "S1")
        totp.validCodes["S1"] = "123456"
        val challenge = needCode(auth.login("mod", PASSWORD, IP))

        assertEquals(StaffAuth.VerifyResult.Invalid(restart = false), auth.verify(challenge, "12345", IP))
        assertEquals(StaffAuth.VerifyResult.Invalid(restart = false), auth.verify(challenge, "abcdef", IP))
    }

    @Test
    fun `a code cannot be reused for a second sign in`() {
        store.add("Mod", Rights.MOD, secret = "S1")
        totp.validCodes["S1"] = "123456"

        val first = needCode(auth.login("mod", PASSWORD, IP))
        assertInstanceOf(StaffAuth.VerifyResult.Success::class.java, auth.verify(first, "123456", IP))

        val second = needCode(auth.login("mod", PASSWORD, IP))
        assertEquals(StaffAuth.VerifyResult.Invalid(restart = false), auth.verify(second, "123456", IP))

        now += StaffAuth.CODE_REUSE_WINDOW_MILLIS + 1
        assertInstanceOf(StaffAuth.VerifyResult.Success::class.java, auth.verify(second, "123456", IP))
    }

    @Test
    fun `too many wrong codes cancels the sign in`() {
        store.add("Mod", Rights.MOD, secret = "S1")
        totp.validCodes["S1"] = "123456"
        val challenge = needCode(auth.login("mod", PASSWORD, IP))

        repeat(StaffAuth.MAX_CODE_ATTEMPTS - 1) {
            assertEquals(StaffAuth.VerifyResult.Invalid(restart = false), auth.verify(challenge, "000000", IP))
        }
        assertEquals(StaffAuth.VerifyResult.Invalid(restart = true), auth.verify(challenge, "000000", IP))
        assertEquals(StaffAuth.VerifyResult.Expired, auth.verify(challenge, "123456", IP))
    }

    @Test
    fun `repeated failures lock the account name and the address`() {
        store.add("Mod", Rights.MOD, secret = "S1")
        repeat(StaffAuth.MAX_FAILURES) { auth.login("mod", "wrong", "9.9.9.9") }

        assertInstanceOf(StaffAuth.LoginResult.Locked::class.java, auth.login("mod", PASSWORD, IP))
        assertInstanceOf(StaffAuth.LoginResult.Locked::class.java, auth.login("other", PASSWORD, "9.9.9.9"))

        now += StaffAuth.LOCKOUT_MILLIS + 1
        assertInstanceOf(StaffAuth.LoginResult.NeedCode::class.java, auth.login("mod", PASSWORD, IP))
    }

    @Test
    fun `wrong codes count towards the lockout`() {
        store.add("Mod", Rights.MOD, secret = "S1")
        totp.validCodes["S1"] = "123456"
        repeat(StaffAuth.MAX_FAILURES) {
            val challenge = needCode(auth.login("mod", PASSWORD, IP))
            auth.verify(challenge, "000000", IP)
        }

        assertInstanceOf(StaffAuth.LoginResult.Locked::class.java, auth.login("mod", PASSWORD, IP))
    }

    @Test
    fun `challenges expire`() {
        store.add("Mod", Rights.MOD, secret = "S1")
        totp.validCodes["S1"] = "123456"
        val challenge = needCode(auth.login("mod", PASSWORD, IP))

        now += StaffAuth.CHALLENGE_TTL_MILLIS + 1
        assertNull(auth.challenge(challenge))
        assertEquals(StaffAuth.VerifyResult.Expired, auth.verify(challenge, "123456", IP))
    }

    @Test
    fun `sessions end when idle, on logout and when rights are removed`() {
        val id = store.add("Mod", Rights.MOD, secret = "S1")
        totp.validCodes["S1"] = "123456"

        val idle = signIn("mod")
        now += StaffAuth.IDLE_TIMEOUT_MILLIS + 1
        assertNull(auth.session(idle))

        val loggedOut = signIn("mod")
        auth.logout(loggedOut)
        assertNull(auth.session(loggedOut))

        val demoted = signIn("mod")
        store.rights[id] = Rights.NONE
        assertNotNull(auth.session(demoted))
        now += StaffAuth.RIGHTS_RECHECK_MILLIS + 1
        assertNull(auth.session(demoted))
    }

    @Test
    fun `promotions show up after the rights recheck`() {
        val id = store.add("Mod", Rights.MOD, secret = "S1")
        totp.validCodes["S1"] = "123456"
        val token = signIn("mod")

        store.rights[id] = Rights.ADMINISTRATOR
        now += StaffAuth.RIGHTS_RECHECK_MILLIS + 1
        assertTrue(auth.session(token)!!.isAdmin)
    }

    private fun signIn(name: String): String {
        now += StaffAuth.CODE_REUSE_WINDOW_MILLIS + 1
        val challenge = needCode(auth.login(name, PASSWORD, IP))
        val code = totp.validCodes.getValue("S1")
        return assertInstanceOf(StaffAuth.VerifyResult.Success::class.java, auth.verify(challenge, code, IP)).token
    }

    private fun needCode(result: StaffAuth.LoginResult): String =
        assertInstanceOf(StaffAuth.LoginResult.NeedCode::class.java, result).challenge

    private class MemoryStore : StaffStore {
        val accounts = HashMap<String, StaffAccount>()
        val rights = HashMap<Int, Rights>()
        val enabled = HashMap<Int, String>()

        fun add(name: String, rights: Rights, secret: String?): Int {
            val id = accounts.size + 1
            accounts[name.lowercase()] = StaffAccount(id, name, "h:$PASSWORD", rights, secret, secret != null)
            this.rights[id] = rights
            return id
        }

        override fun findAccount(accountName: String): StaffAccount? = accounts[accountName.lowercase()]

        override fun rightsOf(accountId: Int): Rights = rights[accountId] ?: Rights.NONE

        override fun enableTwoFactor(accountId: Int, secret: String) {
            enabled[accountId] = secret
        }
    }

    private class FakeTotp : StaffTotp {
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
        const val IP = "1.2.3.4"
    }
}
