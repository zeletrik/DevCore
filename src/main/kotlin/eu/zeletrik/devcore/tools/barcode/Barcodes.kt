package eu.zeletrik.devcore.tools.barcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/** The barcode formats offered; [digits] is a GS1 format's payload length, alone or with its check digit. */
enum class Symbology(val label: String, val format: BarcodeFormat, val digits: IntRange?) {
	CODE_128("Code 128", BarcodeFormat.CODE_128, null),
	EAN_13("EAN-13", BarcodeFormat.EAN_13, digits = 12..13),
	EAN_8("EAN-8", BarcodeFormat.EAN_8, digits = 7..8),
	UPC_A("UPC-A", BarcodeFormat.UPC_A, digits = 11..12),
	CODE_39("Code 39", BarcodeFormat.CODE_39, null),
}

/** Validates against each format's rules first: ZXing alone is laxer (Code 39 takes `x`, Code 128 takes a tab). */
object Barcodes {
	private const val CODE_39_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -.$/+%"
	private const val GS1_WEIGHT = 3
	private const val GS1_MODULUS = 10

	/**
	 * Code 128 and Code 39 have no length limit of their own, but scanners rarely read past this; ZXing caps Code 39
	 * here, and its Code 128 checksum overflows past about 7,000 characters, where the image also runs to megapixels.
	 */
	private const val MAX_CHARS = 80

	/**
	 * ZXing splits the margin between both sides: 11 modules each, enough for every symbology here (EAN-13's left side).
	 */
	private val quietZone = mapOf(EncodeHintType.MARGIN to 22)

	/** The value actually encoded (with its check digit) and its bars. */
	fun encode(input: String, symbology: Symbology): Pair<String, BitMatrix> {
		val value = when (symbology) {
			Symbology.CODE_128 -> input.also { text ->
				require(text.length <= MAX_CHARS) { "Code 128 takes at most 80 characters" }
				text.firstOrNull { it.code !in ' '.code..'~'.code }
					?.let { throw IllegalArgumentException("Code 128 takes only ASCII characters 32–126 (space to ~)") }
			}

			Symbology.CODE_39 -> input.also { text ->
				require(text.length <= MAX_CHARS) { "Code 39 takes at most 80 characters" }
				text.firstOrNull { it !in CODE_39_SET }
					?.let { throw IllegalArgumentException("Code 39 can't encode '$it'") }
			}

			else -> withCheckDigit(input, symbology)
		}
		return value to MultiFormatWriter().encode(value, symbology.format, 0, 1, quietZone)
	}

	private fun withCheckDigit(input: String, symbology: Symbology): String {
		val range = symbology.digits!!
		require(input.all { it in '0'..'9' } && input.length in range) {
			"${symbology.label} needs ${range.first} or ${range.last} digits"
		}
		val payload = input.take(range.first)
		val check = checkDigit(payload)
		if (input.length == range.last && input.last().digitToInt() != check) {
			throw IllegalArgumentException("Check digit should be $check")
		}
		return payload + check
	}

	/** GS1: weights 3, 1, 3, … from the rightmost payload digit; the check digit tops the sum up to a multiple of 10. */
	private fun checkDigit(payload: String): Int {
		val sum = payload.reversed().mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) GS1_WEIGHT else 1 }.sum()
		return (GS1_MODULUS - sum % GS1_MODULUS) % GS1_MODULUS
	}
}
