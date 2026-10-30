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
	fun `wildcards, filters, recursion, slices and unions`() {
		assertEquals("[\n  \"A\",\n  \"B\"\n]", JsonPathTester.evaluate(store, "$.store.book[*].title"))
		assertEquals("[\"B\"]", flat(store, "$..book[?(@.price > 10)].title"))
		assertEquals("[\"A\"]", flat(store, "$.store.book[0:1].title"))
		assertEquals("[8,12]", flat(store, "$.store.book[0,1].price"))
		assertEquals("[8,12]", flat(store, "$..price"))
	}

	@Test
	fun `numbers keep every digit and a miss is an empty array`() {
		assertEquals("[12345678901234567890]", flat("""{"n":12345678901234567890}""", "$.n"))
		assertEquals("[]", flat("""{"a":1}""", "$.missing"))
	}

	@Test
	fun errors() {
		assertTrue(assertFailsWith<IllegalArgumentException> { JsonPathTester.evaluate("""{"a":""", "$") }.message!!.startsWith("Invalid JSON at line 1, column"))
		assertEquals("path", assertFailsWith<FieldError> { JsonPathTester.evaluate("""{"a":1}""", "$.[") }.field)
	}
}
