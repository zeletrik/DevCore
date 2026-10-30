package eu.zeletrik.devcore.tools.random

import eu.zeletrik.devcore.tools.FieldError
import eu.zeletrik.devcore.tools.secureRandom
import java.math.BigInteger

/** Uniform integers in an inclusive 64-bit range, from the secure source. */
object RandomNumbers {
	fun generate(min: Long, max: Long, count: Int, unique: Boolean): List<Long> {
		if (max < min) throw FieldError("max", "Must be at least the minimum")
		val size = BigInteger.valueOf(max) - BigInteger.valueOf(min) + BigInteger.ONE
		if (unique && size < BigInteger.valueOf(count.toLong())) throw FieldError("count", "Only $size unique numbers in this range")
		if (!unique) return List(count) { draw(min, max) }
		val drawn = LinkedHashSet<Long>()
		while (drawn.size < count) drawn += draw(min, max)
		return drawn.toList()
	}

	/** Inclusive at both ends without overflowing `max + 1`. */
	fun draw(min: Long, max: Long): Long = when {
		min == Long.MIN_VALUE && max == Long.MAX_VALUE -> secureRandom.nextLong()
		max == Long.MAX_VALUE -> secureRandom.nextLong(min - 1, max) + 1
		else -> secureRandom.nextLong(min, max + 1)
	}
}
