package eu.zeletrik.devcore.tools.barcode

import eu.zeletrik.devcore.tools.codes.CodeImages
import eu.zeletrik.devcore.tools.codes.decodePng
import eu.zeletrik.devcore.tools.codes.decodeSvg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BarcodesTest {
	private fun value(input: String, symbology: Symbology) = Barcodes.encode(input, symbology).first

	private fun error(input: String, symbology: Symbology) =
		assertFailsWith<IllegalArgumentException> { Barcodes.encode(input, symbology) }.message!!

	@Test
	fun `the value carries the check digit, and the bars decode to it from PNG and SVG`() {
		mapOf(
			("ABC-123" to Symbology.CODE_128) to "ABC-123",
			("400638133393" to Symbology.EAN_13) to "4006381333931",
			("4006381333931" to Symbology.EAN_13) to "4006381333931",
			("9638507" to Symbology.EAN_8) to "96385074",
			("03600029145" to Symbology.UPC_A) to "036000291452",
			("AB-12 $/+%." to Symbology.CODE_39) to "AB-12 $/+%.",
			(" ~" to Symbology.CODE_128) to " ~",
		).forEach { (input, expected) -> assertEquals(expected, value(input.first, input.second), input.toString()) }
		val image = CodeImages.barcode(Barcodes.encode("ABC-123", Symbology.CODE_128).second)
		assertEquals("ABC-123", decodePng(image.png).text)
		assertEquals("ABC-123", decodeSvg(image.svg).text)
	}

	@Test
	fun `errors name the rule`() {
		assertEquals("Check digit should be 1", error("4006381333932", Symbology.EAN_13))
		assertEquals("EAN-13 needs 12 or 13 digits", error("12345678901", Symbology.EAN_13))
		assertEquals("EAN-13 needs 12 or 13 digits", error("12345678901234", Symbology.EAN_13))
		assertEquals("EAN-13 needs 12 or 13 digits", error("12345678901a", Symbology.EAN_13))
		assertEquals("Check digit should be 4", error("96385075", Symbology.EAN_8))
		assertEquals("Check digit should be 2", error("036000291453", Symbology.UPC_A))
		assertEquals("EAN-8 needs 7 or 8 digits", error("123456", Symbology.EAN_8))
		assertEquals("UPC-A needs 11 or 12 digits", error("1234567890123", Symbology.UPC_A))
		assertEquals("Code 39 can't encode 'x'", error("AxB", Symbology.CODE_39))
		listOf("é", "a\tb").forEach {
			assertEquals("Code 128 takes only ASCII characters 32–126 (space to ~)", error(it, Symbology.CODE_128), it)
		}
	}

	@Test
	fun `at least 11 modules of quiet zone on each side`() {
		mapOf(
			Symbology.CODE_128 to "Hello-128", Symbology.CODE_39 to "HELLO", Symbology.EAN_13 to "590123412345",
			Symbology.EAN_8 to "9638507", Symbology.UPC_A to "03600029145",
		).forEach { (symbology, value) ->
			val matrix = Barcodes.encode(value, symbology).second
			val row = (0 until matrix.width).map { matrix.get(it, 0) }
			val left = row.indexOfFirst { it }
			val right = matrix.width - 1 - row.indexOfLast { it }
			assertTrue(left >= 11 && right >= 11, "$symbology: $left / $right")
		}
	}
}
