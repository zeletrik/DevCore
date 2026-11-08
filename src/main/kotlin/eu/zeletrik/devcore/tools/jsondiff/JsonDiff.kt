package eu.zeletrik.devcore.tools.jsondiff

import com.github.difflib.DiffUtils
import com.github.difflib.algorithm.DiffAlgorithmListener
import com.github.difflib.algorithm.myers.MyersDiff
import com.github.difflib.patch.AbstractDelta
import eu.zeletrik.devcore.shell.BoundedWriter
import eu.zeletrik.devcore.tools.FieldError
import eu.zeletrik.devcore.tools.json.BYTE_ORDER_MARK
import eu.zeletrik.devcore.tools.json.JsonFormatter
import eu.zeletrik.devcore.tools.json.LoneSurrogates
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.Arrays

/** Work units for matching array items over one whole comparison; spending them all takes about 0.4 s. */
private const val MAX_ARRAY_WORK = 60_000_000L
private val ARROW = Char(0x2192)
private val plainName = Regex("[A-Za-z_][A-Za-z0-9_]*")

/** How numbers match: by value, so `1`, `1.0` and `1e0` are equal, or only when they are written the same way. */
enum class Numbers(val label: String) { BY_VALUE("By value"), AS_WRITTEN("As written") }

/** One line per difference, and the summary shown above them: `No differences` or `3 differences: 1 added, …`. */
data class Differences(val text: String, val status: String)

/**
 * Compares two JSON documents as values: key order and formatting don't count, and arrays are matched item by item the
 * way a line diff matches lines, so an inserted item is one difference. Each difference is one line with its JSONPath,
 * `+` for added, `-` for removed and `~` for changed. Removed and changed values are found at their path in the
 * original, added ones at their path in the changed document. Values are compact JSON, with numbers as written.
 */
object JsonDiff {
	private val mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()

	/**
	 * The differences from [original] to [changed], or null while either holds no JSON value. A side with text is
	 * parsed even then, so its error shows before the other side is filled in.
	 */
	fun diff(original: String, changed: String, numbers: Numbers = Numbers.BY_VALUE): Differences? {
		val values = Values(numbers)
		val a = parse(original, values)
		val b = try {
			parse(changed, values)
		} catch (e: IllegalArgumentException) {
			throw FieldError("changed", e.message ?: "Invalid JSON", e)
		}
		if (a == null || b == null) return null
		return Walk().apply { compare(a, b) }.result()
	}

	private fun parse(text: String, values: Values): Node? = try {
		mapper.createParser(text.removePrefix(BYTE_ORDER_MARK)).use { parser ->
			read(parser, values)?.also {
				if (parser.nextToken() != null) {
					throw JsonFormatter.invalid(parser.currentTokenLocation(), "unexpected content after the JSON value")
				}
			}
		}
	} catch (e: JacksonException) {
		throw JsonFormatter.invalid(e)
	}

	/** One value, read without recursion so that nesting costs no stack; null when the text holds none. */
	private fun read(parser: JsonParser, values: Values): Node? {
		val open = ArrayDeque<Container>()
		var token = parser.nextToken()
		while (token != null) {
			val value = when {
				token.isStructStart -> {
					open.addLast(Container(token == JsonToken.START_OBJECT))
					null
				}

				token == JsonToken.PROPERTY_NAME -> {
					open.last().name = parser.currentName()
					null
				}

				token.isStructEnd -> open.removeLast().close(values)
				else -> values.scalar(parser, token)
			}
			if (value != null) {
				val parent = open.lastOrNull() ?: return value
				parent.add(value)
			}
			token = parser.nextToken()
		}
		return null
	}
}

/** A parsed value. Equal values get equal ids, across both documents, so comparing two subtrees costs nothing. */
private sealed class Node(val id: Int)

/** A string, number, boolean or null as compact JSON; numbers stay as written. */
private class Scalar(val json: String, id: Int) : Node(id)

/** An object, its members in the order written. */
private class Obj(val members: Map<String, Node>, id: Int) : Node(id)

/** An array, its items in order. */
private class Arr(val items: List<Node>, id: Int) : Node(id)

