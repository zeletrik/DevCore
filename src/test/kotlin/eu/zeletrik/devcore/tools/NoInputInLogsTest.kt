package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasValue
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.textfield.PasswordField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import eu.zeletrik.devcore.shell.MAX_INPUT_CHARS
import eu.zeletrik.devcore.shell.Tool
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import kotlin.reflect.full.memberProperties
import kotlin.test.assertFalse

/** Every editable text field of every tool, secrets included, in every mode: the value must never reach the log. */
@SpringBootTest
@ExtendWith(OutputCaptureExtension::class)
class NoInputInLogsTest : SpringBrowserlessTest() {
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

	@Test
	fun `no tool writes its input to the log`(output: CapturedOutput) {
		val inputs = listOf(marker, "{\"$marker\": [", "$marker.$marker.$marker", marker.padEnd(MAX_INPUT_CHARS + 1, 'x'))
		Tool.entries.forEach { tool ->
			UI.getCurrent().navigate(tool.view)
			val view = currentView as Component
			val mode = view::class.memberProperties.firstOrNull { it.name == "mode" }?.getter?.call(view) as? RadioButtonGroup<*>
			val fill = { view.textFields().forEach { field -> inputs.forEach { field.value = it } } }
			if (mode == null) fill() else mode.forEachValue(fill)
		}
		assertFalse(output.all.contains(marker))
	}
}
