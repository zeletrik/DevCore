package eu.zeletrik.devcore.tools.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MarkdownToHtmlTest {
	@Test
	fun `tables, strikethrough and autolinks are on`() {
		val table = MarkdownToHtml.convert("| a | b |\n|---|---|\n| 1 | 2 |")
		assertTrue(
			Regex(
				"<table>.*<th>a</th>.*<th>b</th>.*<td>1</td>.*<td>2</td>.*</table>",
				RegexOption.DOT_MATCHES_ALL,
			).containsMatchIn(table)
		)
		assertEquals("<p><del>gone</del></p>", MarkdownToHtml.convert("~~gone~~"))
		assertTrue(
			"<a href=\"https://example.com\">https://example.com</a>" in MarkdownToHtml.convert("see https://example.com")
		)
	}

	@Test
	fun `raw HTML passes through as text`() {
		assertTrue("<img src=x onerror=alert(1)>" in MarkdownToHtml.convert("<img src=x onerror=alert(1)>"))
	}

	@Test
	fun `input up to 100,000 characters, and reference links stop at the output cap`() {
		assertEquals("<p>${"a".repeat(100_000)}</p>", MarkdownToHtml.convert("a".repeat(100_000)))
		val tooLarge = assertFailsWith<IllegalArgumentException> { MarkdownToHtml.convert("a".repeat(100_001)) }
		assertEquals("Input is too large for Markdown to HTML (max 100,000 characters)", tooLarge.message)
		// 14,000 characters that would render to 10,000,000: each [a] repeats the 10,000-character destination.
		val amplified = "[a]: http://x/" + "y".repeat(10_000) + "\n\n" + "[a] ".repeat(1_000)
		val tooMuch = assertFailsWith<IllegalArgumentException> { MarkdownToHtml.convert(amplified) }
		assertEquals("Output is too large (max 5,000,000 characters)", tooMuch.message)
	}
}
