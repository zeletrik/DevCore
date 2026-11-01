package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.json.Indent
import eu.zeletrik.devcore.tools.json.JsonFormatterView
import eu.zeletrik.devcore.tools.jsonpath.JsonPathView
import eu.zeletrik.devcore.tools.regex.RegexView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class OutputCapBrowserlessTest : SpringBrowserlessTest() {
	private val tooLarge = "Output is too large (max 5,000,000 characters)"

	private fun regex(replacementLength: Int): RegexView {
		val view = navigate(RegexView::class.java)
		test(view.pattern).setValue("a")
		view.replace.value = true
		test(view.replacement).setValue("x".repeat(replacementLength))
		view.input.value = "a".repeat(1_000)
		return view
	}

	@Test
	fun `replacement just under, at and over the cap`() {
		assertEquals(4_999_000, regex(4_999).replaced.value.length)
		assertEquals(5_000_000, regex(5_000).replaced.value.length)
		val over = regex(5_001)
		assertTrue(over.input.isInvalid)
		assertEquals(tooLarge, over.input.errorMessage)
		assertEquals("", over.replaced.value)
		assertEquals("", over.matches.value)
	}

	@Test
	fun `deep JSON with 4-space indentation`() {
		val view = navigate(JsonFormatterView::class.java)
		view.indent.value = Indent.FOUR
		val text = "[".repeat(499) + "0,".repeat(499_500) + "0" + "]".repeat(499)
		assertEquals(999_999, text.length)
		view.input.value = text
		assertEquals(tooLarge, view.input.errorMessage)
		assertEquals("", view.output.value)
	}

	@Test
	fun `JSONPath recursive descent over a deep document`() {
		val view = navigate(JsonPathView::class.java)
		test(view.path).setValue("$..*")
		view.input.value = "[".repeat(400) + List(4_000) { "0" }.joinToString(",") + "]".repeat(400)
		assertEquals(tooLarge, view.input.errorMessage)
		assertEquals("", view.output.value)
	}
}
