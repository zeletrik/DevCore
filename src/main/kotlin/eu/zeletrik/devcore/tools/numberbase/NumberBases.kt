package eu.zeletrik.devcore.tools.numberbase

import java.math.BigInteger

enum class InputBase(val label: String, val radix: Int?) { AUTO("Auto", null), BINARY("2", 2), OCTAL("8", 8), DECIMAL("10", 10), HEX("16", 16) }

data class Bases(val binary: String, val octal: String, val decimal: String, val hex: String)

/** Integers of any size; `_` and spaces between digits are ignored, and the sign is kept (no two's complement). */
object NumberBases {
	private val prefixes = mapOf("0x" to 16, "0o" to 8, "0b" to 2)
	private val names = mapOf(2 to "binary", 8 to "octal", 10 to "decimal", 16 to "hexadecimal")

	fun convert(text: String, base: InputBase, uppercase: Boolean): Bases {
		var digits = text.trim().replace(Regex("[_\\s]"), "")
		val negative = digits.startsWith("-")
		digits = digits.removePrefix("-").removePrefix("+")
		val prefix = prefixes.entries.firstOrNull { digits.lowercase().startsWith(it.key) }
		val radix = base.radix ?: prefix?.value ?: 10
		if (prefix != null && prefix.value == radix) digits = digits.drop(2)
		require(digits.isNotEmpty()) { "Enter some digits" }
		digits.firstOrNull { (it.digitToIntOrNull(36) ?: 99) >= radix || it.code > 127 }?.let {
			throw IllegalArgumentException("'$it' is not a valid ${names[radix]} digit")
		}
		val value = BigInteger(digits, radix).let { if (negative) it.negate() else it }
		val hex = value.toString(16)
		return Bases(value.toString(2), value.toString(8), value.toString(), if (uppercase) hex.uppercase() else hex)
	}
}
