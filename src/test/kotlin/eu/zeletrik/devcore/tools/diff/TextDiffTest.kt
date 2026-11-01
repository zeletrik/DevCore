package eu.zeletrik.devcore.tools.diff

import eu.zeletrik.devcore.tools.FieldError
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TextDiffTest {
	private val minus = Char(0x2212)

	private fun diff(
		a: String,
		b: String,
		context: Int = 3,
		ignoreCase: Boolean = false,
		ignoreWhitespace: Boolean = false,
	) =
		TextDiff.diff(a, b, context, ignoreCase, ignoreWhitespace)

	private val fifty = (1..50).joinToString("\n") { "L$it" }
	private val fiftyChanged = fifty.replace("L25\nL26", "X25\nX26")

	@Test
	fun `unified diff and status`() {
		val result = diff("a\nb\nc", "a\nB\nc")
		assertEquals("--- Original\n+++ Changed\n@@ -1,3 +1,3 @@\n a\n-b\n+B\n c", result.unified)
		assertEquals("+1 ${minus}1 lines", result.status)
	}

	@Test
	fun `context lines - default, and both bounds`() {
		assertEquals("@@ -22,8 +22,8 @@", diff(fifty, fiftyChanged).unified.lines()[2])
		assertEquals("@@ -25,2 +25,2 @@", diff(fifty, fiftyChanged, context = 0).unified.lines()[2])
		assertEquals("@@ -5,42 +5,42 @@", diff(fifty, fiftyChanged, context = 20).unified.lines()[2])
	}

	@Test
	fun `no differences, including a trailing newline`() {
		assertEquals(Diff("", "No differences"), diff("x\ny", "x\ny"))
		assertEquals(Diff("", "No differences"), diff("x\ny", "x\ny\n"))
	}

	@Test
	fun `ignore options compare normalized lines but show them as written`() {
		assertEquals("No differences", diff("Hello", "hello", ignoreCase = true).status)
		assertEquals("No differences", diff("a b ", " a b", ignoreWhitespace = true).status)
		val shown = diff("Foo Bar", "Baz", ignoreCase = true, ignoreWhitespace = true).unified.lines()
		assertEquals(listOf("-Foo Bar", "+Baz"), shown.drop(3))
		val context = diff("A  b\nkeep\nold", "a b\nkeep\nnew", ignoreCase = true, ignoreWhitespace = true).unified.lines()
		assertTrue(" A  b" in context && "-old" in context && "+new" in context, context.toString())
	}

	@Test
	fun `only additions or only removals count as such`() {
		assertEquals("+0 ${minus}1 lines", diff("a\nb", "a").status)
		assertEquals("+1 ${minus}0 lines", diff("", "a").status)
	}

	@Test
	fun `both ignore options on 10,000 lines finish quickly`() {
		val original = (1..10_000).joinToString("\n") { "Line  $it of the ORIGINAL text" }
		val changed = (1..10_000).joinToString("\n") { "changed line $it, entirely different" }
		var result: Diff? = null
		val nanos = measureNanoTime { result = diff(original, changed, ignoreCase = true, ignoreWhitespace = true) }
		assertTrue(nanos < 5_000_000_000, "took ${nanos / 1_000_000} ms")
		assertTrue(result!!.unified.contains("-Line  1 of the ORIGINAL text"))
	}

	@Test
	fun `line cap - within, at and beyond`() {
		fun lines(n: Int) = (1..n).joinToString("\n") { "line $it" }
		assertEquals("+1 ${minus}1 lines", diff(lines(9_999), lines(9_999).replace("line 5000\n", "five\n")).status)
		assertEquals("+1 ${minus}1 lines", diff(lines(10_000), lines(10_000).replace("line 5000\n", "five\n")).status)
		val e = assertFailsWith<FieldError> { diff(lines(10_000), lines(10_001)) }
		assertEquals("changed", e.field)
		assertEquals("Too many lines to compare (max 10,000)", e.message)
		assertFailsWith<IllegalArgumentException> { diff(lines(10_001), "x") }
	}
}
