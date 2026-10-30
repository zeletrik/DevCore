package eu.zeletrik.devcore.tools.diff

import eu.zeletrik.devcore.tools.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextDiffTest {
	private val minus = Char(0x2212)

	private fun diff(a: String, b: String, context: Int = 3, ignoreCase: Boolean = false, ignoreWhitespace: Boolean = false) =
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
	fun `ignore options compare normalised lines but show them as written`() {
		assertEquals("No differences", diff("Hello", "hello", ignoreCase = true).status)
		assertEquals("No differences", diff("a b ", " a b", ignoreWhitespace = true).status)
		val shown = diff("Foo Bar", "Baz", ignoreCase = true, ignoreWhitespace = true).unified.lines()
		assertEquals(listOf("-Foo Bar", "+Baz"), shown.drop(3))
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
