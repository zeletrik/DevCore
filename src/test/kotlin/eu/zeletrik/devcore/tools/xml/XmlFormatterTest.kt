package eu.zeletrik.devcore.tools.xml

import eu.zeletrik.devcore.tools.json.Indent
import java.net.ServerSocket
import java.net.SocketTimeoutException
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
	fun `not well-formed input reports line and column`() {
		listOf("<a><b/></a>x", "<a>").forEach { assertTrue(error(it).startsWith("Invalid XML at line 1, column"), it) }
	}

	@Test
	fun `any DTD is refused before an entity could be resolved, read or expanded`() {
		assertEquals("DTDs are not supported", error("<!DOCTYPE x [<!ENTITY a \"b\">]><x>&a;</x>"))
		assertEquals("DTDs are not supported", error("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/hostname\">]><x>&e;</x>"))
		val laughs = "<!DOCTYPE l [<!ENTITY l0 \"ha\">" + (1..9).joinToString("") { "<!ENTITY l$it \"" + "&l${it - 1};".repeat(10) + "\">" } + "]><l>&l9;</l>"
		assertEquals("DTDs are not supported", error(laughs))
		ServerSocket(0).use { server ->
			server.soTimeout = 300
			assertEquals("DTDs are not supported", error("<!DOCTYPE x [<!ENTITY e SYSTEM \"http://127.0.0.1:${server.localPort}/x\">]><x>&e;</x>"))
			assertFailsWith<SocketTimeoutException> { server.accept() }
		}
	}
}