/** An object or array whose members are still being read. */
private class Container(private val isObject: Boolean) {
	/** The name of the member whose value comes next. */
	var name = ""
	private val members = LinkedHashMap<String, Node>()
	private val items = ArrayList<Node>()

	fun add(node: Node) {
		if (isObject) members[name] = node else items += node
	}

	fun close(values: Values): Node = if (isObject) {
		val names = members.keys.toTypedArray().apply { sort() }
		Obj(members, values.id(Shape(names, IntArray(names.size) { members.getValue(names[it]).id })))
	} else {
		Arr(items, values.id(Shape(null, IntArray(items.size) { items[it].id })))
	}
}

/**
 * What makes two containers equal: an object's member names in sorted order with their values' ids, or an array's item
 * ids (and no names). It is comparable, so even keys crafted to share a hash code are found in logarithmic time.
 */
private class Shape(private val names: Array<String>?, private val ids: IntArray) : Comparable<Shape> {
	override fun equals(other: Any?) =
		other is Shape && names.contentEquals(other.names) && ids.contentEquals(other.ids)

	override fun hashCode() = 31 * names.contentHashCode() + ids.contentHashCode()

	override fun compareTo(other: Shape) = Arrays.compare(names, other.names).takeIf { it != 0 }
		?: Arrays.compare(ids, other.ids)
}

/**
 * Hands out one id per distinct value. Scalars written the same way share one instance, so a repeated `0` or `true`
 * costs nothing; by value, `1` and `1.0` are two instances with one id.
 */
private class Values(private val numbers: Numbers) {
	private val scalars = HashMap<String, Scalar>()
	private val keys = HashMap<String, Int>()
	private val shapes = HashMap<Shape, Int>()
	private var next = 0

	fun id(shape: Shape) = shapes.getOrPut(shape) { next++ }

	fun scalar(parser: JsonParser, token: JsonToken): Scalar {
		val json = if (token == JsonToken.VALUE_STRING) LoneSurrogates.escaped(parser.string) else parser.string
		return scalars.getOrPut(json) {
			val key = if (token.isNumeric && numbers == Numbers.BY_VALUE) value(json) else json
			Scalar(json, keys.getOrPut(key) { next++ })
		}
	}

	/**
	 * The number without trailing zeros, written by BigDecimal, which writes each value one way only. It starts with a
	 * digit or a minus sign, so it can't be taken for a string, which is quoted, or for true, false or null.
	 */
	private fun value(literal: String) = try {
		BigDecimal(literal).stripTrailingZeros().toString()
	} catch (_: NumberFormatException) {
		// NOTE: an exponent past BigDecimal's range (1e9999999999), or pushed past it by stripping the zeros
		// (100e2147483647), is matched as written; comparing it by value would take a decimal type with an unbounded
		// exponent.
		literal
	} catch (_: ArithmeticException) {
		literal
	}
}

/** One walk over both trees that writes a line per difference. */
private class Walk {
	private val out = BoundedWriter()
	private val path = StringBuilder("$")
	private val work = ArrayWork()
	private var added = 0
	private var removed = 0
	private var changed = 0

	fun compare(a: Node, b: Node) {
		when {
			a.id == b.id -> Unit
			a is Obj && b is Obj -> members(a, b)
			a is Arr && b is Arr -> items(a.items, b.items)
			else -> line('~', a, b)
		}
	}

	private fun members(a: Obj, b: Obj) {
		a.members.forEach { (name, old) ->
			val new = b.members[name]
			when {
				new == null -> at(step(name)) { line('-', old) }
				new.id != old.id -> at(step(name)) { compare(old, new) }
			}
		}
		b.members.forEach { (name, new) -> if (name !in a.members) at(step(name)) { line('+', new) } }
	}

	/**
	 * Past their common start and end, the items are matched by Myers' diff over their ids. Items with nothing in
	 * common have nothing to match, so they are paired in order right away, which is also what the diff would give.
	 */
	private fun items(a: List<Node>, b: List<Node>) {
		val shorter = minOf(a.size, b.size)
		var head = 0
		while (head < shorter && a[head].id == b[head].id) head++
		var tail = 0
		while (tail < shorter - head && a[a.size - 1 - tail].id == b[b.size - 1 - tail].id) tail++
		val old = a.subList(head, a.size - tail)
		val new = b.subList(head, b.size - tail)
		val deltas = if (shareAny(old, new)) work.match(old, new) else null
		if (deltas == null) {
			replace(old, head, new, head)
		} else {
			deltas.forEach {
				replace(it.source.lines, head + it.source.position, it.target.lines, head + it.target.position)
			}
		}
	}

