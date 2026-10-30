package eu.zeletrik.devcore.tools.htmlentities

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEntitiesTest {
	@Test
	fun `encode escapes the five HTML-special characters`() {
		assertEquals(
			"&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&#39;s&lt;/a&gt;",
			HtmlEntities.encode("<a href=\"x\">Tom & Jerry's</a>", nonAscii = false),
		)
	}

	@Test
	fun `non-ASCII stays unless asked for`() {
		assertEquals("café 😀", HtmlEntities.encode("café 😀", nonAscii = false))
		assertEquals("caf&#xE9; &#x1F600; &lt;", HtmlEntities.encode("café 😀 <", nonAscii = true))
	}

	@Test
	fun `decode resolves named, decimal and hex references`() {
		assertEquals("<b>café © © © 😀", HtmlEntities.decode("&lt;b&gt;caf&eacute; &copy; &#169; &#xA9; &#x1F600;"))
	}

	@Test
	fun `anything that is not a complete reference stays as written, without errors`() {
		listOf("&amp", "&nope;", "& b", "&#99999999999;", "&#xD800;", "&;", "&#;").forEach {
			assertEquals(it, HtmlEntities.decode(it))
		}
	}
}
