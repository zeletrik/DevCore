package eu.zeletrik.devcore.tools.xml

import eu.zeletrik.devcore.tools.json.Indent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XmlFormatterTest {
	private val sample = "<a><b>text</b><c>1</c></a>"
	private val twoSpaces = "<a>\n  <b>text</b>\n  <c>1</c>\n</a>"

	private fun error(text: String) = assertFailsWith<IllegalArgumentException> { XmlFormatter.format(text) }.message!!

	@Test
	fun layouts() {
		assertEquals(twoSpaces, XmlFormatter.format(sample))
		assertEquals("<a>\n    <b>text</b>\n    <c>1</c>\n</a>", XmlFormatter.format(sample, Indent.FOUR))
		assertEquals(sample, XmlFormatter.format(twoSpaces, Indent.MINIFIED))
		assertEquals("<r>\n  <t>two spaces</t>\n</r>", XmlFormatter.format("<r><t>two spaces</t></r>"))
	}

	@Test
	fun `declaration, comments, CDATA, PIs and attributes are kept as written`() {
		val input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><r><!-- note --><![CDATA[<x> & y]]><?pi data?></r>"
		assertEquals(
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<r>\n  <!-- note -->\n  <![CDATA[<x> & y]]>\n  <?pi data?>\n</r>",
			XmlFormatter.format(input),
		)
		assertEquals("<r z=\"1\" a=\"x &amp; y\">t</r>", XmlFormatter.format("<r z=\"1\" a=\"x &amp; y\">t</r>"))
	}

	@Test
	fun `a standalone declaration, a PI without data and the minified top level`() {
		val input = "<?xml version=\"1.0\" standalone=\"yes\"?><a><?pi?></a>"
		assertEquals(input, XmlFormatter.format(input, Indent.MINIFIED))
		assertEquals(
			"<?xml version=\"1.0\" standalone=\"yes\"?>\n<a>\n  <?pi?>\n</a>",
			XmlFormatter.format(input),
		)
	}

	@Test
	fun `namespace declarations come first, then attributes`() {
		assertEquals(
			"<r xmlns=\"urn:x\" xmlns:p=\"urn:p\" p:a=\"1\">\n  <p:c/>\n</r>",
			XmlFormatter.format("<r xmlns=\"urn:x\" xmlns:p=\"urn:p\" p:a=\"1\"><p:c/></r>"),
		)
	}

	@Test
	fun `text around entity and character references is joined and escaped once`() {
		assertEquals("<a>Tom &amp; \"Jerry\"</a>", XmlFormatter.format("<a>Tom &amp; &quot;Jerry&quot;</a>"))
		assertEquals("<a>line1\nline2</a>", XmlFormatter.format("<a>line1&#10;line2</a>"))
		assertEquals("<a>a  b</a>", XmlFormatter.format("<a>a&#32;&#32;b</a>"))
		assertEquals("<a>1 &lt; 2 &gt; 0</a>", XmlFormatter.format("<a>1 &lt; 2 &gt; 0</a>"))
	}

	@Test
	fun `attribute values keep the whitespace references a parser would normalize`() {
		assertEquals("<a t=\"l1&#10;l2\" u=\"x&#9;y\"/>", XmlFormatter.format("<a t=\"l1&#10;l2\" u=\"x&#9;y\"/>"))
		assertEquals("<a t=\"x&#13;y\"/>", XmlFormatter.format("<a t=\"x&#13;y\"/>"))
	}

	@Test
	fun `whitespace-only values are kept, whitespace between elements is layout`() {
		assertEquals("<cfg>\n  <sep> </sep>\n</cfg>", XmlFormatter.format("<cfg><sep> </sep></cfg>"))
		assertEquals("<a>\n  <b/>\n</a>", XmlFormatter.format("<a>\n  <b/>\n</a>"))
	}

	@Test
	fun `a leading byte order mark is accepted`() {
		assertEquals("<a/>", XmlFormatter.format(Char(0xFEFF) + "<a/>"))
	}

	@Test
	fun `not well-formed input reports line and column`() {
		listOf("<a><b/></a>x", "<a>").forEach { assertTrue(error(it).startsWith("Invalid XML at line 1, column"), it) }
	}

	@Test
	fun `any DTD is refused before an entity could be resolved or read`() {
		assertEquals("DTDs aren't supported", error("<!DOCTYPE x [<!ENTITY a \"b\">]><x>&a;</x>"))
		assertEquals("DTDs aren't supported", error("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/hostname\">]><x>&e;</x>"))
	}

	@Test
	fun `the output stops at the cap while it is written`() {
		val xml = "<a>".repeat(90) + "<b/>".repeat(60_000) + "</a>".repeat(90)
		assertEquals("Output is too large (max 5,000,000 characters)", error(xml))
	}
}
