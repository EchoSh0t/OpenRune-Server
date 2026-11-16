package org.rsmod.api.totp.google

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import dev.turingcomplete.kotlinonetimepassword.RandomSecretGenerator
import java.time.Instant
import java.util.Date
import org.apache.commons.codec.binary.Base32
import org.rsmod.api.totp.Totp

public class GoogleTotp : Totp {
    override fun verifyCode(secret: CharArray, code: String, now: Instant): Boolean {
        val normalized = normalize(secret)
        if (normalized.isEmpty()) {
            return false
        }
        val authenticator = GoogleAuthenticator(normalized)
        normalized.fill(0)
        return authenticator.isValid(code, Date.from(now))
    }

    /** Standard unpadded base32, the form authenticator apps and Laravel Fortify use. */
    override fun generateSecret(): CharArray {
        val raw = RandomSecretGenerator().createRandomSecret(SECRET_BYTES)
        val base32 = Base32().encodeToString(raw).trimEnd('=')
        raw.fill(0)
        return base32.toCharArray()
    }

    private fun normalize(secret: CharArray): ByteArray {
        val out = ByteArray(secret.size)
        var length = 0
        for (c in secret) {
            if (c.isWhitespace() || c == '=' || c == '-') continue
            out[length++] = c.uppercaseChar().code.toByte()
        }
        return out.copyOf(length).also { out.fill(0) }
    }

    private companion object {
        const val SECRET_BYTES = 20
    }
}
