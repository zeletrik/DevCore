package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasValue
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.textfield.PasswordField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import eu.zeletrik.devcore.tools.FieldError
import eu.zeletrik.devcore.tools.base64.Base64View
import eu.zeletrik.devcore.tools.diff.TextDiffView
import eu.zeletrik.devcore.tools.jsonpath.JsonPathView
import eu.zeletrik.devcore.tools.regex.RegexView
import eu.zeletrik.devcore.tools.sql.SqlDialect
import eu.zeletrik.devcore.tools.sql.SqlFormatterView
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.io.IOException
import kotlin.reflect.full.memberProperties
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
		val n = boundedInt(count) ?: return
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

/** A text tool whose render fails with [failure] for any input except `ok`. */
private class FailingTool(private val failure: () -> Throwable) :
	TextToolView<TextArea>(Tool.BASE64, textArea("Input")) {
	val output = OutputField("Output")

	override fun render(text: String) {
		if (text != "ok") throw failure()
		output.value = "fine"
	}

	override fun clearOutputs() = output.clear()
}

/** A generator that succeeds on attach and fails, with a checked exception, once [fail] is set. */
private class FailingGenerator : GeneratorView(Tool.LOREM) {
	var fail = false
	val output = OutputField("Output")

	init {
		add(generateButton, generateError, output)
	}

	override fun produce() {
		if (fail) throw IOException("generator-secret")
		output.value = "first"
	}
}

