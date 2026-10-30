package eu.zeletrik.devcore.tools.stringescape

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StringEscapesTest {
	private val bs = "\\"

	private fun invalidAt(position: Int, text: String, flavour: Flavour = Flavour.JSON) {
		val e = assertFailsWith<IllegalArgumentException> { StringEscapes.unescape(text, flavour) }
		assertEquals("Invalid escape sequence at position $position", e.message)
	}

	@Test
	fun `escape per flavour`() {
		assertEquals("""He said \"hi\" \\ it's\n\té""", StringEscapes.escape("He said \"hi\" \\ it's\n\té", Flavour.JSON))
		assertEquals("""it\'s \"x\"""", StringEscapes.escape("it's \"x\"", Flavour.JAVASCRIPT))
		assertEquals("""it's \"x\"""", StringEscapes.escape("it's \"x\"", Flavour.JAVA))
	}

	@Test
	fun `control characters use short forms where they exist, else four hex digits`() {
		val input = "${Char(1)}\r\b${Char(12)}"
		assertEquals(bs + "u0001" + """\r\b\f""", StringEscapes.escape(input, Flavour.JSON))
	}

	@Test
	fun `unescape per flavour`() {
		val json = """a\"b\\c\/d""" + bs + "u00E9" + """\n"""
		assertEquals("a\"b\\c/dé\n", StringEscapes.unescape(json, Flavour.JSON))
		assertEquals("it's", StringEscapes.unescape("""it\'s""", Flavour.JAVASCRIPT))
	}

	@Test
	fun `undefined or malformed escapes fail at the backslash's UTF-16 position`() {
		invalidAt(3, """it\'s""")
		invalidAt(3, """ab\q""")
		invalidAt(2, "x" + bs + "u12")
		invalidAt(4, """abc\""")
		invalidAt(3, "😀" + """\q""")
	}

	@Test
	fun `escape then unescape round-trips in every flavour`() {
		val text = "a\"b'c\\d\n\t\r\b${Char(12)}${Char(1)}é😀/"
		Flavour.entries.forEach { assertEquals(text, StringEscapes.unescape(StringEscapes.escape(text, it), it), it.name) }
	}
}
