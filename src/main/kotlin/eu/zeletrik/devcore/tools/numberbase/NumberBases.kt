package eu.zeletrik.devcore.tools.numberbase

import java.math.BigInteger

private const val BINARY_RADIX = 2
private const val OCTAL_RADIX = 8
private const val DECIMAL_RADIX = 10
private const val HEX_RADIX = 16

/** [radix] null means detect from a `0x`, `0o` or `0b` prefix, else decimal. */
enum class InputBase(val label: String, val radix: Int?) {
	AUTO("Auto", null),
	BINARY("2", BINARY_RADIX),
	OCTAL("8", OCTAL_RADIX),
	DECIMAL("10", DECIMAL_RADIX),
	HEX("16", HEX_RADIX),
}

/** One integer written in the four bases. */
data class Bases(val binary: String, val octal: String, val decimal: String, val hex: String)

/** Integers of any size; `_` and spaces between digits are ignored, and the sign is kept (no two's complement). */
object NumberBases {
	private const val MAX_DIGITS = 10_000
	private val prefixes = mapOf("0x" to HEX_RADIX, "0o" to OCTAL_RADIX, "0b" to BINARY_RADIX)
	private val names = mapOf(
		BINARY_RADIX to "binary", OCTAL_RADIX to "octal", DECIMAL_RADIX to "decimal", HEX_RADIX to "hexadecimal",
	)

	fun convert(text: String, base: InputBase, uppercase: Boolean): Bases {
		var digits = text.trim().replace(Regex("[_\\s]"), "")
		val negative = digits.startsWith("-")
		digits = digits.removePrefix("-").removePrefix("+")
		val prefix = prefixes.entries.firstOrNull { digits.lowercase().startsWith(it.key) }
		val radix = base.radix ?: prefix?.value ?: DECIMAL_RADIX
		if (prefix != null && prefix.value == radix) digits = digits.drop(prefix.key.length)
		require(digits.isNotEmpty()) { "Enter some digits" }
		require(digits.length <= MAX_DIGITS) { "Number too long (max 10,000 digits)" }
		// `digitToIntOrNull` also reads non-ASCII digits (Arabic-Indic, fullwidth), so the range check comes first.
		digits.firstOrNull { it !in '0'..'z' || it.digitToIntOrNull(radix) == null }?.let {
			throw IllegalArgumentException("'$it' is not a valid ${names[radix]} digit")
		}
		val value = BigInteger(digits, radix).let { if (negative) it.negate() else it }
		val hex = value.toString(HEX_RADIX)
		val binary = value.toString(BINARY_RADIX)
		return Bases(binary, value.toString(OCTAL_RADIX), value.toString(), if (uppercase) hex.uppercase() else hex)
	}
}
