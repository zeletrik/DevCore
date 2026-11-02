package eu.zeletrik.devcore.tools.casing

import java.util.Locale
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaseConverterTest {
	private fun convert(text: String, case: Case) = CaseConverter.convert(text, case)

	@Test
	fun `all ten cases`() {
		val expected = mapOf(
			Case.CAMEL to "xmlHttpRequest2Go", Case.PASCAL to "XmlHttpRequest2Go", Case.SNAKE to "xml_http_request2_go",
			Case.KEBAB to "xml-http-request2-go", Case.CONSTANT to "XML_HTTP_REQUEST2_GO", Case.DOT to "xml.http.request2.go",
			Case.TITLE to "Xml Http Request2 Go", Case.SENTENCE to "Xml http request2 go", Case.LOWER to "xmlhttprequest2go",
			Case.UPPER to "XMLHTTPREQUEST2GO",
		)
		assertEquals(expected, Case.entries.associateWith { convert("XMLHttpRequest2Go", it) })
	}

	@Test
	fun `word splitting`() {
		assertEquals("hello_world_foo_bar_baz", convert("hello_world-foo bar.baz", Case.SNAKE))
		assertEquals("foo-bar-v2-api", convert("fooBar v2Api", Case.KEBAB))
		assertEquals("http_server", convert("HTTPServer", Case.SNAKE))
		assertEquals("utf8_string_abc123def", convert("utf8String abc123def", Case.SNAKE))
	}

	@Test
	fun `each line separately, and lower and UPPER case as written`() {
		assertEquals("fooBar\nbazQux", convert("foo bar\nbaz qux", Case.CAMEL))
		assertEquals("foo-bar baz", convert("Foo-Bar baz", Case.LOWER))
		assertEquals("FOO-BAR BAZ", convert("Foo-Bar baz", Case.UPPER))
	}

	@Test
	fun `case mapping ignores the default locale`() {
		val saved = Locale.getDefault()
		try {
			Locale.setDefault(Locale.forLanguageTag("tr-TR"))
			assertEquals("title", convert("TITLE", Case.LOWER))
		} finally {
			Locale.setDefault(saved)
		}
	}

	@Test
	fun `combining marks stay inside words`() {
		assertEquals("cafe\u0301_au_lait", convert("cafe\u0301 au lait", Case.SNAKE))
		val hindi = "नमस्ते दुनिया"
		assertEquals(hindi.replace(' ', '-'), convert(hindi, Case.KEBAB))
	}

	@Test
	fun `every case of text dense in expanding letters converts in linear time`() {
		val dotted = "İ".repeat(200_000)
		assertTrue(measureNanoTime { Case.entries.forEach { convert(dotted, it) } } < 2_000_000_000)
		assertEquals("STRASSE", convert("straße", Case.UPPER))
	}
}
