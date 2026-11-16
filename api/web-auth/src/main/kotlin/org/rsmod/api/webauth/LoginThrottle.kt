package org.rsmod.api.webauth

import java.util.concurrent.ConcurrentHashMap

/**
 * Counts failed sign-in attempts per key (an account name or a client address) and locks a key
 * out once it reaches [maxFailures] inside [windowMillis]. The window starts at the first failure,
 * so a lockout always ends [windowMillis] after it began.
 */
public class LoginThrottle(
    private val maxFailures: Int = 5,
    private val windowMillis: Long = 15 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val failures = ConcurrentHashMap<String, Failures>()

    /** Milliseconds until the longest lockout among [keys] ends, or 0 when none is locked. */
    public fun lockedFor(keys: List<String>): Long {
        val now = clock()
        return keys.maxOfOrNull { remaining(it, now) } ?: 0
    }

    public fun recordFailure(keys: List<String>) {
        val now = clock()
        if (failures.size > MAX_TRACKED) {
            failures.values.removeIf { now - it.windowStart > windowMillis }
        }
        for (key in keys) {
            failures.compute(key) { _, entry ->
                if (entry == null || now - entry.windowStart > windowMillis) {
                    Failures(1, now)
                } else {
                    entry.copy(count = entry.count + 1)
                }
            }
        }
    }

    public fun clear(keys: List<String>) {
        keys.forEach(failures::remove)
    }

    private fun remaining(key: String, now: Long): Long {
        val entry = failures[key] ?: return 0
        if (now - entry.windowStart > windowMillis) {
            failures.remove(key)
            return 0
        }
        return if (entry.count >= maxFailures) windowMillis - (now - entry.windowStart) else 0
    }

    private data class Failures(val count: Int, val windowStart: Long)

    public companion object {
        private const val MAX_TRACKED = 10_000

        public fun keys(accountName: String, client: String): List<String> =
            listOf("user:${accountName.trim().lowercase()}", "addr:$client")
    }
}
