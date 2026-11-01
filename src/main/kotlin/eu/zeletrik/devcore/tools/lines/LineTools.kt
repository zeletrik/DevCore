package eu.zeletrik.devcore.tools.lines

import java.util.Arrays
import kotlin.random.Random

/** How lines are ordered; natural order compares digit runs numerically. */
enum class Order(val label: String) {
	AS_IS("As is"), ASCENDING("A→Z"), DESCENDING("Z→A"), NATURAL("Natural"), REVERSE("Reverse"), SHUFFLE("Shuffle"),
}

/** The switches of the Line Tools, applied in declaration order. */
data class LineOptions(
	val trim: Boolean = false,
	val removeEmpty: Boolean = false,
	val removeDuplicates: Boolean = false,
	val ignoreCase: Boolean = false,
	val order: Order = Order.AS_IS,
)

/** Trim, drop empty lines, drop duplicates, then order — in that sequence. Sorting is by code point, not locale. */
object LineTools {
	fun apply(text: String, options: LineOptions, random: Random = Random): String {
		var lines = text.removeSuffix("\n").split("\n")
		val key: (String) -> String = if (options.ignoreCase) { line -> line.lowercase() } else { line -> line }
		if (options.trim) lines = lines.map { it.trim() }
		if (options.removeEmpty) lines = lines.filter { it.isNotEmpty() }
		if (options.removeDuplicates) lines = lines.distinctBy(key)
		lines = when (options.order) {
			Order.AS_IS -> lines
			Order.ASCENDING -> lines.sortedWith { a, b -> byCodePoint(key(a), key(b)) }
			Order.DESCENDING -> lines.sortedWith { a, b -> byCodePoint(key(b), key(a)) }
			Order.NATURAL -> lines.sortedWith { a, b -> natural(key(a), key(b)) }
			Order.REVERSE -> lines.reversed()
			Order.SHUFFLE -> lines.shuffled(random)
		}
		return lines.joinToString("\n")
	}

	private fun byCodePoint(a: String, b: String) = Arrays.compare(a.codePoints().toArray(), b.codePoints().toArray())

	private val chunk = Regex("[0-9]+|[^0-9]+")

	/** Two ASCII digit runs by numeric value without parsing them: fewer significant digits first, then digit by digit. */
	private fun byValue(a: String, b: String): Int {
		val x = a.trimStart('0')
		val y = b.trimStart('0')
		return if (x.length != y.length) x.length.compareTo(y.length) else x.compareTo(y)
	}

	/** Digit runs compare by numeric value, everything else by code point: item2 < item10. */
	private fun natural(a: String, b: String): Int {
		val x = chunk.findAll(a).map { it.value }.toList()
		val y = chunk.findAll(b).map { it.value }.toList()
		for (i in 0 until minOf(x.size, y.size)) {
			val (p, q) = x[i] to y[i]
			val order = if (p[0] in '0'..'9' && q[0] in '0'..'9') byValue(p, q) else byCodePoint(p, q)
			if (order != 0) return order
		}
		return x.size.compareTo(y.size)
	}
}
