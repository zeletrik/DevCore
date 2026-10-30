package eu.zeletrik.devcore.tools.regex

import eu.zeletrik.devcore.tools.FieldError
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

enum class RegexFlag(val label: String, val bits: Int) {
	IGNORE_CASE("Ignore case", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
	MULTILINE("Multiline", Pattern.MULTILINE),
	DOT_ALL("Dot matches newline", Pattern.DOTALL),
	COMMENTS("Comments", Pattern.COMMENTS),
}

/** Java's regex flavour, bounded: every evaluation stops once its deadline passes (one bad pattern can't stall the server). */
object RegexTester {
	const val LISTED = 1000
	const val STOPPED = "Stopped after 1 second: the pattern backtracks too much on this text"
	private const val BUDGET_NANOS = 1_000_000_000L

	/** Thrown when evaluation runs past its deadline; deliberately not an IllegalArgumentException. */
	class Timeout : RuntimeException(STOPPED, null, false, false)

	fun deadline() = System.nanoTime() + BUDGET_NANOS

	fun compile(pattern: String, flags: Set<RegexFlag>): Pattern = try {
		Pattern.compile(pattern, flags.fold(0) { bits, flag -> bits or flag.bits })
	} catch (e: PatternSyntaxException) {
		throw FieldError("pattern", "${e.description} near index ${e.index}")
	}

	/** `N matches`, then `#k [start, end) "text"` per match with one indented line per group, at most [LISTED]. */
	fun list(pattern: Pattern, text: String, deadline: Long): String {
		val names = pattern.namedGroups().entries.associate { (name, index) -> index to name }
		val matcher = pattern.matcher(Bounded(text, deadline))
		val lines = mutableListOf<String>()
		var count = 0
		while (matcher.find()) {
			count++
			if (count > LISTED) continue
			lines += "#$count ${span(matcher.start(), matcher.end(), matcher.group())}"
			for (group in 1..matcher.groupCount()) {
				val label = names[group] ?: group.toString()
				lines += if (matcher.start(group) < 0) "  $label: no match"
				else "  $label: ${span(matcher.start(group), matcher.end(group), matcher.group(group))}"
			}
		}
		val header = if (count == 1) "1 match" else "$count matches"
		val more = if (count > LISTED) listOf("… and ${count - LISTED} more") else emptyList()
		return (listOf(header) + lines + more).joinToString("\n")
	}

	/** Every match replaced; `$n` and `${name}` refer to groups and `\$` is a literal dollar. */
	fun replace(pattern: Pattern, text: String, replacement: String, deadline: Long): String = try {
		pattern.matcher(Bounded(text, deadline)).replaceAll(replacement)
	} catch (e: IndexOutOfBoundsException) {
		throw IllegalArgumentException(e.message ?: "Invalid group reference")
	}

	private fun span(start: Int, end: Int, value: String) = "[$start, $end) \"$value\""

	/** The text the matcher reads, checking the clock as it goes (java.util.regex itself can't be interrupted). */
	private class Bounded(private val text: CharSequence, private val deadline: Long) : CharSequence {
		private var reads = 0

		override val length get() = text.length

		override fun get(index: Int): Char {
			if (++reads and 0x3FF == 0 && System.nanoTime() > deadline) throw Timeout()
			return text[index]
		}

		override fun subSequence(startIndex: Int, endIndex: Int) = text.subSequence(startIndex, endIndex)

		override fun toString() = text.toString()
	}

}
