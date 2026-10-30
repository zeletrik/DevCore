package eu.zeletrik.devcore.tools.diff

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.DeltaType
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
			val spaced = if (ignoreWhitespace) line.trim().replace(Regex("\\s+"), " ") else line
			return if (ignoreCase) spaced.lowercase(Locale.ROOT) else spaced
		}
		val patch = DiffUtils.diff(a, b) { x: String, y: String -> normal(x) == normal(y) }
		if (patch.deltas.isEmpty()) return Diff("", "No differences")
		val added = patch.deltas.filter { it.type != DeltaType.DELETE }.sumOf { it.target.size() }
		val removed = patch.deltas.filter { it.type != DeltaType.INSERT }.sumOf { it.source.size() }
		val unified = UnifiedDiffUtils.generateUnifiedDiff("Original", "Changed", a, patch, context).joinToString("\n")
		return Diff(unified, "+$added $MINUS$removed lines")
	}

	private const val TOO_MANY = "Too many lines to compare (max 10,000)"

	/** Lines without one trailing newline; null when there are more than [MAX_LINES]. */
	private fun lines(text: String): List<String>? {
		val body = text.removeSuffix("\n")
		val lines = if (body.isEmpty() && text.length <= 1) emptyList() else body.split("\n")
		return lines.takeIf { it.size <= MAX_LINES }
	}
}
