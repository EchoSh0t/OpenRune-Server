package org.rsmod.api.totp.google

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import java.time.Instant
import java.util.Date
import org.apache.commons.codec.binary.Base32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleTotpTest {
    private val totp = GoogleTotp()

    @Test
    fun `verifies the RFC 6238 SHA1 vector`() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ".toCharArray()
        assertTrue(totp.verifyCode(secret, "287082", Instant.ofEpochSecond(59)))
        assertTrue(totp.verifyCode(secret, "081804", Instant.ofEpochSecond(1111111109)))
        assertFalse(totp.verifyCode(secret, "287083", Instant.ofEpochSecond(59)))
    }

    @Test
    fun `accepts lowercase, spaced and padded secrets`() {
        val secret = "gezd gnbv gy3t qojq gezd gnbv gy3t qojq====".toCharArray()
        assertTrue(totp.verifyCode(secret, "287082", Instant.ofEpochSecond(59)))
    }

    @Test
    fun `generated secrets are unpadded base32 of 20 bytes`() {
        val secret = String(totp.generateSecret())
        assertEquals(32, secret.length)
        assertTrue(secret.all { it in 'A'..'Z' || it in '2'..'7' }) { secret }
        assertEquals(20, Base32().decode(secret).size)
    }

    @Test
    fun `generated secrets verify codes from a standard authenticator`() {
        val secret = totp.generateSecret()
        val now = Instant.ofEpochSecond(1_800_000_000)
        val appCode = GoogleAuthenticator(String(secret).toByteArray()).generate(Date.from(now))
        assertTrue(totp.verifyCode(secret, appCode, now))
    }

    @Test
    fun `empty secret never verifies`() {
        assertFalse(totp.verifyCode(CharArray(0), "000000", Instant.ofEpochSecond(59)))
    }
}
