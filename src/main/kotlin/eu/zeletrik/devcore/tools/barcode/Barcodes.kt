package eu.zeletrik.devcore.tools.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.Code128Writer
import com.google.zxing.oned.Code39Writer
import com.google.zxing.oned.EAN13Writer
import com.google.zxing.oned.EAN8Writer
import com.google.zxing.oned.UPCAWriter

enum class Symbology(val label: String, val digits: IntRange?) {
	CODE_128("Code 128", null), EAN_13("EAN-13", 12..13), EAN_8("EAN-8", 7..8), UPC_A("UPC-A", 11..12), CODE_39("Code 39", null),
}

/** Validates against each format's rules first: ZXing alone is laxer (Code 39 takes `x`, Code 128 takes a tab). */
object Barcodes {
	private const val CODE_39_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -.$/+%"

	/** The value actually encoded (with its check digit) and its bars. */
	fun encode(input: String, symbology: Symbology): Pair<String, BitMatrix> {
		val value = when (symbology) {
			Symbology.CODE_128 -> input.also { text ->
				text.firstOrNull { it.code !in 32..126 }?.let { throw IllegalArgumentException("Code 128 takes only ASCII characters 32–126 (space to ~)") }
			}
			Symbology.CODE_39 -> input.also { text ->
				text.firstOrNull { it !in CODE_39_SET }?.let { throw IllegalArgumentException("Code 39 can't encode '$it'") }
			}
			else -> withCheckDigit(input, symbology)
		}
		val matrix = when (symbology) {
			Symbology.CODE_128 -> Code128Writer().encode(value, BarcodeFormat.CODE_128, 0, 1)
			Symbology.CODE_39 -> Code39Writer().encode(value, BarcodeFormat.CODE_39, 0, 1)
			Symbology.EAN_13 -> EAN13Writer().encode(value, BarcodeFormat.EAN_13, 0, 1)
			Symbology.EAN_8 -> EAN8Writer().encode(value, BarcodeFormat.EAN_8, 0, 1)
			Symbology.UPC_A -> UPCAWriter().encode(value, BarcodeFormat.UPC_A, 0, 1)
		}
		return value to matrix
	}

	private fun withCheckDigit(input: String, symbology: Symbology): String {
		val range = symbology.digits!!
		require(input.all { it in '0'..'9' } && input.length in range) { "${symbology.label} needs ${range.first} or ${range.last} digits" }
		val payload = input.take(range.first)
		val check = checkDigit(payload)
		if (input.length == range.last && input.last().digitToInt() != check) throw IllegalArgumentException("Check digit should be $check")
		return payload + check
	}

	/** GS1: weights 3, 1, 3, … from the rightmost payload digit. */
	fun checkDigit(payload: String): Int {
		val sum = payload.reversed().mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) 3 else 1 }.sum()
		return (10 - sum % 10) % 10
	}
}
