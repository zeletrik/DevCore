package eu.zeletrik.devcore.tools.textstats

import java.text.BreakIterator
import java.util.Locale

object TextStatistics {
	fun describe(text: String): String {
		val codePoints = text.codePoints().toArray()
		val paragraphs = text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }
		return listOf(
			"Characters" to codePoints.size,
			"Characters without whitespace" to codePoints.count { !Character.isWhitespace(it) },
			"Words" to units(text, BreakIterator.getWordInstance(Locale.ROOT)),
			"Lines" to text.count { it == '\n' } + 1,
			"Sentences" to units(text, BreakIterator.getSentenceInstance(Locale.ROOT)),
			"Paragraphs" to paragraphs,
			"UTF-8 bytes" to text.toByteArray(Charsets.UTF_8).size,
		).joinToString("\n") { (label, count) -> "$label: $count" }
	}

	/** Break-iterator segments that contain at least one letter or digit. */
	private fun units(text: String, iterator: BreakIterator): Int {
		iterator.setText(text)
		var count = 0
		var start = iterator.first()
		var end = iterator.next()
		while (end != BreakIterator.DONE) {
			if (text.substring(start, end).codePoints().anyMatch(Character::isLetterOrDigit)) count++
			start = end
			end = iterator.next()
		}
		return count
	}
}
