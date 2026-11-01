package eu.zeletrik.devcore.tools

import eu.zeletrik.devcore.tools.barcode.Barcodes
import eu.zeletrik.devcore.tools.barcode.Symbology
import eu.zeletrik.devcore.tools.chmod.Permissions
import eu.zeletrik.devcore.tools.color.Colors
import eu.zeletrik.devcore.tools.jwt.JwtAlgorithm
import eu.zeletrik.devcore.tools.jwt.JwtDecoder
import eu.zeletrik.devcore.tools.jwt.JwtSigner
import eu.zeletrik.devcore.tools.timestamp.Timestamps
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SmallToolFixesTest {
	private val utc = ZoneId.of("UTC")

	@Test
	fun `timestamps with basic offsets, minute precision and zone ids`() {
		mapOf(
			"2026-10-28T10:00:00+0200" to "2026-10-28T08:00:00Z",
			"2026-10-28T10:00:00.000+0000" to "2026-10-28T10:00:00Z",
			"2026-10-28T10:00Z" to "2026-10-28T10:00:00Z",
			"2026-10-28T10:00+02:00" to "2026-10-28T08:00:00Z",
			"2026-10-28T10:00:00+02:00[Europe/Budapest]" to "2026-10-28T08:00:00Z",
		).forEach { (input, utcText) -> assertEquals(Instant.parse(utcText), Timestamps.parse(input, utc), input) }
	}

	@Test
	fun `custom formats use English names`() {
		val instant = Instant.parse("2026-10-29T14:30:00Z")
		val budapest = ZoneId.of("Europe/Budapest")
		assertEquals("Thursday 29 October 2026 CET", Timestamps.custom(instant, budapest, "EEEE d MMMM yyyy z"))
		assertEquals("Central European Standard Time", Timestamps.custom(instant, budapest, "zzzz"))
	}

	private val b64 = Base64.getUrlEncoder().withoutPadding()

	@Test
	fun `a signature differing only in its spare bits is invalid`() {
		val token = JwtSigner.sign("""{"sub":"1"}""", JwtAlgorithm.HS256, "secret")
		assertEquals("Signature valid", JwtSigner.check(token, "secret"))
		val signature = token.substringAfterLast('.')
		val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
		val last = alphabet.indexOf(signature.last())
		// HS256 is 32 bytes: 43 characters, the last carrying 4 data bits and 2 spare ones.
		val flipped = signature.dropLast(1) + alphabet[last xor 1]
		assertEquals(Base64.getUrlDecoder().decode(signature).toList(), Base64.getUrlDecoder().decode(flipped).toList())
		assertEquals("Signature invalid", JwtSigner.check(token.substringBeforeLast('.') + "." + flipped, "secret"))
	}

	private fun token(payload: String) =
		"${b64.encodeToString("""{"alg":"HS256"}""".toByteArray())}.${b64.encodeToString(payload.toByteArray())}.c2ln"

	@Test
	fun `unreadable time claims are named`() {
		val expiry = JwtDecoder.decode(token("""{"exp":"1600000000"}"""), Instant.EPOCH)
		assertEquals("Expiry not readable", expiry.status)
		assertEquals("exp: not readable", expiry.times)
		val notBefore = JwtDecoder.decode(token("""{"nbf":"soon"}"""), Instant.EPOCH)
		assertEquals("nbf: not readable", notBefore.times)
		assertEquals("No expiry", notBefore.status)
	}

	@Test
	fun `chmod accepts one ls -l marker`() {
		assertEquals(0b110_100_100, Permissions.parse("-rw-r--r--@"))
		assertEquals(0b110_100_100, Permissions.parse("-rw-r--r--."))
		assertEquals(0b111_101_101, Permissions.parse("drwxr-xr-x+"))
		assertFailsWith<IllegalArgumentException> { Permissions.parse("-rw-r--r--.@") }
	}

	@Test
	fun `colors - alpha, bare hex and NaN`() {
		assertEquals("rgba(255, 0, 0, 0.502)", Colors.notations(Colors.parse("#ff000080")).rgb)
		assertEquals("rgba(255, 0, 0, 0.5)", Colors.notations(Colors.parse("rgba(255, 0, 0, 0.5)")).rgb)
		assertEquals("#ff8800", Colors.notations(Colors.parse("ff8800")).hex)
		assertEquals("#aadddd", Colors.notations(Colors.parse("add")).hex)
		assertEquals("tan", Colors.notations(Colors.parse("tan")).name)
		listOf("hsl(NaN, 50%, 50%)", "rgb(Infinity, 0, 0)").forEach {
			assertEquals("Invalid color", assertFailsWith<IllegalArgumentException> { Colors.parse(it) }.message)
		}
	}

	@Test
	fun `barcodes have at least 11 modules of quiet zone on each side`() {
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
