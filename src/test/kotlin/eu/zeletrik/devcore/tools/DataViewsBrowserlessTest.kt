package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.dataformat.DataFormatView
import eu.zeletrik.devcore.tools.dataformat.Format
import eu.zeletrik.devcore.tools.jsonpath.JsonPathView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class DataViewsBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `data format converter defaults to JSON to YAML and reports errors on the input`() {
		val view = navigate("data-format-converter?input=%7B%22a%22%3A1%7D", DataFormatView::class.java)
		assertEquals(Format.JSON, view.from.value)
		assertEquals(Format.YAML, view.to.value)
		assertEquals("a: 1", view.output.value)
		test(view.to).selectItem("TOML")
		assertEquals("a = 1", view.output.value)
		test(view.input).setValue("[1]")
		assertTrue(view.input.isInvalid)
		assertEquals("TOML needs an object at the top level", view.input.errorMessage)
		assertEquals("", view.output.value)
	}

	@Test
	fun `jsonpath opens with the root path, puts path errors on Path and treats an empty path as nothing to do`() {
		val view = navigate("jsonpath-tester?input=%7B%22a%22%3A1%7D", JsonPathView::class.java)
		assertEquals("$", view.path.value)
		assertTrue(view.output.value.startsWith("["))
		test(view.path).setValue("$.[")
		assertTrue(view.path.isInvalid)
		assertEquals("", view.output.value)
		test(view.path).setValue("")
		assertEquals("", view.output.value)
		assertTrue(!view.path.isInvalid && !view.input.isInvalid)
	}
}
