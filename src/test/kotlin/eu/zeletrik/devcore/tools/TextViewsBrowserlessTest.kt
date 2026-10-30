package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.casing.Case
import eu.zeletrik.devcore.tools.casing.CaseConverterView
import eu.zeletrik.devcore.tools.diff.TextDiffView
import eu.zeletrik.devcore.tools.lines.LineToolsView
import eu.zeletrik.devcore.tools.lines.Order
import eu.zeletrik.devcore.tools.textstats.TextStatisticsView
import eu.zeletrik.devcore.tools.regex.RegexTester
import eu.zeletrik.devcore.tools.regex.RegexView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
class TextViewsBrowserlessTest : SpringBrowserlessTest() {
	@Test
	fun `regex - hint, empty pattern, replace toggle, and a bad replacement keeps the matches`() {
		val view = navigate("regex-tester?input=a1b2", RegexView::class.java)
		assertEquals("Java regular expression syntax", view.pattern.helperText)
		assertEquals("", view.matches.value)
		assertFalse(view.input.isInvalid || view.pattern.isInvalid)
		assertFalse(view.replacement.isVisible || view.replaced.isVisible)
		test(view.pattern).setValue("(\\d)")
		assertTrue(view.matches.value.startsWith("2 matches"))
		test(view.replace).click()
		assertTrue(view.replacement.isVisible && view.replaced.isVisible)
		assertEquals("ab", view.replaced.value)
		test(view.replacement).setValue("$2")
		assertTrue(view.replacement.isInvalid)
		assertEquals("", view.replaced.value)
		assertTrue(view.matches.value.startsWith("2 matches"))
	}

	@Test
	fun `regex - an invalid pattern or a runaway one clears the outputs with an error on the pattern`() {
		val view = navigate("regex-tester?input=" + "a,".repeat(30), RegexView::class.java)
		test(view.pattern).setValue("a(b")
		assertEquals("Unclosed group near index 3", view.pattern.errorMessage)
		test(view.pattern).setValue("(.*,){15}x")
		assertTrue(view.pattern.isInvalid)
		assertEquals(RegexTester.STOPPED, view.pattern.errorMessage)
		assertEquals("", view.matches.value)
	}

	@Test
	fun `text diff - both empty shows nothing, invalid context keeps the previous diff, no input deep link`() {
		val view = navigate("text-diff?input=ignored", TextDiffView::class.java)
		assertEquals("", view.input.value)
		assertEquals("", view.status.text)
		test(view.input).setValue("a\nb")
		test(view.changed).setValue("a\nc")
		val before = view.output.value
		assertTrue(before.startsWith("--- Original"))
		test(view.context).setValue(21)
		assertTrue(view.context.isInvalid)
		assertEquals(before, view.output.value)
		test(view.context).setValue(null)
		assertEquals(before, view.output.value)
	}

	@Test
	fun `case converter, line tools and statistics from deep links`() {
		val cases = navigate("case-converter?input=fooBar", CaseConverterView::class.java)
		assertEquals(10, cases.outputs.size)
		assertEquals("foo_bar", cases.outputs.getValue(Case.SNAKE).value)
		val lines = navigate("line-tools?input=b%0Aa", LineToolsView::class.java)
		test(lines.order).selectItem(Order.ASCENDING.label)
		assertEquals("a\nb", lines.output.value)
		val stats = navigate("text-statistics?input=hi", TextStatisticsView::class.java)
		assertTrue(stats.output.value.startsWith("Characters: 2"))
	}
}
