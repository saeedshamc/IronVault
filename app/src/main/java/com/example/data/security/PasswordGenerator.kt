package com.example.data.security

import java.security.SecureRandom
import kotlin.math.log2

object PasswordGenerator {

    private val secureRandom = SecureRandom()

    private val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val NUMBERS = "0123456789"
    private val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:',.?/~"

    private val AMBIGUOUS_CHARS = setOf('l', '1', 'I', 'O', '0', 'o', 'i')

    private val CONSONANTS = "bcdfghjklmnpqrstvwxyz"
    private val VOWELS = "aeiou"

    /**
     * Generates a fully random password based on the supplied parameters.
     */
    fun generateRandom(
        length: Int,
        includeUpper: Boolean,
        includeLower: Boolean,
        includeNumbers: Boolean,
        includeSymbols: Boolean,
        excludeAmbiguous: Boolean
    ): String {
        val poolBuilder = StringBuilder()
        
        if (includeLower) poolBuilder.append(LOWERCASE)
        if (includeUpper) poolBuilder.append(UPPERCASE)
        if (includeNumbers) poolBuilder.append(NUMBERS)
        if (includeSymbols) poolBuilder.append(SYMBOLS)

        var pool = poolBuilder.toString()
        if (excludeAmbiguous) {
            pool = pool.filter { !AMBIGUOUS_CHARS.contains(it) }
        }

        if (pool.isEmpty()) return ""

        val password = StringBuilder(length)
        
        // Guarantee at least one of each selected charset is added first to prevent weak configurations
        val guaranteed = mutableListOf<Char>()
        if (includeLower) guaranteed.add(getRandomChar(if (excludeAmbiguous) LOWERCASE.filter { !AMBIGUOUS_CHARS.contains(it) } else LOWERCASE))
        if (includeUpper) guaranteed.add(getRandomChar(if (excludeAmbiguous) UPPERCASE.filter { !AMBIGUOUS_CHARS.contains(it) } else UPPERCASE))
        if (includeNumbers) guaranteed.add(getRandomChar(if (excludeAmbiguous) NUMBERS.filter { !AMBIGUOUS_CHARS.contains(it) } else NUMBERS))
        if (includeSymbols) guaranteed.add(getRandomChar(if (excludeAmbiguous) SYMBOLS.filter { !AMBIGUOUS_CHARS.contains(it) } else SYMBOLS))

        guaranteed.filter { it != '\u0000' }.forEach { password.append(it) }

        while (password.length < length) {
            val nextChar = pool[secureRandom.nextInt(pool.length)]
            password.append(nextChar)
        }

        // Shuffle the characters so the guaranteed ones aren't always at the front
        val chars = password.toString().toCharArray().toList().shuffled(secureRandom)
        return chars.joinToString("")
    }

    private fun getRandomChar(source: String): Char {
        if (source.isEmpty()) return '\u0000'
        return source[secureRandom.nextInt(source.length)]
    }

    /**
     * Generates a pronounceable, memorable password (e.g. "kavoki-mitedi-62") using VCV groupings.
     */
    fun generatePronounceable(length: Int, includeNumbers: Boolean): String {
        val builder = StringBuilder()
        var useConsonant = true
        
        val actualLength = if (includeNumbers) length - 3 else length

        for (i in 0 until actualLength) {
            if (i > 0 && i % 4 == 0) {
                builder.append("-")
            } else {
                if (useConsonant) {
                    builder.append(CONSONANTS[secureRandom.nextInt(CONSONANTS.length)])
                } else {
                    builder.append(VOWELS[secureRandom.nextInt(VOWELS.length)])
                }
                useConsonant = !useConsonant
            }
        }

        if (includeNumbers) {
            builder.append("-")
            builder.append(NUMBERS[secureRandom.nextInt(NUMBERS.length)])
            builder.append(NUMBERS[secureRandom.nextInt(NUMBERS.length)])
        }

        return builder.toString().take(length)
    }

    /**
     * Estimates the Shannon entropy of a password and maps it to strength ratings.
     */
    fun estimateStrength(
        password: String,
        includeUpper: Boolean,
        includeLower: Boolean,
        includeNumbers: Boolean,
        includeSymbols: Boolean
    ): StrengthResult {
        if (password.isEmpty()) return StrengthResult(0.0, PasswordStrength.WEAK)

        // Count unique pools utilized
        var poolSize = 0
        if (includeLower && password.any { LOWERCASE.contains(it) }) poolSize += 26
        if (includeUpper && password.any { UPPERCASE.contains(it) }) poolSize += 26
        if (includeNumbers && password.any { NUMBERS.contains(it) }) poolSize += 10
        if (includeSymbols && password.any { SYMBOLS.contains(it) }) poolSize += SYMBOLS.length

        if (poolSize == 0) {
            // Fallback for custom entries
            if (password.any { it.isLowerCase() }) poolSize += 26
            if (password.any { it.isUpperCase() }) poolSize += 26
            if (password.any { it.isDigit() }) poolSize += 10
            if (password.any { !it.isLetterOrDigit() }) poolSize += 20
        }

        val entropy = password.length * log2(poolSize.toDouble().coerceAtLeast(2.0))
        
        val strength = when {
            entropy < 35 -> PasswordStrength.WEAK
            entropy < 60 -> PasswordStrength.MEDIUM
            entropy < 85 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }

        return StrengthResult(entropy, strength)
    }

    data class StrengthResult(
        val entropy: Double,
        val strength: PasswordStrength
    )

    enum class PasswordStrength {
        WEAK, MEDIUM, STRONG, VERY_STRONG
    }
}
