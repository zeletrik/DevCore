package eu.zeletrik.devcore.tools.datasize

import java.math.BigDecimal
import java.math.RoundingMode

/** Each unit's size in bits; decimal units are powers of 1000, binary ones powers of 1024. */
enum class DataUnit(val label: String, val bits: BigDecimal) {
	BIT("bit", BigDecimal.ONE), KBIT("kbit", 1000.bits()), MBIT("Mbit", 1000.pow(2).bits()), GBIT("Gbit", 1000.pow(3).bits()), TBIT("Tbit", 1000.pow(4).bits()),
	B("B", 8.bits()), KB("kB", (1000 * 8).bits()), MB("MB", (1000.pow(2) * 8).bits()), GB("GB", (1000.pow(3) * 8).bits()),
	TB("TB", (1000.pow(4) * 8).bits()), PB("PB", (1000.pow(5) * 8).bits()),
	KIB("KiB", (1024 * 8).bits()), MIB("MiB", (1024.pow(2) * 8).bits()), GIB("GiB", (1024.pow(3) * 8).bits()),
	TIB("TiB", (1024.pow(4) * 8).bits()), PIB("PiB", (1024.pow(5) * 8).bits()),
}

private fun Int.pow(n: Int): Long = (1..n).fold(1L) { acc, _ -> acc * this }

private fun Int.bits() = BigDecimal(this)

private fun Long.bits() = BigDecimal(this)

object DataSizes {
	/** One `<value> <unit>` line per unit: exact, rounded half-even to at most 6 decimals, plain notation. */
	fun convert(text: String, unit: DataUnit): String {
		val value = text.trim().toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
			?: throw IllegalArgumentException("Enter a number of 0 or more")
		val bits = value * unit.bits
		return DataUnit.entries.joinToString("\n") { target ->
			val amount = bits.divide(target.bits, 6, RoundingMode.HALF_EVEN).stripTrailingZeros()
			"${if (amount.signum() == 0) "0" else amount.toPlainString()} ${target.label}"
		}
	}
}
