package com.grandsphere.overwatch.domain.security

import java.security.MessageDigest
import java.security.SecureRandom

object PinHasher {
    fun hash(pin: String, saltHex: String? = null): String {
        val salt = saltHex ?: randomSalt()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest((salt + pin).toByteArray())
        return salt + ":" + hashed.joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val parts = stored.split(":", limit = 2)
        if (parts.size != 2) return false
        return hash(pin, parts[0]) == stored
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
