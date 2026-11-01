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
}