/**
 * The contracts every tool inherits from the shell: live recompute, caps, error routing, the safety net, deep
 * links, stale output, deep work and never logging input.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension::class)
class ToolViewContractTest : SpringBrowserlessTest() {
	private val tooLarge = "Input is too large (max 1,000,000 characters)"
	private val bs = "\\"

	private fun fixture() = navigate(InputHandlingFixtureView::class.java).apply { test(input).setValue("ab") }

	private fun failureLines(output: CapturedOutput) = output.all.lines().filter { " failed: " in it }

	@Test
	fun `recomputes live on input and immediately on option change`() {
		val view = navigate(Base64View::class.java)
		test(view.input).setValue("???")
		assertEquals("Pz8/", view.output.value)
		test(view.urlSafe).selectItem("URL-safe")
		assertEquals("Pz8_", view.output.value)
	}

	@Test
	fun `empty input gives empty output and no error`() {
		val view = navigate(Base64View::class.java)
		test(view.input).setValue("hi")
		test(view.input).setValue("")
		assertEquals("", view.output.value)
		assertFalse(view.input.isInvalid)
	}

	@Test
	fun `invalid input shows an error and clears the output, valid input recovers`() {
		val view = navigate(Base64View::class.java)
		test(view.mode).selectItem("Decode")
		test(view.input).setValue("aGk=")
		test(view.input).setValue("aGk*")
		assertTrue(view.input.isInvalid)
		assertEquals("Invalid Base64", view.input.errorMessage)
		assertEquals("", view.output.value)
		test(view.input).setValue("aGk=")
		assertFalse(view.input.isInvalid)
		assertEquals("hi", view.output.value)
	}

	@Test
	fun `a deep link fills input and mode, replacing kept state`() {
		val view = navigate(Base64View::class.java)
		test(view.input).setValue("kept")
		navigate(HomeView::class.java)
		navigate("base64?mode=decode&input=aGk", Base64View::class.java)
		assertEquals(Mode.DECODE, view.mode.value)
		assertEquals("aGk", view.input.value)
		assertEquals("hi", view.output.value)
	}

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

	@Test
	fun `an unexpected exception marks the input and clears the outputs`(output: CapturedOutput) {
		val view = FailingTool { IllegalStateException("exception-secret") }
		view.input.value = "ok"
		assertEquals("fine", view.output.value)
		view.input.value = "input-secret"
		assertTrue(view.input.isInvalid)
		assertEquals("Couldn't process this input", view.input.errorMessage)
		assertEquals("", view.output.value)
		val lines = failureLines(output)
		assertEquals(1, lines.size)
		assertTrue(lines.single().contains("WARN"))
		assertTrue(lines.single().contains("${Tool.BASE64.id} failed: java.lang.IllegalStateException"))
		assertFalse(output.all.contains("exception-secret"))
		assertFalse(output.all.contains("input-secret"))
	}

	@Test
	fun `a checked exception, which Kotlin lets pass undeclared, is caught the same way`(output: CapturedOutput) {
		val view = FailingTool { IOException("checked-secret") }
		view.input.value = "input-secret"
		assertEquals("Couldn't process this input", view.input.errorMessage)
		assertTrue(failureLines(output).single().contains("${Tool.BASE64.id} failed: java.io.IOException"))
		assertFalse(output.all.contains("checked-secret"))
	}

	@Test
	fun `a stack overflow says the input is too deeply nested`(output: CapturedOutput) {
		val view = FailingTool { StackOverflowError() }
		view.input.value = "[[["
		assertEquals("Couldn't process this input (too deeply nested)", view.input.errorMessage)
		assertEquals("", view.output.value)
		assertTrue(failureLines(output).single().contains("java.lang.StackOverflowError"))
	}

	@Test
	fun `a failing generator says so and keeps the previous output`(output: CapturedOutput) {
		val view = FailingGenerator()
		UI.getCurrent().add(view)
		assertEquals("first", view.output.value)
		assertFalse(view.generateError.isVisible)
		view.fail = true
		test(view.generateButton).click()
		assertTrue(view.generateError.isVisible)
		assertEquals("Couldn't generate output", view.generateError.text)
		assertEquals("first", view.output.value)
		assertTrue(failureLines(output).single().contains("${Tool.LOREM.id} failed: java.io.IOException"))
		assertFalse(output.all.contains("generator-secret"))
	}

	@Test
	fun `an invalid option keeps the output only until a main input changes`() {
		val view = navigate(TextDiffView::class.java)
		view.input.value = "a\nb"
		view.changed.value = "a\nc"
		val diff = view.output.value
		assertTrue(diff.contains("+c"))
		view.context.value = 21
		assertEquals(diff, view.output.value)
		view.changed.value = "a\nd"
		assertEquals("", view.output.value)
		assertEquals("", view.status.text)
	}

	@Test
	fun `an output over the cap is reported on the input`() {
		val view = navigate(RegexView::class.java)
		test(view.pattern).setValue("a")
		view.replace.value = true
		test(view.replacement).setValue("x".repeat(5_001))
		view.input.value = "a".repeat(1_000)
		assertTrue(view.input.isInvalid)
		assertEquals("Output is too large (max 5,000,000 characters)", view.input.errorMessage)
		assertEquals("", view.replaced.value)
		assertEquals("", view.matches.value)
	}

	@Test
	fun `deep inputs compute on the large stack`() {
		val regex = navigate(RegexView::class.java)
		test(regex.pattern).setValue("(.|${bs}n)*")
		regex.input.value = "ab${'\n'}".repeat(3_334).take(10_000)
		assertFalse(regex.input.isInvalid)
		assertEquals("2 matches", regex.matches.value.lines().first())

		val sql = navigate(SqlFormatterView::class.java)
		sql.dialect.value = SqlDialect.MYSQL
		val literal = "'" + "$bs'".repeat(2_000) + "'"
		sql.input.value = "select $literal"
		assertFalse(sql.input.isInvalid)
		assertTrue(sql.output.value.contains(literal))

		val jsonPath = navigate(JsonPathView::class.java)
		jsonPath.input.value = "[1]"
		test(jsonPath.path).setValue("$[?(" + "(".repeat(4_990) + "@ == 1" + ")".repeat(4_990) + ")]")
		assertFalse(jsonPath.path.isInvalid)
		assertEquals("[${'\n'}  1${'\n'}]", jsonPath.output.value)
	}

	private val marker = "MARKER-7f3c9a1e"

	private fun Component.textFields(): List<HasValue<*, String>> = children.toList().flatMap { child ->
		val field: HasValue<*, String>? = when (child) {
			is TextField -> child
			is TextArea -> child
			is PasswordField -> child
			else -> null
		}
		listOfNotNull(field?.takeUnless { it.isReadOnly }) + child.textFields()
	}

	private fun <T> RadioButtonGroup<T>.forEachValue(action: () -> Unit) =
		listDataView.items.toList().forEach { value = it; action() }

	/** Every editable text field of every tool, secrets included, in every mode: the value must never reach the log. */
	@Test
	fun `no tool writes its input to the log`(output: CapturedOutput) {
		val inputs =
			listOf(marker, "{\"$marker\": [", "$marker.$marker.$marker", marker.padEnd(MAX_INPUT_CHARS + 1, 'x'))
		Tool.entries.forEach { tool ->
			UI.getCurrent().navigate(tool.view)
			val view = currentView as Component
			val mode =
				view::class.memberProperties.firstOrNull { it.name == "mode" }
					?.getter?.call(view) as? RadioButtonGroup<*>
			val fill = { view.textFields().forEach { field -> inputs.forEach { field.value = it } } }
			if (mode == null) fill() else mode.forEachValue(fill)
		}
		assertFalse(output.all.contains(marker))
	}
}
