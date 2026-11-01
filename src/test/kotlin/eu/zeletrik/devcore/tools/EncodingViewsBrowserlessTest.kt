package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.htmlentities.HtmlEntitiesView
import eu.zeletrik.devcore.tools.morse.MorseView
import eu.zeletrik.devcore.tools.nato.NatoView
import eu.zeletrik.devcore.tools.stringescape.StringEscapeView
import eu.zeletrik.devcore.tools.unicode.EscapeFormat
import eu.zeletrik.devcore.tools.unicode.UnicodeEscapeView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class EncodingViewsBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `html entities deep link in decode mode, and the encode option only shows when encoding`() {
		val view = navigate("html-entities?mode=decode&input=%26lt%3Bb%26gt%3B", HtmlEntitiesView::class.java)
		assertEquals("<b>", view.output.value)
		assertFalse(view.nonAscii.isVisible)
		test(view.mode).selectItem("Encode")
		assertTrue(view.nonAscii.isVisible)
		assertEquals("&amp;lt;b&amp;gt;", view.output.value)
	}

	@Test
	fun `unicode escape follows the chosen format`() {
		val view = navigate("unicode-escape?input=%C3%A9", UnicodeEscapeView::class.java)
		assertEquals("\\u00E9", view.output.value)
		test(view.format).selectItem(EscapeFormat.CODE_POINTS.label)
		assertEquals("U+00E9", view.output.value)
		assertFalse(view.escapeAscii.isVisible)
	}

	@Test
	fun `string escape labels its modes Escape and Unescape and unescapes from a deep link`() {
		val view = navigate("string-escape?mode=decode&input=it%5C%27s", StringEscapeView::class.java)
		assertTrue(view.input.isInvalid)
		assertEquals("Invalid escape sequence at position 3", view.input.errorMessage)
		test(view.flavor).selectItem("JavaScript")
		assertEquals("it's", view.output.value)
		assertEquals("Unescape", view.mode.itemLabelGenerator.apply(view.mode.value))
	}

	@Test
	fun `morse and nato from deep links`() {
		assertEquals("... --- ...", navigate("morse-code?input=sos", MorseView::class.java).output.value)
		assertEquals("SOS", navigate("morse-code?mode=decode&input=...%20---%20...", MorseView::class.java).output.value)
		assertEquals("Alfa Bravo", navigate("nato-alphabet?input=ab", NatoView::class.java).output.value)
	}
}
