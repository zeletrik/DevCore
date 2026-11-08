package eu.zeletrik.devcore.tools.qr

import com.google.zxing.ResultMetadataType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import eu.zeletrik.devcore.tools.codes.CodeImages
import eu.zeletrik.devcore.tools.codes.decodePng
import eu.zeletrik.devcore.tools.codes.decodeSvg
import java.io.ByteArrayInputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QrCodesTest {
	private fun qr(content: String, level: ErrorCorrectionLevel = ErrorCorrectionLevel.M) =
		CodeImages.qr(QrCodes.encode(content, level))

	@Test
	fun `PNG and SVG decode to the content at the chosen level, UTF-8 included`() {
		val code = qr("https://example.com")
		val result = decodePng(code.png)
		assertEquals("https://example.com", result.text)
		assertEquals("M", result.resultMetadata[ResultMetadataType.ERROR_CORRECTION_LEVEL])
		assertEquals("https://example.com", decodeSvg(code.svg).text)
		assertEquals("Grüße 😀", decodePng(qr("Grüße 😀").png).text)
	}

	@Test
	fun `an exact 4-module quiet zone at the largest whole module size within 512 px, black on white`() {
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
	fun `too much data names the error correction level`() {
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
}
