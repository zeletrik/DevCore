package eu.zeletrik.devcore.tools.jsonpath

import eu.zeletrik.devcore.tools.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonPathTesterTest {
	private val store = """{"store":{"book":[{"title":"A","price":8},{"title":"B","price":12}]}}"""

	private fun flat(json: String, path: String) = JsonPathTester.evaluate(json, path).replace(Regex("\\s+"), "")

	@Test
	fun `root path wraps the document in an array`() {
		assertEquals("[\n  {\n    \"a\": 1\n  }\n]", JsonPathTester.evaluate("""{"a":1}""", "$"))
	}

	@Test
	fun `matches come back as a pretty-printed array`() {
		assertEquals("[\n  \"A\",\n  \"B\"\n]", JsonPathTester.evaluate(store, "$.store.book[*].title"))
	}

	@Test
	fun `numbers keep every digit and a miss is an empty array`() {
		assertEquals("[12345678901234567890]", flat("""{"n":12345678901234567890}""", "$.n"))
		assertEquals("[]", flat("""{"a":1}""", "$.missing"))
	}

	@Test
	fun errors() {
		assertTrue(
			assertFailsWith<IllegalArgumentException> {
				JsonPathTester.evaluate("""{"a":""", "$")
			}.message!!.startsWith("Invalid JSON at line 1, column")
		)
		assertEquals("path", assertFailsWith<FieldError> { JsonPathTester.evaluate("""{"a":1}""", "$.[") }.field)
	}

	private fun pathError(json: String, path: String) =
		assertFailsWith<FieldError> { JsonPathTester.evaluate(json, path) }

	@Test
	fun `regex filters and path functions are refused on the path, but =~ inside a quoted string is just text`() {
		val regex = pathError("""[{"name":"ann"}]""", "$[?(@.name =~ /a.*/)]")
		assertEquals(
			"path" to "Regex filters (=~) aren't supported; use the Regex Tester",
			regex.field to regex.message
		)
		val function = pathError("""{"store":{"book":[1,2]}}""", "$.store.book.length()")
		assertEquals("path" to "Path functions aren't supported", function.field to function.message)
		assertEquals("""[{"x":"=~"}]""", flat("""[{"x": "=~"}]""", "$[?(@.x == '=~')]"))
		assertEquals("""[{"x":"it's=~"}]""", flat("""[{"x": "it's=~"}]""", "$[?(@.x == 'it\\'s=~')]"))
	}

	@Test
	fun `duplicate keys name the key and its position`() {
		val message = assertFailsWith<IllegalArgumentException> {
			JsonPathTester.evaluate("""{"a":1,"a":2}""", "$")
		}.message!!
		assertTrue(message.startsWith("Invalid JSON at line 1, column"), message)
		assertTrue(message.contains("\"a\""), message)
	}

	@Test
	fun `one deep scan per path, paths up to 10,000 characters, and a bounded number of matches`() {
		fun refused(json: String, path: String) = pathError(json, path).let { it.field to it.message }
		val nested = "{\"a\":".repeat(100) + "1" + "}".repeat(100)
		assertEquals("path" to "Only one deep scan (..) per path is supported", refused(nested, "$..*..*..*..*"))
		assertEquals(100, JsonPathTester.evaluate(nested, "$..*").lines().count { it.startsWith("  {") || it == "  1" })
		assertEquals("[]", flat("{}", "$['" + "a".repeat(9_995) + "']"))
		assertEquals("path" to "Path is too long (max 10,000 characters)", refused("{}", "$['" + "a".repeat(9_996) + "']"))
		val tooMany = "path" to "Too many matches to show; narrow the path"
		assertEquals(100_000, flat("[" + "1,".repeat(99_999) + "1]", "$[*]").count { it == '1' })
		assertEquals(tooMany, refused("[" + "1,".repeat(100_000) + "1]", "$[*]"))
		// Jayway keeps a path per match: 100 matches under 1,000-character keys spell out more than 5,000,000.
		val key = "k".repeat(1_000)
		assertEquals(tooMany, refused("{\"$key\":".repeat(100) + "1" + "}".repeat(100), "$..*"))
	}
}
