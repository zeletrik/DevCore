package eu.zeletrik.devcore.tools

import eu.zeletrik.devcore.tools.json.Indent
import eu.zeletrik.devcore.tools.json.JsonFormatter
import eu.zeletrik.devcore.tools.xml.XmlFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XmlJsonFixesTest {
	private val bom = Char(0xFEFF).toString()
	private val backslash = "\\"

	@Test
	fun `xml keeps text around entity and character references`() {
		assertEquals("<a>Tom &amp; \"Jerry\"</a>", XmlFormatter.format("<a>Tom &amp; &quot;Jerry&quot;</a>"))
		assertEquals("<a>line1\nline2</a>", XmlFormatter.format("<a>line1&#10;line2</a>"))
		assertEquals("<a>a  b</a>", XmlFormatter.format("<a>a&#32;&#32;b</a>"))
	}

	@Test
	fun `xml keeps whitespace-only values, drops whitespace between elements`() {
		assertEquals("<cfg>\n  <sep> </sep>\n</cfg>", XmlFormatter.format("<cfg><sep> </sep></cfg>"))
		assertEquals("<a>\n  <b/>\n</a>", XmlFormatter.format("<a>\n  <b/>\n</a>"))
	}

	@Test
	fun `xml attribute values keep their character references`() {
		assertEquals("<a t=\"l1&#10;l2\" u=\"x&#9;y\"/>", XmlFormatter.format("<a t=\"l1&#10;l2\" u=\"x&#9;y\"/>"))
	}

	@Test
	fun `a leading byte order mark is accepted`() {
		assertEquals("<a/>", XmlFormatter.format(bom + "<a/>"))
		assertEquals("{}", JsonFormatter.format(bom + "{}"))
	}

	@Test
	fun `an escaped lone surrogate stays escaped`() {
		assertEquals("[\"${backslash}uD83D\"]", JsonFormatter.format("[\"${backslash}ud83d\"]", Indent.MINIFIED))
	}

	@Test
	fun `expansion up to 500 levels`() {
		val at = "[".repeat(499) + "\"[1]\"" + "]".repeat(499)
		assertEquals(500, JsonFormatter.format(at, Indent.MINIFIED, expand = true).count { it == '[' })
		val over = "[".repeat(499) + "\"[[1]]\"" + "]".repeat(499)
		assertEquals(
			"Invalid JSON: nested too deeply (max 500 levels)",
			assertFailsWith<IllegalArgumentException> { JsonFormatter.format(over, Indent.MINIFIED, expand = true) }.message,
		)
	}
}
