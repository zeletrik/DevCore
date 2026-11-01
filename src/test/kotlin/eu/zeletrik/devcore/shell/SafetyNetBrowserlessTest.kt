package eu.zeletrik.devcore.shell

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.textfield.TextArea
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@ExtendWith(OutputCaptureExtension::class)
class SafetyNetBrowserlessTest : SpringBrowserlessTest() {
	/** A text tool whose render fails with [failure] for any input except `ok`. */
	private class FailingTool(private val failure: () -> Throwable) : TextToolView<TextArea>(
		Tool.BASE64,
		textArea("Input"),
	) {
		val output = OutputField("Output")

		override fun render(text: String) {
			if (text != "ok") throw failure()
			output.value = "fine"
		}

		override fun clearOutputs() = output.clear()
	}

	/** A generator that succeeds on attach and fails once [fail] is set. */
	private class FailingGenerator : GeneratorView(Tool.LOREM) {
		var fail = false
		val output = OutputField("Output")

		init {
			add(generateButton, generateError, output)
		}

		override fun produce() {
			if (fail) throw IllegalStateException("generator-secret")
			output.value = "first"
		}
	}

	private fun failureLines(output: CapturedOutput) = output.all.lines().filter { " failed: " in it }

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
		assertTrue(failureLines(output).single().contains("${Tool.LOREM.id} failed: java.lang.IllegalStateException"))
		assertFalse(output.all.contains("generator-secret"))
	}
}
