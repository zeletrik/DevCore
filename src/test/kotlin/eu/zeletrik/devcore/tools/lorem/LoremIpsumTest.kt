package eu.zeletrik.devcore.tools.lorem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

	@Test
	fun `the classic start is optional for words and sentences`() {
		val classic = LoremIpsum.generate(LoremUnit.WORDS, 9, classicStart = true)
		assertTrue(classic.startsWith("Lorem ipsum dolor sit amet consectetur adipiscing elit "), classic)
		assertEquals(9, classic.split(" ").size)
		val words = LoremIpsum.generate(LoremUnit.WORDS, 3, classicStart = false).split(" ")
		assertEquals(3, words.size)
		assertTrue(words.all { word -> word.all { it in 'a'..'z' } }, words.toString())
		val text = LoremIpsum.generate(LoremUnit.SENTENCES, 2, classicStart = false)
		assertEquals(2, sentences(text).size)
		// Random sentences have no comma, so only the classic opening can start this way.
		assertFalse(text.startsWith("Lorem ipsum dolor sit amet,"), text)
	}
}
