package eu.zeletrik.devcore.tools.textstats

import kotlin.test.Test
import kotlin.test.assertEquals

class TextStatisticsTest {
	private fun stats(text: String) = TextStatistics.describe(text).lines().associate { it.substringBefore(": ") to it.substringAfter(": ").toInt() }

	@Test
	fun counts() {
		assertEquals(
			mapOf(
				"Characters" to 30, "Characters without whitespace" to 24, "Words" to 6, "Lines" to 3, "Sentences" to 3,
				"Paragraphs" to 2, "UTF-8 bytes" to 30,
			),
			stats("Hello world. Bye!\n\nNew para 2."),
		)
	}

	@Test
	fun `code points and bytes`() {
		val s = stats("Héllo 😀")
		assertEquals(listOf(7, 1, 11), listOf(s["Characters"], s["Words"], s["UTF-8 bytes"]))
	}
}
