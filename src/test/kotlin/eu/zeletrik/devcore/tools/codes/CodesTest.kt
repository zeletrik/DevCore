package eu.zeletrik.devcore.tools.codes

import com.google.zxing.ResultMetadataType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import eu.zeletrik.devcore.tools.barcode.Barcodes
import eu.zeletrik.devcore.tools.barcode.Symbology
import eu.zeletrik.devcore.tools.qr.QrCodes
import eu.zeletrik.devcore.tools.qr.WifiSecurity
import java.io.ByteArrayInputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CodesTest {
	private fun qr(content: String, level: ErrorCorrectionLevel = ErrorCorrectionLevel.M) =
		CodeImages.qr(QrCodes.encode(content, level))

	@Test
	fun `QR decodes to the content at the chosen level, PNG and SVG alike`() {
		val code = qr("https://example.com")
		val result = decodePng(code.png)
		assertEquals("https://example.com", result.text)
		assertEquals("M", result.resultMetadata[ResultMetadataType.ERROR_CORRECTION_LEVEL])
		assertEquals("https://example.com", decodeSvg(code.svg).text)
		listOf(ErrorCorrectionLevel.L, ErrorCorrectionLevel.Q, ErrorCorrectionLevel.H).forEach { level ->
			assertEquals(level.name, decodePng(qr("x", level).png).resultMetadata[ResultMetadataType.ERROR_CORRECTION_LEVEL])
		}
	}

	@Test
	fun `QR carries the UTF-8 bytes`() {
		val result = decodePng(qr("Grüße 😀").png)
		assertEquals("Grüße 😀", result.text)
		val segments = (result.resultMetadata[ResultMetadataType.BYTE_SEGMENTS] as List<*>).map { it as ByteArray }
		assertContentEquals("Grüße 😀".toByteArray(Charsets.UTF_8), segments.reduce(ByteArray::plus))
	}

	@Test
	fun `QR has an exact 4-module quiet zone at the largest whole module size within 512 px, black on white`() {
		val matrix = QrCodes.encode("https://example.com", ErrorCorrectionLevel.M)
		val image = ImageIO.read(ByteArrayInputStream(CodeImages.qr(matrix).png))
		val module = 512 / (matrix.width + 8)
		assertEquals((matrix.width + 8) * module, image.width)
		assertEquals(image.width, image.height)
		assertTrue(image.width <= 512)
		val firstDark = (0 until image.width).first { x -> (image.getRGB(x, 4 * module) and 0xFFFFFF) == 0 }
		assertEquals(4 * module, firstDark)
		assertEquals(0xFFFFFF, image.getRGB(0, 0) and 0xFFFFFF)
	}

	@Test
	fun `QR capacity - fits at L, too much at H`() {
		assertEquals("a".repeat(2000), decodePng(qr("a".repeat(2000), ErrorCorrectionLevel.L).png).text)
		val e = assertFailsWith<IllegalArgumentException> { QrCodes.encode("a".repeat(2000), ErrorCorrectionLevel.H) }
		assertEquals("Too much data for a QR code at error correction level H", e.message)
	}

	@Test
	fun `WiFi payloads`() {
		assertEquals("WIFI:T:WPA;S:home;P:secret;;", QrCodes.wifi("home", "secret", WifiSecurity.WPA, hidden = false))
		assertEquals("WIFI:T:WPA;S:home;P:secret;H:true;;", QrCodes.wifi("home", "secret", WifiSecurity.WPA, hidden = true))
		assertEquals("WIFI:T:nopass;S:cafe;;", QrCodes.wifi("cafe", "", WifiSecurity.NONE, hidden = false))
		assertTrue("T:WEP" in QrCodes.wifi("x", "y", WifiSecurity.WEP, hidden = false))
		assertEquals(
			"""WIFI:T:WPA;S:my\;net;P:a\\b\,c\:d\"e;;""",
			QrCodes.wifi("my;net", """a\b,c:d"e""", WifiSecurity.WPA, hidden = false),
		)
	}

	@Test
	fun `PNGs never touch the disk`() {
		val dir = Files.createTempDirectory("devcore-no-cache").toFile()
		val savedUse = ImageIO.getUseCache()
		val savedDir = ImageIO.getCacheDirectory()
		// A read-only cache directory makes any attempt to use ImageIO's file cache fail.
		val png = try {
			dir.setWritable(false)
			ImageIO.setUseCache(true)
			ImageIO.setCacheDirectory(dir)
			qr(QrCodes.wifi("home", "secret", WifiSecurity.WPA, false)).png
		} finally {
			ImageIO.setUseCache(savedUse)
			ImageIO.setCacheDirectory(savedDir)
			dir.setWritable(true)
			dir.delete()
		}
		assertEquals("WIFI:T:WPA;S:home;P:secret;;", decodePng(png).text)
	}

	private fun barcode(input: String, symbology: Symbology) =
		Barcodes.encode(input, symbology).let { (value, matrix) -> value to CodeImages.barcode(matrix) }

	private fun error(input: String, symbology: Symbology) =
		assertFailsWith<IllegalArgumentException> { Barcodes.encode(input, symbology) }.message!!

	@Test
	fun `barcodes decode to the value, check digits included`() {
		mapOf(
			("ABC-123" to Symbology.CODE_128) to "ABC-123",
			("400638133393" to Symbology.EAN_13) to "4006381333931",
			("4006381333931" to Symbology.EAN_13) to "4006381333931",
			("9638507" to Symbology.EAN_8) to "96385074",
			("03600029145" to Symbology.UPC_A) to "036000291452",
			("AB-12 $/+%." to Symbology.CODE_39) to "AB-12 $/+%.",
			(" ~" to Symbology.CODE_128) to " ~",
		).forEach { (input, expected) ->
			val (value, image) = barcode(input.first, input.second)
			assertEquals(expected, value)
			assertEquals(expected, decodePng(image.png).text, input.toString())
			assertEquals(expected, decodeSvg(image.svg).text, input.toString())
		}
	}

	@Test
	fun `barcode errors name the rule`() {
		assertEquals("Check digit should be 1", error("4006381333932", Symbology.EAN_13))
		assertEquals("EAN-13 needs 12 or 13 digits", error("12345678901", Symbology.EAN_13))
		assertEquals("EAN-13 needs 12 or 13 digits", error("12345678901234", Symbology.EAN_13))
		assertEquals("Check digit should be 4", error("96385075", Symbology.EAN_8))
		assertEquals("Check digit should be 2", error("036000291453", Symbology.UPC_A))
		assertEquals("EAN-8 needs 7 or 8 digits", error("123456", Symbology.EAN_8))
		assertEquals("UPC-A needs 11 or 12 digits", error("1234567890123", Symbology.UPC_A))
		assertEquals("Code 39 can't encode 'x'", error("AxB", Symbology.CODE_39))
		assertTrue(error("é", Symbology.CODE_128).startsWith("Code 128 takes only ASCII"))
		assertTrue(error("a\tb", Symbology.CODE_128).startsWith("Code 128 takes only ASCII"))
	}
}
