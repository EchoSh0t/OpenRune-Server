package org.rsmod.api.webauth

import java.util.concurrent.ConcurrentHashMap

/**
 * Accepts each authenticator code at most once per account. A TOTP code stays valid for its whole
 * 30-second step, so without this a code read over someone's shoulder could be replayed.
 */
public class TotpReplayGuard(
    private val windowMillis: Long = 2 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val used = ConcurrentHashMap<String, Long>()

    /**
     * Returns true and remembers the code when it hasn't been used for [accountId] recently and
     * [verify] accepts it.
     */
    public fun accept(accountId: Int, code: String, verify: (String) -> Boolean): Boolean {
        val digits = normalize(code) ?: return false
        val now = clock()
        used.entries.removeIf { now - it.value > windowMillis }
        val key = "$accountId:$digits"
        if (used.containsKey(key) || !verify(digits)) {
            return false
        }
        used[key] = now
        return true
    }

    public companion object {
        private val CODE = Regex("^[0-9]{6}$")

        /** Strips spaces from a typed code; null when what's left isn't six digits. */
        public fun normalize(code: String): String? =
            code.filter { !it.isWhitespace() }.takeIf(CODE::matches)
    }
}
