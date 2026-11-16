package org.rsmod.content.other.websiteapi

import java.util.ArrayDeque

class RateLimiter(
    private val limit: Int,
    private val windowMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val hits = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun isLimited(key: String): Boolean {
        val queue = hits[key] ?: return false
        prune(queue, clock())
        if (queue.isEmpty()) {
            hits.remove(key)
        }
        return queue.size >= limit
    }

    @Synchronized
    fun record(key: String) {
        val now = clock()
        val queue = hits.getOrPut(key, ::ArrayDeque)
        prune(queue, now)
        queue.addLast(now)
        if (hits.size > MAX_TRACKED_KEYS) {
            hits.entries.removeIf { (_, times) -> times.apply { prune(this, now) }.isEmpty() }
        }
    }

    private fun prune(queue: ArrayDeque<Long>, now: Long) {
        while (queue.isNotEmpty() && now - queue.first() >= windowMillis) {
            queue.removeFirst()
        }
    }

    private companion object {
        const val MAX_TRACKED_KEYS = 10_000
    }
}
