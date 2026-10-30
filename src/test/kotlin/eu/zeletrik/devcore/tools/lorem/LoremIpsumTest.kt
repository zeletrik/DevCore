package eu.zeletrik.devcore.tools.lorem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoremIpsumTest {
	private fun sentences(text: String) = text.split(Regex("(?<=\\.)\\s+")).filter { it.isNotBlank() }

	@Test
	fun `paragraphs, sentences and words in the requested numbers`() {
		val text = LoremIpsum.generate(LoremUnit.PARAGRAPHS, 3, classicStart = true)
		val paragraphs = text.split("\n\n")
		assertEquals(3, paragraphs.size)
		assertTrue(text.startsWith("Lorem ipsum dolor sit amet"))
		listOf(1, 100).forEach { assertEquals(it, LoremIpsum.generate(LoremUnit.PARAGRAPHS, it, true).split("\n\n").size) }
		assertEquals(5, sentences(LoremIpsum.generate(LoremUnit.SENTENCES, 5, true)).size)
		assertEquals(7, LoremIpsum.generate(LoremUnit.WORDS, 7, true).split(" ").size)
		assertEquals(3, LoremIpsum.generate(LoremUnit.WORDS, 3, true).split(" ").size)
	}

	@Test
	fun `sentence and paragraph shapes`() {
		repeat(20) {
			LoremIpsum.generate(LoremUnit.PARAGRAPHS, 5, classicStart = false).split("\n\n").forEach { paragraph ->
				val sentences = sentences(paragraph)
				assertTrue(sentences.size in 4..7, paragraph)
				sentences.forEach { sentence ->
					assertTrue(sentence.split(" ").size in 8..16 && sentence[0].isUpperCase() && sentence.endsWith("."), sentence)
				}
			}
		}
	}
}
