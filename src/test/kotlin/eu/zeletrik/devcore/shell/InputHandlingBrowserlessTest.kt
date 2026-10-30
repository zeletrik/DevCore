package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import eu.zeletrik.devcore.tools.FieldError
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A test-only tool exercising the shared input handling: a required field, an optional one and a numeric option. */
@Route("test-input-handling")
class InputHandlingFixtureView : TextToolView<TextArea>(Tool.BASE64, textArea("Input")) {
	internal val key = field(textLine("Key"), "key")
	internal val extra = field(textLine("Extra"), "extra", optional = true)
	internal val count = numberField("Count", 1..10, 1).apply { addValueChangeListener { recompute() } }
	internal val main = OutputField("Main")
	internal val dependent = OutputField("Dependent")

	init {
		add(input, key, extra, count, main, dependent)
	}

	override fun render(text: String) {
		val n = boundedInt(count, 1..10) ?: return
		if (key.value == "bad") throw FieldError("key", "Bad key")
		main.value = text.repeat(n)
		dependent.value = optional(extra) {
			require(extra.value != "bad") { "Bad extra" }
			extra.value.uppercase()
		} ?: ""
	}

	override fun clearOutputs() {
		main.clear()
		dependent.clear()
	}
}

@SpringBootTest
class InputHandlingBrowserlessTest : SpringBrowserlessTest() {
	private val tooLarge = "Input is too large (max 1,000,000 characters)"

	private fun fixture() = navigate(InputHandlingFixtureView::class.java).apply { test(input).setValue("ab") }

	@Test
	fun `a required field at the cap is processed, above it the error lands on that field`() {
		val view = fixture()
		test(view.key).setValue("k".repeat(MAX_INPUT_CHARS))
		assertFalse(view.key.isInvalid)
		assertEquals("ab", view.main.value)
		test(view.key).setValue("k".repeat(MAX_INPUT_CHARS + 1))
		assertTrue(view.key.isInvalid)
		assertEquals(tooLarge, view.key.errorMessage)
		assertFalse(view.input.isInvalid)
		assertEquals("", view.main.value)
	}

	@Test
	fun `a FieldError marks its field and clears every output`() {
		val view = fixture()
		test(view.key).setValue("bad")
		assertTrue(view.key.isInvalid)
		assertEquals("Bad key", view.key.errorMessage)
		assertFalse(view.input.isInvalid)
		assertEquals("", view.main.value)
	}

	@Test
	fun `an optional field error clears only the outputs that depend on it`() {
		val view = fixture()
		test(view.extra).setValue("x")
		assertEquals("X", view.dependent.value)
		test(view.extra).setValue("bad")
		assertTrue(view.extra.isInvalid)
		assertEquals("Bad extra", view.extra.errorMessage)
		assertEquals("ab", view.main.value)
		assertEquals("", view.dependent.value)
		test(view.extra).setValue("y".repeat(MAX_INPUT_CHARS + 1))
		assertEquals(tooLarge, view.extra.errorMessage)
		assertEquals("ab", view.main.value)
		test(view.extra).setValue("ok")
		assertFalse(view.extra.isInvalid)
		assertEquals("OK", view.dependent.value)
	}

	@Test
	fun `an out-of-range or empty number marks the field and keeps the previous output`() {
		val view = fixture()
		test(view.count).setValue(2)
		assertEquals("abab", view.main.value)
		test(view.count).setValue(11)
		assertTrue(view.count.isInvalid)
		assertEquals("abab", view.main.value)
		test(view.count).setValue(null)
		assertTrue(view.count.isInvalid)
		assertEquals("abab", view.main.value)
		test(view.count).setValue(3)
		assertFalse(view.count.isInvalid)
		assertEquals("ababab", view.main.value)
	}
}
