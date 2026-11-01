package eu.zeletrik.devcore.tools.diff

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.ChangeDelta
import com.github.difflib.patch.Chunk
import com.github.difflib.patch.DeleteDelta
import com.github.difflib.patch.DeltaType
import com.github.difflib.patch.EqualDelta
import com.github.difflib.patch.InsertDelta
import com.github.difflib.patch.Patch
import eu.zeletrik.devcore.tools.FieldError
import java.util.Locale

data class Diff(val unified: String, val status: String)

object TextDiff {
	const val MAX_LINES = 10_000
	private val MINUS = Char(0x2212)

	// NOTE: the default Myers algorithm on purpose — the linear-space variant emits a changed line as
	// insert-then-delete. Its worst case at the line cap (~2 s, ~140 MiB) is accepted.
	fun diff(original: String, changed: String, context: Int, ignoreCase: Boolean, ignoreWhitespace: Boolean): Diff {
		val a = lines(original) ?: throw IllegalArgumentException(TOO_MANY)
		val b = lines(changed) ?: throw FieldError("changed", TOO_MANY)
		fun normal(line: String): String {
			val spaced = if (ignoreWhitespace) line.trim().replace(whitespace, " ") else line
			return if (ignoreCase) spaced.lowercase(Locale.ROOT) else spaced
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

	private val whitespace = Regex("\\s+")

	private const val TOO_MANY = "Too many lines to compare (max 10,000)"

	/** Lines without one trailing newline; null when there are more than [MAX_LINES]. */
	private fun lines(text: String): List<String>? {
		val body = text.removeSuffix("\n")
		val lines = if (body.isEmpty() && text.length <= 1) emptyList() else body.split("\n")
		return lines.takeIf { it.size <= MAX_LINES }
	}

	/** The same deltas over the original lines, so the unified diff prints what the user wrote. */
	private fun asWritten(patch: Patch<String>, a: List<String>, b: List<String>): Patch<String> = Patch<String>().apply {
		patch.deltas.forEach { delta ->
			val source = Chunk(
				delta.source.position,
				a.subList(delta.source.position, delta.source.position + delta.source.size()),
			)
			val target = Chunk(
				delta.target.position,
				b.subList(delta.target.position, delta.target.position + delta.target.size()),
			)
			addDelta(
				when (delta.type) {
					DeltaType.DELETE -> DeleteDelta(source, target)
					DeltaType.INSERT -> InsertDelta(source, target)
					DeltaType.EQUAL -> EqualDelta(source, target)
					else -> ChangeDelta(source, target)
				},
			)
		}
	}
}
