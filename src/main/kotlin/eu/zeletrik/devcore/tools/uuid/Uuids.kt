package eu.zeletrik.devcore.tools.uuid

import eu.zeletrik.devcore.tools.secureRandom
import java.math.BigInteger
import java.util.UUID

enum class IdVersion(val label: String) { V4("v4"), V7("v7"), ULID("ULID") }

object Uuids {
	private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

	/** 26 Crockford Base32 characters: 48-bit Unix milliseconds, then 80 random bits; always uppercase. */
	fun ulid(epochMillis: Long): String {
		val bytes = ByteArray(6) { (epochMillis ushr (40 - 8 * it)).toByte() } + ByteArray(10).also(secureRandom::nextBytes)
		val value = BigInteger(1, bytes)
		return String(CharArray(26) { i -> CROCKFORD[value.shiftRight(5 * (25 - i)).toInt() and 31] })
	}

	// NOTE: replace with UUID.ofEpochMillis when the toolchain reaches JDK 27.
	/** RFC 9562 version 7: 48-bit Unix ms, version 7, 12 random bits, variant 10, 62 random bits. */
	fun v7(epochMillis: Long): UUID {
		val msb = (epochMillis shl 16) or 0x7000L or (secureRandom.nextLong() and 0x0FFFL)
		val lsb = (secureRandom.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL) or Long.MIN_VALUE
		return UUID(msb, lsb)
	}

	fun generate(version: IdVersion, count: Int, epochMillis: Long, uppercase: Boolean, hyphens: Boolean): String =
		(1..count).joinToString("\n") {
			if (version == IdVersion.ULID) return@joinToString ulid(epochMillis)
			val text = (if (version == IdVersion.V7) v7(epochMillis) else UUID.randomUUID()).toString()
			val plain = if (hyphens) text else text.replace("-", "")
			if (uppercase) plain.uppercase() else plain
		}
}