	private fun shareAny(a: List<Node>, b: List<Node>): Boolean {
		val ids = a.mapTo(HashSet()) { it.id }
		return b.any { it.id in ids }
	}

	/** [old] items from index [i] replaced by [new] ones from index [j]: paired in order, the rest removed or added. */
	private fun replace(old: List<Node>, i: Int, new: List<Node>, j: Int) {
		val paired = minOf(old.size, new.size)
		for (k in 0 until paired) at("[${i + k}]") { compare(old[k], new[k]) }
		for (k in paired until old.size) at("[${i + k}]") { line('-', old[k]) }
		for (k in paired until new.size) at("[${j + k}]") { line('+', new[k]) }
	}

	/** A member's step in a JSONPath: `.name` for a plain ASCII name, `["…"]` with JSON escapes for any other. */
	private fun step(name: String) = if (plainName.matches(name)) ".$name" else "[${LoneSurrogates.escaped(name)}]"

	private inline fun at(step: String, block: () -> Unit) {
		val length = path.length
		path.append(step)
		block()
		path.setLength(length)
	}

	private fun line(sign: Char, value: Node, new: Node? = null) {
		if (added + removed + changed > 0) out.write("\n")
		when (sign) {
			'+' -> added++
			'-' -> removed++
			else -> changed++
		}
		out.write("$sign ")
		out.append(path).write(": ")
		write(value)
		if (new != null) {
			out.write(" $ARROW ")
			write(new)
		}
	}

	/** [node] as compact JSON, members in the order written. */
	private fun write(node: Node) {
		when (node) {
			is Scalar -> out.write(node.json)
			is Arr -> {
				out.write("[")
				node.items.forEachIndexed { i, item ->
					if (i > 0) out.write(",")
					write(item)
				}
				out.write("]")
			}

			is Obj -> {
				out.write("{")
				node.members.entries.forEachIndexed { i, (name, value) ->
					if (i > 0) out.write(",")
					out.write(LoneSurrogates.escaped(name))
					out.write(":")
					write(value)
				}
				out.write("}")
			}
		}
	}

	fun result(): Differences {
		val total = added + removed + changed
		if (total == 0) return Differences("", "No differences")
		val counts = listOf(added to "added", removed to "removed", changed to "changed")
			.filter { it.first > 0 }
			.joinToString(", ") { (count, kind) -> "$count $kind" }
		val noun = if (total == 1) "difference" else "differences"
		val note = if (work.ranOut) "; some arrays were compared by position" else ""
		return Differences(out.toString(), "$total $noun: $counts$note")
	}
}

/**
 * The cost of matching array items, over one whole comparison: a step of Myers' diff costs one unit per diagonal it
 * visits, and an item comparison one unit. Arrays that share items but differ almost everywhere cost the square of
 * their length, so once [MAX_ARRAY_WORK] units are spent, the arrays still to match are compared by position.
 */
private class ArrayWork : DiffAlgorithmListener {
	/** Stops Myers' diff from inside, the only way out of it. */
	private class OutOfWork : RuntimeException()

	private var units = 0L

	/** Whether some array was compared by position because the work ran out. */
	var ranOut = false
		private set

	/** The diff of [old] against [new], or null once the work has run out. */
	fun match(old: List<Node>, new: List<Node>): List<AbstractDelta<Node>>? {
		if (!ranOut) {
			try {
				return DiffUtils.diff(old, new, MyersDiff<Node>(::same), this).deltas
			} catch (_: OutOfWork) {
				ranOut = true
			}
		}
		return null
	}

	private fun same(a: Node, b: Node): Boolean {
		spend(1)
		return a.id == b.id
	}

	override fun diffStep(value: Int, max: Int) = spend(value + 1L)

	override fun diffStart() = Unit

	override fun diffEnd() = Unit

	private fun spend(cost: Long) {
		units += cost
		if (units > MAX_ARRAY_WORK) throw OutOfWork()
	}
}
