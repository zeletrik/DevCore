package eu.zeletrik.devcore.tools.base64

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base64CodecTest {
	@Test
	fun `standard encode is padded`() {
		assertEquals("aGk=", Base64Codec.encode("hi", urlSafe = false))
		assertEquals("w7w=", Base64Codec.encode("ü", urlSafe = false))
	}

	@Test
	fun `url-safe encode uses - and _ without padding`() {
		assertEquals("Pz8_", Base64Codec.encode("???", urlSafe = true))
		assertEquals("aGk", Base64Codec.encode("hi", urlSafe = true))
	}

	@Test
	fun `decodes both variants`() {
		assertEquals("hi", Base64Codec.decode("aGk=", urlSafe = false))
		assertEquals("???", Base64Codec.decode("Pz8_", urlSafe = true))
	}

	@Test
	fun `decode ignores whitespace and missing padding`() {
		assertEquals("hi", Base64Codec.decode("aG\n k=", urlSafe = false))
		assertEquals("hi", Base64Codec.decode("aGk", urlSafe = false))
	}

	@Test
	fun `invalid input is rejected with a fixed message`() {
		listOf("aGk*" to false, "Pz8_" to false, "abcde" to false).forEach { (text, urlSafe) ->
			val e = assertFailsWith<IllegalArgumentException> { Base64Codec.decode(text, urlSafe) }
			assertEquals("Not valid Base64", e.message)
		}
	}

	@Test
	fun `non-UTF-8 bytes become replacement characters`() {
		assertEquals("�", Base64Codec.decode("/w==", urlSafe = false))
	}
}
