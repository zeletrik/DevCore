package eu.zeletrik.devcore.tools.regex

import java.util.regex.Pattern

private const val DECIMAL = 10

/** A replacement string split into literal text and group references, with Java's `Matcher` syntax and errors. */
internal object Replacement {
	sealed interface Part

	class Literal(val text: String) : Part

	class Group(val index: Int) : Part

	fun parse(replacement: String, pattern: Pattern, groupCount: Int): List<Part> =
		Parser(replacement, pattern, groupCount).parse()

	/** Reads [text] left to right; [i] is the index of the next unread character. */
	private class Parser(private val text: String, private val pattern: Pattern, private val groupCount: Int) {
		private val parts = mutableListOf<Part>()
		private val literal = StringBuilder()
		private var i = 0

		fun parse(): List<Part> {
			while (i < text.length) {
				when (text[i]) {
					'\\' -> escaped()
					'$' -> reference()
					else -> literal.append(text[i++])
				}
			}
			flushLiteral()
			return parts
		}

		private fun escaped() {
			require(i + 1 < text.length) { "Character to be escaped is missing" }
			literal.append(text[i + 1])
			i += 2
		}

		private fun reference() {
			require(i + 1 < text.length) { "Illegal group reference: group index is missing" }
			val index = if (text[i + 1] == '{') named() else numbered()
			flushLiteral()
			parts += Group(index)
		}

		private fun named(): Int {
			val end = text.indexOf('}', i + 2)
			require(end > i + 2) { "Named capturing group is missing trailing '}'" }
			val name = text.substring(i + 2, end)
			i = end + 1
			return requireNotNull(pattern.namedGroups()[name]) { "No group with name {$name}" }
		}

		private fun numbered(): Int {
			var index = text[i + 1].takeIf { it in '0'..'9' }?.digitToInt()
				?: throw IllegalArgumentException("Illegal group reference")
			require(index <= groupCount) { "No group $index" }
			i += 2
			// Java's rule: later digits join the number only while it still names a group.
			while (i < text.length && text[i] in '0'..'9' && index * DECIMAL + text[i].digitToInt() <= groupCount) {
				index = index * DECIMAL + text[i].digitToInt()
				i++
			}
			return index
		}

		private fun flushLiteral() {
			if (literal.isEmpty()) return
			parts += Literal(literal.toString())
			literal.clear()
		}
	}
}
