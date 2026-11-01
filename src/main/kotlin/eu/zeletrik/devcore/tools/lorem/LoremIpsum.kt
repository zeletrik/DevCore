package eu.zeletrik.devcore.tools.lorem

import kotlin.random.Random

/** What the count counts. */
enum class LoremUnit(val label: String) { PARAGRAPHS("Paragraphs"), SENTENCES("Sentences"), WORDS("Words") }

private const val MIN_WORDS = 8
private const val MAX_WORDS = 16
private const val MIN_SENTENCES = 4
private const val MAX_SENTENCES = 7

/** Sentences of 8–16 words, paragraphs of 4–7 sentences, from the classic vocabulary. */
object LoremIpsum {
	private const val OPENING = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
	private val vocabulary = (
		"lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore " +
			"magna aliqua enim ad minim veniam quis nostrud exercitation ullamco laboris nisi aliquip ex ea commodo " +
			"consequat duis aute irure in reprehenderit voluptate velit esse cillum fugiat nulla pariatur excepteur sint " +
			"occaecat cupidatat non proident sunt culpa qui officia deserunt mollit anim id est laborum"
		).split(" ")
	private val openingWords = OPENING.removeSuffix(".").replace(",", "").split(" ")

	fun generate(unit: LoremUnit, count: Int, classicStart: Boolean): String = when (unit) {
		LoremUnit.WORDS -> {
			val opening = if (classicStart) openingWords else emptyList()
			(opening + List(count) { vocabulary.random() }).take(count).joinToString(" ")
		}
		LoremUnit.SENTENCES -> sentences(count, classicStart).joinToString(" ")
		LoremUnit.PARAGRAPHS -> List(count) { i ->
			sentences(Random.nextInt(MIN_SENTENCES, MAX_SENTENCES + 1), classicStart && i == 0).joinToString(" ")
		}.joinToString("\n\n")
	}

	private fun sentences(count: Int, classicStart: Boolean) = List(count) { i ->
		if (i == 0 && classicStart) OPENING
		else List(Random.nextInt(MIN_WORDS, MAX_WORDS + 1)) { vocabulary.random() }.joinToString(" ")
			.replaceFirstChar { it.uppercase() } + "."
	}
}
