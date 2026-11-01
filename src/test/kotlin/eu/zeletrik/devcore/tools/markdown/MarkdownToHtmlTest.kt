package eu.zeletrik.devcore.tools.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
