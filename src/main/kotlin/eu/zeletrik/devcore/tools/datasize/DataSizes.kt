package eu.zeletrik.devcore.tools.datasize

import java.math.BigDecimal
import java.math.RoundingMode

private const val DECIMAL = 1000
private const val BINARY = 1024

/** Each unit's size in bits: [base] to the [power], times 8 for byte units. */
enum class DataUnit(val label: String, base: Int, power: Int, bytes: Boolean) {
	BIT("bit", DECIMAL, power = 0, bytes = false),
	KBIT("kbit", DECIMAL, power = 1, bytes = false),
	MBIT("Mbit", DECIMAL, power = 2, bytes = false),
	GBIT("Gbit", DECIMAL, power = 3, bytes = false),
	TBIT("Tbit", DECIMAL, power = 4, bytes = false),
	B("B", DECIMAL, power = 0, bytes = true),
	KB("kB", DECIMAL, power = 1, bytes = true),
	MB("MB", DECIMAL, power = 2, bytes = true),
	GB("GB", DECIMAL, power = 3, bytes = true),
	TB("TB", DECIMAL, power = 4, bytes = true),
	PB("PB", DECIMAL, power = 5, bytes = true),
	KIB("KiB", BINARY, power = 1, bytes = true),
	MIB("MiB", BINARY, power = 2, bytes = true),
	GIB("GiB", BINARY, power = 3, bytes = true),
	TIB("TiB", BINARY, power = 4, bytes = true),
	PIB("PiB", BINARY, power = 5, bytes = true),
	;

	val bits: BigDecimal = BigDecimal(base).pow(power) * if (bytes) BigDecimal(Byte.SIZE_BITS) else BigDecimal.ONE
}

private const val MAX_DIGITS = 30
private const val DECIMALS = 6

object DataSizes {
	/** One `<value> <unit>` line per unit: exact, rounded half-even to at most 6 decimals, plain notation. */
	fun convert(text: String, unit: DataUnit): String {
		val value = text.trim().toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
			?: throw IllegalArgumentException("Enter a number of 0 or more")
		// BigDecimal would build 10^exponent in full, so absurd magnitudes and precisions stop here.
		require(value.precision() - value.scale() <= MAX_DIGITS && value.scale() <= MAX_DIGITS) { "Value out of range" }
		val bits = value * unit.bits
		return DataUnit.entries.joinToString("\n") { target ->
			val amount = bits.divide(target.bits, DECIMALS, RoundingMode.HALF_EVEN).stripTrailingZeros()
			"${if (amount.signum() == 0) "0" else amount.toPlainString()} ${target.label}"
		}
	}
}
