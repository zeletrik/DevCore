package eu.zeletrik.devcore.tools.diff

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.Chunk
import com.github.difflib.patch.DeltaType
import com.github.difflib.patch.Patch
import eu.zeletrik.devcore.tools.FieldError
import eu.zeletrik.devcore.tools.lowercaseLinear

/** A unified diff and the one-line summary shown above it: `No differences` or `+added −removed lines`. */
data class Diff(val unified: String, val status: String)

/** Line-by-line diff of two texts as a unified patch; each side is capped at [MAX_LINES] lines. */
object TextDiff {
	private const val MAX_LINES = 10_000
	private const val TOO_MANY = "Too many lines to compare (max 10,000)"
	private val MINUS = Char(0x2212)
	private val whitespace = Regex("\\s+")

	// NOTE: the default Myers algorithm on purpose — the linear-space variant emits a changed line as
	// insert-then-delete. Its worst case at the line cap (~2 s, ~140 MiB) is accepted.
	fun diff(original: String, changed: String, context: Int, ignoreCase: Boolean, ignoreWhitespace: Boolean): Diff {
		val a = lines(original) ?: throw IllegalArgumentException(TOO_MANY)
		val b = lines(changed) ?: throw FieldError("changed", TOO_MANY)
		fun normal(line: String): String {
			val spaced = if (ignoreWhitespace) line.trim().replace(whitespace, " ") else line
			return if (ignoreCase) spaced.lowercaseLinear() else spaced
		}
		// Each line is normalized once and the normalized lists are diffed; the hunks then show the lines as written.
		val patch = if (ignoreCase || ignoreWhitespace) {
			asWritten(DiffUtils.diff(a.map(::normal), b.map(::normal)), a, b)
		} else {
			DiffUtils.diff(a, b)
		}
		if (patch.deltas.isEmpty()) return Diff("", "No differences")
		val added = patch.deltas.filter { it.type != DeltaType.DELETE }.sumOf { it.target.size() }
		val removed = patch.deltas.filter { it.type != DeltaType.INSERT }.sumOf { it.source.size() }
		val unified = UnifiedDiffUtils.generateUnifiedDiff("Original", "Changed", a, patch, context).joinToString("\n")
		return Diff(unified, "+$added $MINUS$removed lines")
	}

	/** Lines without one trailing newline; null when there are more than [MAX_LINES]. */
	private fun lines(text: String): List<String>? {
		val body = text.removeSuffix("\n")
		val lines = if (body.isEmpty()) emptyList() else body.split("\n")
		return lines.takeIf { it.size <= MAX_LINES }
	}

	/** The same deltas over the original lines, so the unified diff prints what the user wrote. */
	private fun asWritten(patch: Patch<String>, a: List<String>, b: List<String>) = Patch<String>().apply {
		patch.deltas.forEach { addDelta(it.withChunks(it.source.over(a), it.target.over(b))) }
	}

	/** This chunk's span taken from [lines] instead of the normalized copies it was computed on. */
	private fun Chunk<String>.over(lines: List<String>) = Chunk(position, lines.subList(position, position + size()))
}
