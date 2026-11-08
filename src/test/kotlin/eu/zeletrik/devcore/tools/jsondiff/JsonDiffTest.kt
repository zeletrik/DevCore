package eu.zeletrik.devcore.tools.jsondiff

import eu.zeletrik.devcore.shell.OutputTooLarge
import eu.zeletrik.devcore.tools.FieldError
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonDiffTest {
	private val arrow = Char(0x2192)
	private val bs = "\\"
	private val byPosition = "; some arrays were compared by position"

	private fun diff(original: String, changed: String, numbers: Numbers = Numbers.BY_VALUE) =
		assertNotNull(JsonDiff.diff(original, changed, numbers))

	private fun lines(original: String, changed: String, numbers: Numbers = Numbers.BY_VALUE) =
		diff(original, changed, numbers).text.lines()

	private fun status(original: String, changed: String, numbers: Numbers = Numbers.BY_VALUE) =
		diff(original, changed, numbers).status

	/** Odd numbers negated: every other item differs, so the arrays share half their items. */
	private fun interleaved(n: Int) = (1..n).joinToString(",", "[", "]") { if (it % 2 == 0) "$it" else "-$it" }

	private fun count(n: Int) = (1..n).joinToString(",", "[", "]")

	@Test
	fun `each difference is a line with its path, under a summary`() {
		val result = diff(
			"""{"user":{"email":"ann@old.example","phone":"+36 1 234 5678"},"tags":["a","b"]}""",
			"""{"tags":["a","b","beta"],"user":{"email":"ann@new.example"}}""",
		)
		assertEquals("3 differences: 1 added, 1 removed, 1 changed", result.status)
		assertEquals(
			listOf(
				"~ $.user.email: \"ann@old.example\" $arrow \"ann@new.example\"",
				"- $.user.phone: \"+36 1 234 5678\"",
				"+ $.tags[2]: \"beta\"",
			),
			result.text.lines(),
		)
	}

	@Test
	fun `key order and formatting are no difference, at any depth`() {
		val compact = """{"a":1,"b":[{"x":1,"y":[true,null]}]}"""
		val spread = "{\n  \"b\": [ { \"y\": [ true, null ], \"x\": 1 } ],\n\t\"a\": 1\n}"
		assertEquals(Differences("", "No differences"), diff(compact, spread))
	}

	@Test
	fun `one difference is counted in the singular`() {
		assertEquals(Differences("~ $: 1 $arrow 2", "1 difference: 1 changed"), diff("1", "2"))
	}

	@Test
	fun `numbers match by value, exactly`() {
		listOf("1" to "1.0", "1e2" to "100", "1E+2" to "1e2", "-0" to "0", "0.10" to "0.1", "-1.50" to "-15e-1")
			.forEach { (a, b) -> assertEquals("No differences", status(a, b), "$a vs $b") }
		assertEquals(
			listOf("~ $: 12345678901234567890 $arrow 12345678901234567891"),
			lines("12345678901234567890", "12345678901234567891"),
		)
		assertEquals(listOf("~ $: 0.1 $arrow 0.10000000000000001"), lines("0.1", "0.10000000000000001"))
	}

	@Test
	fun `as written, numbers match only when they are written the same way`() {
		assertEquals(listOf("~ $: 1 $arrow 1.0"), lines("1", "1.0", Numbers.AS_WRITTEN))
		assertEquals(listOf("~ $: -0 $arrow 0"), lines("-0", "0", Numbers.AS_WRITTEN))
		assertEquals("No differences", status("[1e2]", "[1e2]", Numbers.AS_WRITTEN))
	}

	@Test
	fun `numbers show as written, even where an equal one is written differently`() {
		assertEquals(listOf("- $.a: 1.0", "+ $.c: 1"), lines("""{"a":1.0,"b":[1.0]}""", """{"b":[1],"c":1}"""))
	}

	@Test
	fun `an exponent past the decimal range is matched as written`() {
		assertEquals("No differences", status("1e9999999999", "1e9999999999"))
		assertEquals(listOf("~ $: 1e9999999999 $arrow 1E9999999999"), lines("1e9999999999", "1E9999999999"))
		assertEquals(listOf("~ $: 100e2147483647 $arrow 1e2147483649"), lines("100e2147483647", "1e2147483649"))
	}

	@Test
	fun `an inserted or removed array item is one difference`() {
		assertEquals(listOf("+ $[0]: 0"), lines("[1,2,3]", "[0,1,2,3]"))
		assertEquals(listOf("- $[1]: 2"), lines("[1,2,3]", "[1,3]"))
		assertEquals(listOf("- $[2]: 1"), lines("[1,1,1]", "[1,1]"))
		assertEquals(listOf("~ $[2]: 2 $arrow 7", "+ $[6]: 6"), lines("[0,1,2,3,4,5]", "[0,1,7,3,4,5,6]"))
		assertEquals("4 differences: 2 added, 2 removed", status("[1,2,3]", "[3,2,1]"))
	}

	@Test
	fun `a replaced item is compared in depth, at its index in Original`() {
		assertEquals(
			listOf("+ $[0]: {\"id\":0}", "~ $[1].n: \"b\" $arrow \"B\""),
			lines("""[{"id":1,"n":"a"},{"id":2,"n":"b"}]""", """[{"id":0},{"id":1,"n":"a"},{"id":2,"n":"B"}]"""),
		)
		assertEquals(
			listOf("+ $.a[0]: 0", "- $.a[1].b[0]: 1"),
			lines("""{"a":[1,{"b":[1,2]}]}""", """{"a":[0,1,{"b":[2]}]}"""),
		)
	}

	@Test
	fun `items with nothing in common are compared in order, at any length`() {
		assertEquals(
			listOf("~ $[0].t: 1 $arrow 2", "~ $[1].t: 1 $arrow 2", "- $[2]: {\"t\":1}"),
			lines("""[{"t":1},{"t":1},{"t":1}]""", """[{"t":2},{"t":2}]"""),
		)
		val records = (1..50_000).joinToString(",", "[", "]") { """{"id":$it,"t":1}""" }
		val touched = diff(records, records.replace("\"t\":1", "\"t\":2"))
		assertEquals("50000 differences: 50000 changed", touched.status)
		assertEquals("~ $[49999].t: 1 $arrow 2", touched.text.lines().last())
	}

	@Test
	fun `a change of type shows both values`() {
		assertEquals(
			listOf("~ $.a: [1,{\"b\":null}] $arrow {\"b\":null}"),
			lines("""{"a":[1,{"b":null}]}""", """{"a":{"b":null}}"""),
		)
		assertEquals(listOf("~ $.a: 1 $arrow \"1\""), lines("""{"a":1}""", """{"a":"1"}"""))
		assertEquals(listOf("~ $: [] $arrow {}"), lines("[]", "{}"))
		assertEquals(listOf("~ $: true $arrow null"), lines("true", "null"))
	}

	@Test
	fun `paths use a dot for plain names and a quoted bracket for any other`() {
		val original = """{"plain_1":1,"a b":2,"é":3,"":4,"q\"t":5,"1st":6,"nl\nx":7,"😀":8,"İ":9}"""
		assertEquals(
			listOf(
				"- $.plain_1: 1",
				"- $[\"a b\"]: 2",
				"- $[\"é\"]: 3",
				"- $[\"\"]: 4",
				"- $[\"q${bs}\"t\"]: 5",
				"- $[\"1st\"]: 6",
				"- $[\"nl${bs}nx\"]: 7",
				"- $[\"😀\"]: 8",
				"- $[\"İ\"]: 9",
			),
			lines(original, "{}"),
		)
		assertEquals(listOf("~ $.a.b[1].c: 1 $arrow 2"), lines("""{"a":{"b":[0,{"c":1}]}}""", """{"a":{"b":[0,{"c":2}]}}"""))
	}

	@Test
	fun `values are compact JSON in the order written, and stay valid JSON`() {
		assertEquals(
			listOf("- $.o: {\"z\":1,\"a\":[true,null,\"x${bs}ty\"],\"é\":\"😀\"}"),
			lines("""{"o":{"z":1,"a":[true,null,"x\ty"],"é":"😀"}}""", "{}"),
		)
		assertEquals(listOf("~ $: \"${bs}uD800\" $arrow \"x\""), lines("\"${bs}ud800\"", "\"x\""))
	}

	@Test
	fun `nothing to compare while a side is blank, but a side with text is still checked`() {
		assertNull(JsonDiff.diff("", "[1]"))
		assertNull(JsonDiff.diff("{}", " \n\t"))
		assertNull(JsonDiff.diff("", ""))
		assertEquals("changed", assertFailsWith<FieldError> { JsonDiff.diff("", "[1") }.field)
		assertFalse(assertFailsWith<IllegalArgumentException> { JsonDiff.diff("[1", "") } is FieldError)
	}

	@Test
	fun `invalid JSON shows on its own side, with the line and column`() {
		val text = "{\n  \"a\": tru\n}"
		val original = assertFailsWith<IllegalArgumentException> { JsonDiff.diff(text, "{}") }
		assertFalse(original is FieldError)
		assertTrue(original.message!!.startsWith("Invalid JSON at line 2, column "), original.message)
		val changed = assertFailsWith<FieldError> { JsonDiff.diff("{}", text) }
		assertEquals("changed" to original.message, changed.field to changed.message)
	}

	@Test
	fun `a second value or a duplicate key is refused`() {
		assertEquals(
			"Invalid JSON at line 1, column 4: unexpected content after the JSON value",
			assertFailsWith<IllegalArgumentException> { JsonDiff.diff("{} {}", "{}") }.message,
		)
		val duplicate = assertFailsWith<FieldError> { JsonDiff.diff("{}", """{"a":1,"a":2}""") }.message!!
		assertTrue(duplicate.startsWith("Invalid JSON at line 1, column ") && "\"a\"" in duplicate, duplicate)
	}

	@Test
	fun `a byte order mark before the JSON is ignored`() {
		assertEquals("No differences", status(Char(0xFEFF) + "[1]", "[1]"))
	}

	@Test
	fun `nesting up to 500 levels is compared, deeper is refused`() {
		val deep = "[".repeat(500) + "]".repeat(500)
		assertEquals(listOf("+ $" + "[0]".repeat(500) + ": 1"), lines(deep, deep.replace("[]", "[1]")))
		val tooDeep = "[".repeat(501) + "]".repeat(501)
		val e = assertFailsWith<IllegalArgumentException> { JsonDiff.diff(tooDeep, "1") }
		assertTrue(e.message!!.startsWith("Invalid JSON"), e.message)
	}

	@Test
	fun `an output past the cap is refused while it is written`() {
		val zeros = "[" + "0,".repeat(399_999) + "0]"
		val ones = "[" + "1,".repeat(399_999) + "1]"
		val e = assertFailsWith<OutputTooLarge> { JsonDiff.diff(zeros, ones) }
		assertEquals("Output is too large (max 5,000,000 characters)", e.message)
	}

	@Test
	fun `arrays that share items but differ almost everywhere are compared by position, and the summary says so`() {
		val matched = diff(count(2_000), interleaved(2_000))
		assertEquals("1000 differences: 1000 changed", matched.status)
		val positional = diff(count(20_000), interleaved(20_000))
		assertEquals("10000 differences: 10000 changed$byPosition", positional.status)
		assertEquals(listOf("~ $[0]: 1 $arrow -1", "~ $[2]: 3 $arrow -3"), positional.text.lines().take(2))
	}

	@Test
	fun `the slowest arrays at the input cap take well under two seconds`() {
		val original = count(120_000)
		val changed = interleaved(120_000)
		assertTrue(changed.length < 1_000_000)
		var result: Differences? = null
		val nanos = measureNanoTime { result = JsonDiff.diff(original, changed) }
		assertTrue(nanos < 2_000_000_000, "took ${nanos / 1_000_000} ms")
		assertTrue(result!!.status.endsWith(byPosition))
	}
}
