package org.rsmod.api.webauth

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

public object WebTokens {
    private const val TOKEN_BYTES = 32
    private val random = SecureRandom()

    public fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** The `otpauth://` link an authenticator app reads from a QR code. */
    public fun otpauthUri(issuer: String, accountName: String, secret: String): String {
        val label = encode("$issuer:$accountName")
        return "otpauth://totp/$label?secret=$secret&issuer=${encode(issuer)}" +
            "&algorithm=SHA1&digits=6&period=30"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
