package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.basicauth.BasicAuth
import eu.zeletrik.devcore.tools.jsonpath.JsonPathTester
import eu.zeletrik.devcore.tools.jsonpath.JsonPathView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class JsonPathRestrictionsTest : SpringBrowserlessTest() {
	private fun pathError(json: String, path: String) = assertFailsWith<FieldError> { JsonPathTester.evaluate(json, path) }

	@Test
	fun `regex filters are refused on the path`() {
		val error = pathError("""[{"name":"ann"}]""", "$[?(@.name =~ /a.*/)]")
		assertEquals("path", error.field)
		assertEquals("Regex filters (=~) aren't supported; use the Regex Tester", error.message)
	}

	@Test
	fun `=~ inside a quoted string is just text`() {
		assertEquals("[\n  {\n    \"x\": \"=~\"\n  }\n]", JsonPathTester.evaluate("""[{"x": "=~"}]""", "$[?(@.x == '=~')]"))
	}

	@Test
	fun `path functions are refused on the path`() {
		val error = pathError("""{"store":{"book":[1,2]}}""", "$.store.book.length()")
		assertEquals("path", error.field)
		assertEquals("Path functions aren't supported", error.message)
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
	fun `whitespace-only JSON is empty output and no error`() {
		val view = navigate(JsonPathView::class.java)
		view.input.value = "  \n\t "
		assertFalse(view.input.isInvalid)
		assertEquals("", view.output.value)
	}

	@Test
	fun `basic auth decode strips a Proxy-Authorization prefix`() {
		assertEquals("Aladdin" to "open sesame", BasicAuth.decode("Proxy-Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="))
	}
}
