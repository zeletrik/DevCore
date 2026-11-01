package eu.zeletrik.devcore.tools.uuid

import eu.zeletrik.devcore.tools.secureRandom
import java.math.BigInteger
import java.util.UUID

/** The id kinds offered. */
enum class IdVersion(val label: String) { V4("v4"), V7("v7"), ULID("ULID") }

/** Random, time-ordered and ULID ids, drawn from [secureRandom]. */
object Uuids {
	private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
	private const val ULID_LENGTH = 26
	private const val BITS_PER_CHARACTER = 5
	private const val CHARACTER_MASK = (1 shl BITS_PER_CHARACTER) - 1
	private const val TIME_BYTES = 6
	private const val RANDOM_BYTES = 10

	/** 26 Crockford Base32 characters: 48-bit Unix milliseconds, then 80 random bits; always uppercase. */
	fun ulid(epochMillis: Long): String {
		val time = ByteArray(TIME_BYTES) { (epochMillis ushr (Byte.SIZE_BITS * (TIME_BYTES - 1 - it))).toByte() }
		val value = BigInteger(1, time + ByteArray(RANDOM_BYTES).also(secureRandom::nextBytes))
		return String(
			CharArray(ULID_LENGTH) { i ->
				CROCKFORD[value.shiftRight(BITS_PER_CHARACTER * (ULID_LENGTH - 1 - i)).toInt() and CHARACTER_MASK]
			},
		)
	}

	// NOTE: replace with UUID.ofEpochMillis when the toolchain reaches JDK 27.
	/** RFC 9562 version 7: 48-bit Unix ms, version 7, 12 random bits, variant 10, 62 random bits. */
	fun v7(epochMillis: Long): UUID {
		val msb = (epochMillis shl 16) or 0x7000L or (secureRandom.nextLong() and 0x0FFFL)
		val lsb = (secureRandom.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL) or Long.MIN_VALUE
		return UUID(msb, lsb)
	}

	/** [count] ids on one line each; every id in the batch carries the same [epochMillis]. */
	fun generate(version: IdVersion, count: Int, epochMillis: Long, uppercase: Boolean, hyphens: Boolean): String =
		List(count) {
			when (version) {
				IdVersion.ULID -> ulid(epochMillis)
				IdVersion.V7 -> text(v7(epochMillis), uppercase, hyphens)
				IdVersion.V4 -> text(UUID.randomUUID(), uppercase, hyphens)
			}
		}.joinToString("\n")

	private fun text(uuid: UUID, uppercase: Boolean, hyphens: Boolean): String {
		val plain = uuid.toString().let { if (hyphens) it else it.replace("-", "") }
		return if (uppercase) plain.uppercase() else plain
	}
}
