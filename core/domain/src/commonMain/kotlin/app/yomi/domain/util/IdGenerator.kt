package app.yomi.domain.util

import kotlin.random.Random

/** Creates the opaque identifiers used by every entity in the app. */
fun interface IdGenerator {
    fun newId(): String
}

/** Random, collision-resistant, sortable enough for debugging. */
class RandomIdGenerator(private val random: Random = Random.Default) : IdGenerator {
    override fun newId(): String {
        val builder = StringBuilder(20)
        repeat(20) { builder.append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        return builder.toString()
    }

    private companion object {
        const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
    }
}

/** Predictable identifiers so tests can assert on them. */
class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var counter = 0
    override fun newId(): String = "$prefix-${++counter}"
}
