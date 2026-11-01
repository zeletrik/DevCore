package eu.zeletrik.devcore.tools.stringescape

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StringEscapesTest {
	private val bs = "\\"

	private fun invalidAt(position: Int, text: String, flavor: Flavor = Flavor.JSON) {
		val e = assertFailsWith<IllegalArgumentException> { StringEscapes.unescape(text, flavor) }
		assertEquals("Invalid escape sequence at position $position", e.message)
	}

	@Test
	fun `escape per flavor`() {
		assertEquals("""He said \"hi\" \\ it's\n\té""", StringEscapes.escape("He said \"hi\" \\ it's\n\té", Flavor.JSON))
		assertEquals("""it\'s \"x\"""", StringEscapes.escape("it's \"x\"", Flavor.JAVASCRIPT))
		assertEquals("""it's \"x\"""", StringEscapes.escape("it's \"x\"", Flavor.JAVA))
	}

	@Test
	fun `control characters use short forms where they exist, else four hex digits`() {
		val input = "${Char(1)}\r\b${Char(12)}"
		assertEquals(bs + "u0001" + """\r\b\f""", StringEscapes.escape(input, Flavor.JSON))
	}

	@Test
	fun `unescape per flavor`() {
		val json = """a\"b\\c\/d""" + bs + "u00E9" + """\n"""
		assertEquals("a\"b\\c/dé\n", StringEscapes.unescape(json, Flavor.JSON))
		assertEquals("it's", StringEscapes.unescape("""it\'s""", Flavor.JAVASCRIPT))
		assertEquals("it's", StringEscapes.unescape("""it\'s""", Flavor.JAVA))
		val javaScript = bs + "u{1F600}" + bs + "x41" + bs + "v" + bs + "0"
		assertEquals("😀A${Char(11)}${Char(0)}", StringEscapes.unescape(javaScript, Flavor.JAVASCRIPT))
		assertEquals("A0 ", StringEscapes.unescape(bs + "101" + bs + "60" + bs + "s", Flavor.JAVA))
	}

	@Test
	fun `javascript drops a backslash before a line break, the others refuse it`() {
		assertEquals("ab", StringEscapes.unescape("a\\\nb", Flavor.JAVASCRIPT))
		assertEquals("ab", StringEscapes.unescape("a\\\r\nb", Flavor.JAVASCRIPT))
		invalidAt(2, "a\\\nb", Flavor.JAVA)
	}

	@Test
	fun `undefined or malformed escapes fail at the backslash's UTF-16 position`() {
		invalidAt(3, """it\'s""")
		invalidAt(3, """ab\q""")
		invalidAt(2, "x" + bs + "u12")
		invalidAt(4, """abc\""")
		invalidAt(3, "😀" + """\q""")
		listOf(bs + "u{}", bs + "u{110000}", bs + "x4", bs + "01", bs + "1").forEach { invalidAt(1, it, Flavor.JAVASCRIPT) }
		listOf(bs + "x41", bs + "1").forEach { invalidAt(1, it, Flavor.JSON) }
	}

	@Test
	fun `escape then unescape round-trips in every flavor`() {
		val text = "a\"b'c\\d\n\t\r\b${Char(12)}${Char(1)}é😀/"
		Flavor.entries.forEach { assertEquals(text, StringEscapes.unescape(StringEscapes.escape(text, it), it), it.name) }
	}
}
