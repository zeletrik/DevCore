package eu.zeletrik.devcore.tools.regex

import eu.zeletrik.devcore.shell.BoundedWriter
import eu.zeletrik.devcore.tools.FieldError
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Matching checks the clock once every 1024 character reads. */
private const val CLOCK_CHECK_MASK = 0x3FF

/** The most iterations a pattern's counted repetitions may multiply to; about 40 ms of work at worst. */
private const val MAX_REPETITIONS = 10_000_000L

/** The `java.util.regex` flags offered; ignore case is Unicode-aware. */
enum class RegexFlag(val label: String, val bits: Int) {
	IGNORE_CASE("Ignore case", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE),
	MULTILINE("Multiline", Pattern.MULTILINE),
	DOT_ALL("Dot matches newline", Pattern.DOTALL),
	COMMENTS("Comments", Pattern.COMMENTS),
}

/**
 * Java's regex flavor, bounded: every evaluation stops once its deadline passes (one bad pattern can't stall the
 * server).
 */
object RegexTester {
	private const val LISTED = 1000
	private const val SHOWN = 200
	const val STOPPED = "Stopped after 1 second: the pattern backtracks too much on this text"
	const val TOO_MANY_REPETITIONS = "Repetition counts too large: the {n} counts may multiply to at most 10,000,000"
	private const val BUDGET_NANOS = 1_000_000_000L
	private val repetition = Regex("""\{(\d+)(?:,(\d*))?}""")

	/**
	 * Thrown when evaluation runs past its deadline. Not an IllegalArgumentException on purpose: the view reports it on
	 * the pattern, and `optional` would otherwise swallow it as the replacement field's error. Built without a stack
	 * trace, since it is thrown from the hot character-read path.
	 */
	class Timeout : RuntimeException(STOPPED, null, false, false)

	/** One budget for a whole evaluation: the view passes the same deadline to [list] and [replace]. */
	fun deadline() = System.nanoTime() + BUDGET_NANOS

	fun compile(pattern: String, flags: Set<RegexFlag>): Pattern {
		checkRepetitions(pattern)
		return try {
			Pattern.compile(pattern, flags.fold(0) { bits, flag -> bits or flag.bits })
		} catch (e: PatternSyntaxException) {
			throw FieldError("pattern", "${e.description} near index ${e.index}", e)
		}
	}

	/**
	 * The deadline is checked as the text is read, but a counted repetition of something that can match empty, such as
	 * `(?:(?:){2}){1000000000}`, loops without reading: it would spin forever. `*` and `+` stop on an empty iteration,
	 * so only `{n}`, `{n,}` and `{n,m}` are counted, and the product of all of them bounds any nesting. Text in
	 * classes, quotes and comments counts too, which only makes the check stricter.
	 */
	private fun checkRepetitions(pattern: String) {
		var product = 1L
		var i = 0
		while (i < pattern.length) {
			when (pattern[i]) {
				'\\' -> i = escapeEnd(pattern, i)
				'{' -> repetition.matchAt(pattern, i)?.let { match ->
					val (min, max) = match.destructured
					val bound = (max.ifEmpty { min }).toLongOrNull()?.coerceAtLeast(1) ?: Long.MAX_VALUE
					if (bound > MAX_REPETITIONS / product) throw FieldError("pattern", TOO_MANY_REPETITIONS)
					product *= bound
					i = match.range.last
				}
			}
			i++
		}
	}

	/** The last index of the escape at [start]: `\x{…}`, `\p{…}`, `\P{…}` and `\N{…}` run to their brace. */
	private fun escapeEnd(pattern: String, start: Int): Int {
		val braced = pattern.getOrNull(start + 1)?.let { it in "xpPN" } == true && pattern.getOrNull(start + 2) == '{'
		if (!braced) return start + 1
		// A newline also ends it: in comments mode it ends a `#` comment the escape may have been written in.
		val end = pattern.indexOfAny(charArrayOf('}', '\n'), start + 2)
		return if (end < 0) pattern.length else end
	}

	/** `N matches`, then `#k [start, end) "text"` per match with one indented line per group, at most [LISTED]. */
	fun list(pattern: Pattern, text: String, deadline: Long): String {
		val names = pattern.namedGroups().entries.associate { (name, index) -> index to name }
		val matcher = pattern.matcher(Bounded(text, deadline))
		val body = BoundedWriter()
		var count = 0
		while (matcher.find()) {
			count++
			if (count > LISTED) continue
			body.append("\n#$count ${span(matcher, 0)}")
			for (group in 1..matcher.groupCount()) {
				val label = names[group] ?: group.toString()
				body.append("\n  $label: ${span(matcher, group)}")
			}
		}
		val header = if (count == 1) "1 match" else "$count matches"
		if (count > LISTED) body.append("\n… and ${count - LISTED} more")
		return header + body
	}

	/**
	 * Every match replaced; `$n` and `${name}` refer to groups and a backslash escapes the next character, as in
	 * Java. Built piece by piece, so the deadline is checked per match and the output cap holds.
	 */
	fun replace(pattern: Pattern, text: String, replacement: String, deadline: Long): String {
		val matcher = pattern.matcher(Bounded(text, deadline))
		val parts = Replacement.parse(replacement, pattern, matcher.groupCount())
		val out = BoundedWriter()
		var last = 0
		while (matcher.find()) {
			if (System.nanoTime() > deadline) throw Timeout()
			out.append(text, last, matcher.start())
			parts.forEach { part ->
				when (part) {
					is Replacement.Literal -> out.append(part.text)
					is Replacement.Group -> matcher.group(part.index)?.let { out.append(it) }
				}
			}
			last = matcher.end()
		}
		out.append(text, last, text.length)
		return out.toString()
	}

	/** `[start, end) "text"` for [group] (0 is the whole match), or `no match` when it did not take part. */
	private fun span(matcher: Matcher, group: Int) =
		if (matcher.start(group) < 0) "no match" else span(
			matcher.start(group),
			matcher.end(group),
			matcher.group(group)
		)

	/** Long values are cut to [SHOWN] characters; the offsets still give their full extent. */
	private fun span(start: Int, end: Int, value: String): String {
		val shown = if (value.length > SHOWN) value.take(SHOWN) + "…" else value
		return "[$start, $end) \"$shown\""
	}

	/** The text the matcher reads, checking the clock as it goes (java.util.regex itself can't be interrupted). */
	private class Bounded(private val text: CharSequence, private val deadline: Long) : CharSequence {
		private var reads = 0

		override val length get() = text.length

		override fun get(index: Int): Char {
			if ((++reads and CLOCK_CHECK_MASK) == 0 && System.nanoTime() > deadline) throw Timeout()
			return text[index]
		}

		override fun subSequence(startIndex: Int, endIndex: Int) = text.subSequence(startIndex, endIndex)

		override fun toString() = text.toString()
	}
}
