package org.rsmod.content.other.websiteapi

import dev.or2.central.account.AccountNameAuthPolicy
import dev.or2.central.account.AccountNameWorldLinkPrecheck
import dev.or2.central.account.AccountNameWorldLinkPrecheckResult
import dev.or2.central.auth.PasswordHasher

interface AccountStore {
    fun collisionKeyTaken(collisionKey: String): Boolean

    fun insertIfAbsent(accountName: String, passwordHash: String, email: String?): Boolean
}

class AccountRegistrar(
    private val store: AccountStore,
    private val hasher: () -> PasswordHasher,
    private val badWordRoots: () -> Set<String>,
) {
    fun register(request: RegisterRequest): RegisterResult {
        val accountName =
            when (val name = validateName(request.username)) {
                is NameCheck.Ok -> name.canonical
                is NameCheck.Invalid -> return reject(ErrorCode.NAME_INVALID, name.message)
            }
        passwordProblem(accountName, request.password)?.let {
            return reject(ErrorCode.PASSWORD_WEAK, it)
        }
        val email = request.email?.trim()?.ifEmpty { null }
        if (email != null && !isValidEmail(email)) {
            return reject(ErrorCode.EMAIL_INVALID, "That email address doesn't look right.")
        }
        val collisionKey = AccountNameAuthPolicy.collisionKey(accountName)
        if (store.collisionKeyTaken(collisionKey)) {
            return nameTaken()
        }
        val hash = hasher().hash(request.password)
        if (!store.insertIfAbsent(accountName, hash, email)) {
            return nameTaken()
        }
        return RegisterResult(ok = true, accountName = accountName, message = "Account created.")
    }

    private fun validateName(raw: String): NameCheck {
        val collapsed = raw.trim().replace(WHITESPACE, " ")
        if (!ASCII_NAME.matches(collapsed)) {
            return if (collapsed.isEmpty()) {
                NameCheck.Invalid("Enter a username.")
            } else {
                NameCheck.Invalid(INVALID_CHARACTERS)
            }
        }
        val result = AccountNameWorldLinkPrecheck.evaluateRawUsernameForLogin(collapsed, badWordRoots())
        return when (result) {
            is AccountNameWorldLinkPrecheckResult.Ok ->
                if (result.canonical == collapsed) {
                    NameCheck.Ok(result.canonical)
                } else {
                    NameCheck.Invalid(INVALID_CHARACTERS)
                }
            AccountNameWorldLinkPrecheckResult.BadFrame -> NameCheck.Invalid("Enter a username.")
            is AccountNameWorldLinkPrecheckResult.Policy -> {
                val (first, second) = AccountNameAuthPolicy.policyScriptLines(result.reason)
                NameCheck.Invalid(listOf(first, second).filter(String::isNotBlank).joinToString(" "))
            }
        }
    }

    private fun isValidEmail(email: String): Boolean =
        email.length <= MAX_EMAIL_LENGTH && EMAIL.matches(email)

    private fun nameTaken() =
        reject(ErrorCode.NAME_TAKEN, "That username is already taken. Try another one.")

    private fun reject(code: String, message: String) =
        RegisterResult(ok = false, code = code, message = message)

    private sealed class NameCheck {
        data class Ok(val canonical: String) : NameCheck()

        data class Invalid(val message: String) : NameCheck()
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 64

        fun passwordProblem(accountName: String, password: String): String? =
            when {
                password.length < MIN_PASSWORD_LENGTH ->
                    "Passwords must be at least $MIN_PASSWORD_LENGTH characters."
                password.length > MAX_PASSWORD_LENGTH ->
                    "Passwords can be at most $MAX_PASSWORD_LENGTH characters."
                password.isBlank() -> "Passwords can't be only spaces."
                password.trim().equals(accountName, ignoreCase = true) ->
                    "Your password can't be the same as your username."
                else -> null
            }
        private const val MAX_EMAIL_LENGTH = 254
        private const val INVALID_CHARACTERS = "Usernames can only use letters, numbers and spaces."
        private val WHITESPACE = Regex("\\s+")
        private val ASCII_NAME = Regex("^[A-Za-z0-9 ]+$")
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
