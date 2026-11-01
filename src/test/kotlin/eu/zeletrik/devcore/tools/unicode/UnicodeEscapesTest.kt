package eu.zeletrik.devcore.tools.unicode

import kotlin.test.Test
import kotlin.test.assertEquals

class UnicodeEscapesTest {
	private fun encode(text: String, format: EscapeFormat, escapeAscii: Boolean = false) =
		UnicodeEscapes.encode(text, format, escapeAscii)

	private fun unchanged(vararg inputs: String) = inputs.forEach { assertEquals(it, UnicodeEscapes.decode(it)) }

	@Test
	fun `encode in each format`() {
		assertEquals("caf\\u00E9 \\uD83D\\uDE00", encode("café 😀", EscapeFormat.UTF16))
		assertEquals("a\\u{E9}\\u{1F600}", encode("aé😀", EscapeFormat.BRACED))
		assertEquals("a&#xE9;&#x1F600;", encode("aé😀", EscapeFormat.HTML))
		assertEquals("\\u0041\\u00E9", encode("Aé", EscapeFormat.UTF16, escapeAscii = true))
		assertEquals("U+0041 U+00E9 U+1F600", encode("Aé😀", EscapeFormat.CODE_POINTS))
	}

	@Test
	fun `decode every notation`() {
		assertEquals("café 😀", UnicodeEscapes.decode("caf\\u00E9 \\uD83D\\uDE00"))
		assertEquals("ő𐍈ő", UnicodeEscapes.decode("\\u{151}&#x10348;&#337;"))
		assertEquals("Hi😀!", UnicodeEscapes.decode("U+0048 U+0069 U+1F600!"))
		unchanged("plain \\x41 text")
	}

	@Test
	fun `braced digits - within, at and beyond the bounds`() {
		assertEquals("A", UnicodeEscapes.decode("\\u{41}"))
		assertEquals("\n", UnicodeEscapes.decode("\\u{A}"))
		assertEquals("𐍈", UnicodeEscapes.decode("\\u{010348}"))
		unchanged("\\u{}", "\\u{0000041}", "\\u{110000}")
	}

	@Test
	fun `U+ digits - within, at and beyond the bounds`() {
		assertEquals("😀", UnicodeEscapes.decode("U+1F600"))
		assertEquals("A", UnicodeEscapes.decode("U+0041"))
		assertEquals(String(Character.toChars(0x10FFFF)), UnicodeEscapes.decode("U+10FFFF"))
		unchanged("U+041", "U+1F6000", "U+110000")
	}

	@Test
	fun `an unpaired surrogate becomes the replacement character`() {
		assertEquals(Char(0xFFFD).toString(), UnicodeEscapes.decode("\\uD83D"))
	}

	@Test
	fun `round trips through every format`() {
		val text = "Grüße, 世界 😀\n"
		EscapeFormat.entries.forEach {
			assertEquals(text, UnicodeEscapes.decode(encode(text, it, escapeAscii = true)), it.name)
		}
	}
}
