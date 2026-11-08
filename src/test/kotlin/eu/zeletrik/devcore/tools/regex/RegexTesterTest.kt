package eu.zeletrik.devcore.tools.regex

import eu.zeletrik.devcore.tools.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegexTesterTest {
	private fun list(pattern: String, text: String, vararg flags: RegexFlag) =
		RegexTester.list(RegexTester.compile(pattern, flags.toSet()), text, RegexTester.deadline())

	private fun replace(pattern: String, text: String, replacement: String) =
		RegexTester.replace(RegexTester.compile(pattern, emptySet()), text, replacement, RegexTester.deadline())

	@Test
	fun `matches with positions`() {
		assertEquals("2 matches\n#1 [1, 2) \"1\"\n#2 [3, 5) \"22\"", list("\\d+", "a1b22"))
		assertEquals("1 match\n#1 [1, 2) \"b\"", list("b", "abc"))
		assertEquals("0 matches", list("x", "abc"))
	}

	@Test
	fun `numbered and named groups, including one that did not take part`() {
		assertEquals(
			"""
			2 matches
			#1 [0, 7) "2026-10"
			  y: [0, 4) "2026"
			  2: [5, 7) "10"
			  3: no match
			#2 [8, 16) "1999-01x"
			  y: [8, 12) "1999"
			  2: [13, 15) "01"
			  3: [15, 16) "x"
			""".trimIndent(),
			list("(?<y>\\d{4})-(\\d{2})(x)?", "2026-10 1999-01x"),
		)
	}

	@Test
	fun `positions are UTF-16 offsets and zero-length matches are listed`() {
		assertTrue("[2, 3)" in list("a", "😀a😀a") && "[5, 6)" in list("a", "😀a😀a"))
		assertEquals("3 matches\n#1 [0, 0) \"\"\n#2 [1, 1) \"\"\n#3 [2, 2) \"\"", list("a*", "bb"))
	}

	@Test
	fun flags() {
		assertTrue(list("é", "É", RegexFlag.IGNORE_CASE).startsWith("1 match"))
		assertTrue("[2, 3)" in list("^b", "a\nb", RegexFlag.MULTILINE))
		assertTrue("[0, 3)" in list("a.b", "a\nb", RegexFlag.DOT_ALL))
		assertTrue("[0, 2)" in list("a b # note", "ab", RegexFlag.COMMENTS))
	}

	@Test
	fun `replacement follows Java's syntax`() {
		assertEquals("x at ann", replace("(\\w+)@(\\w+)", "ann@x", "$2 at $1"))
		assertEquals("<ann>", replace("(?<u>\\w+)@\\w+", "ann@x", "<\${u}>"))
		assertEquals("a$", replace("\\d", "a1", "\\$"))
		assertEquals("ab", replace("\\d", "a1b2", ""))
		assertEquals("ann@x", replace("(\\w+)@(\\w+)", "ann@x", "$0"))
		assertEquals("x1", replace("(\\w+)@(\\w+)", "ann@x", "$21"))
		assertEquals("j", replace("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)", "abcdefghij", "$10"))
		assertEquals("[]", replace("(a)|(b)", "b", "[$1]"))
	}

	@Test
	fun `a bad replacement names the problem`() {
		mapOf(
			"$" to "Illegal group reference: group index is missing",
			"$3" to "No group 3",
			"\${x}" to "No group with name {x}",
			"\${x" to "Named capturing group is missing trailing '}'",
			"\\" to "Character to be escaped is missing",
			"\$a" to "Illegal group reference",
		).forEach { (bad, message) ->
			assertEquals(message, assertFailsWith<IllegalArgumentException>(bad) { replace("(a)(b)", "ab", bad) }.message)
		}
	}

	@Test
	fun `replacement stops at the output cap and on the deadline`() {
		val pattern = RegexTester.compile("a", emptySet())
		val tooLarge = assertFailsWith<IllegalArgumentException> {
			RegexTester.replace(pattern, "a".repeat(1_000), "x".repeat(5_001), RegexTester.deadline())
		}
		assertEquals("Output is too large (max 5,000,000 characters)", tooLarge.message)
		val atCap = RegexTester.replace(pattern, "a".repeat(1_000), "x".repeat(5_000), RegexTester.deadline())
		assertEquals(5_000_000, atCap.length)
		assertFailsWith<RegexTester.Timeout> { RegexTester.replace(pattern, "aaa", "b", System.nanoTime() - 1) }
	}

	@Test
	fun `values over 200 characters are cut, 199 and 200 are shown in full`() {
		listOf(199, 200).forEach { assertEquals("#1 [0, $it) \"${"a".repeat(it)}\"", list("a+", "a".repeat(it)).lines()[1]) }
		assertEquals("#1 [0, 201) \"${"a".repeat(200)}…\"", list("a+", "a".repeat(201)).lines()[1])
		assertEquals("  1: [0, 300) \"${"a".repeat(200)}…\"", list("(a+)", "a".repeat(300)).lines()[2])
	}

	@Test
	fun `an invalid pattern names the reason and index`() {
		val e = assertFailsWith<FieldError> { RegexTester.compile("a(b", emptySet()) }
		assertEquals("pattern", e.field)
		assertEquals("Unclosed group near index 3", e.message)
	}

	@Test
	fun `listing stops at 1000 matches - below, at and beyond`() {
		assertFalse("more" in list("a", "a".repeat(999)))
		val thousand = list("a", "a".repeat(1000))
		assertTrue(thousand.startsWith("1000 matches") && "#1000 " in thousand && "more" !in thousand)
		val beyond = list("a", "a".repeat(1001)).lines()
		assertEquals("1001 matches", beyond.first())
		assertEquals("… and 1 more", beyond.last())
		assertEquals(1002, beyond.size)
	}

	@Test
	fun `evaluation finishes normally within the budget and is stopped beyond it`() {
		assertTrue(list("\\w+", "ab ".repeat(30_000)).startsWith("30000 matches"))
		val started = System.nanoTime()
		assertFailsWith<RegexTester.Timeout> { list("(.*,){15}x", "a,".repeat(30)) }
		assertTrue(System.nanoTime() - started < 3_000_000_000L)
	}

	@Test
	fun `counted repetitions may multiply to at most ten million, so a loop that reads nothing can't spin`() {
		// The last one hides nothing: a newline ends the comment that the unclosed \p{ is in.
		val refused = listOf(
			"(?:(?:){2}){1000000000}", "a{10000}b{1001}", "a{1,99999999999999999999}", "(?x)#\\p{\n(?:(?:){9}){9999999}",
		)
		refused.forEach { pattern ->
			val error = assertFailsWith<FieldError> { RegexTester.compile(pattern, emptySet()) }
			assertEquals("pattern" to RegexTester.TOO_MANY_REPETITIONS, error.field to error.message, pattern)
		}
		val started = System.nanoTime()
		assertEquals("2 matches", list("(?:(?:){10000}){1000}", "a").lines().first())
		assertTrue(System.nanoTime() - started < 1_000_000_000L)
		// Escaped braces, code points and properties aren't counts.
		assertEquals("1 match", list("\\{100000000}", "{100000000}").lines().first())
		assertEquals("1 match", list("\\x{10000}{1000}\\p{L}{3}", "𐀀".repeat(1000) + "abc").lines().first())
	}
}
