package com.joaoeoneves.fintrack.data.local

import java.security.SecureRandom
import javax.inject.Inject

private const val ID_LENGTH = 20
private const val ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

/** Generates record ids, injectable so tests can make them deterministic. */
fun interface IdGenerator {
    fun newId(): String
}

/** 20-character `[A-Za-z0-9]` ids (the same shape as Firestore auto-ids). */
class RandomIdGenerator
    @Inject
    constructor() : IdGenerator {
        private val random = SecureRandom()

        override fun newId(): String =
            buildString(ID_LENGTH) {
                repeat(ID_LENGTH) { append(ID_ALPHABET[random.nextInt(ID_ALPHABET.length)]) }
            }
    }
