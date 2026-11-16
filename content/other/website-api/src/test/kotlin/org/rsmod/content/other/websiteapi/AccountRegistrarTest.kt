package org.rsmod.content.other.websiteapi

import dev.or2.central.account.AccountNameAuthPolicy
import dev.or2.central.auth.CompositePasswordHasher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccountRegistrarTest {
    private val hasher = CompositePasswordHasher("bcrypt", bcryptCost = 4)
    private val store = MemoryStore()
    private val registrar = AccountRegistrar(store, { hasher }, { setOf("badword") })

    private fun register(username: String, password: String = PASSWORD, email: String? = null) =
        registrar.register(RegisterRequest(username, password, email))

    @Test
    fun `valid registration stores a hash that verifies with central's hasher`() {
        val result = register("Zezima")

        assertTrue(result.ok)
        assertEquals("Zezima", result.accountName)
        val stored = store.accounts.getValue("zezima")
        assertTrue(stored.startsWith("\$2"))
        assertTrue(hasher.verify(stored, PASSWORD))
        assertFalse(hasher.verify(stored, "$PASSWORD!"))
    }

    @Test
    fun `whitespace is collapsed before storing`() {
        val result = register("  Iron   Man  ")

        assertTrue(result.ok)
        assertEquals("Iron Man", result.accountName)
    }

    @Test
    fun `names with symbols or unicode are rejected`() {
        for (name in listOf("bad_name", "dash-name", "dots.name", "café", "", "   ")) {
            val result = register(name)
            assertFalse(result.ok, name)
            assertEquals(ErrorCode.NAME_INVALID, result.code, name)
        }
        assertTrue(store.accounts.isEmpty())
    }

    @Test
    fun `names over twelve characters are rejected`() {
        assertEquals(ErrorCode.NAME_INVALID, register("abcdefghijklm").code)
        assertTrue(register("abcdefghijkl").ok)
    }

    @Test
    fun `profanity and staff-looking names are rejected`() {
        assertEquals(ErrorCode.NAME_INVALID, register("xbadwordx").code)
        assertEquals(ErrorCode.NAME_INVALID, register("Mod Steve").code)
    }

    @Test
    fun `names that only differ by case or spacing collide`() {
        assertTrue(register("Iron Man").ok)

        assertEquals(ErrorCode.NAME_TAKEN, register("iron man").code)
        assertEquals(ErrorCode.NAME_TAKEN, register("IronMan").code)
        assertEquals(1, store.accounts.size)
    }

    @Test
    fun `losing the insert race reports name taken`() {
        store.failNextInsert = true

        val result = register("Racer")

        assertFalse(result.ok)
        assertEquals(ErrorCode.NAME_TAKEN, result.code)
    }

    @Test
    fun `concurrent registrations of the same name create exactly one account`() {
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val results = (0 until threads).map {
            pool.submit<RegisterResult> {
                start.await()
                register("Speedy")
            }
        }
        start.countDown()
        val outcomes = results.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, outcomes.count { it.ok })
        assertTrue(outcomes.filterNot { it.ok }.all { it.code == ErrorCode.NAME_TAKEN })
        assertEquals(1, store.accounts.size)
    }

    @Test
    fun `password rules are enforced`() {
        assertEquals(ErrorCode.PASSWORD_WEAK, register("Alice", "short").code)
        assertEquals(ErrorCode.PASSWORD_WEAK, register("Alice", "a".repeat(65)).code)
        assertEquals(ErrorCode.PASSWORD_WEAK, register("Alice", "         ").code)
        assertEquals(ErrorCode.PASSWORD_WEAK, register("Alice Smith", "alice smith").code)
        assertTrue(store.accounts.isEmpty())
        assertTrue(register("Alice", "a".repeat(64)).ok)
    }

    @Test
    fun `email is optional but validated when given`() {
        assertEquals(ErrorCode.EMAIL_INVALID, register("Bob", email = "not-an-email").code)
        assertTrue(register("Bob", email = "  ").ok)
        assertTrue(register("Carol", email = "carol@example.com").ok)
        assertEquals("carol@example.com", store.emails["carol"])
    }

    private class MemoryStore : AccountStore {
        val accounts = ConcurrentHashMap<String, String>()
        val emails = ConcurrentHashMap<String, String>()

        @Volatile var failNextInsert = false

        override fun collisionKeyTaken(collisionKey: String): Boolean =
            accounts.keys.any { AccountNameAuthPolicy.collisionKey(it) == collisionKey }

        override fun insertIfAbsent(accountName: String, passwordHash: String, email: String?): Boolean {
            if (failNextInsert) {
                failNextInsert = false
                return false
            }
            val key = accountName.lowercase()
            val inserted = accounts.putIfAbsent(key, passwordHash) == null
            if (inserted && email != null) {
                emails[key] = email
            }
            return inserted
        }
    }

    private companion object {
        const val PASSWORD = "correct horse battery"
    }
}
