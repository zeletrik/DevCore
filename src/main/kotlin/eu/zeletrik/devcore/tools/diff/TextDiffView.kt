package eu.zeletrik.devcore.tools.diff

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.boundedInt
import eu.zeletrik.devcore.shell.numberField
import eu.zeletrik.devcore.shell.optionsRow
import eu.zeletrik.devcore.shell.textArea

/** A unified diff of two texts, with optional case and whitespace folding. */
@Route("text-diff", layout = MainLayout::class)
@SpringComponent
@UIScope
class TextDiffView : TextToolView<TextArea>(Tool.TEXT_DIFF, textArea("Original")) {
	internal val changed = field(textArea("Changed"), "changed")
	internal val context = numberField("Context lines", 0..20, 3).apply { addValueChangeListener { recompute() } }
	internal val ignoreCase = Checkbox("Ignore case").apply { addValueChangeListener { recompute() } }
	internal val ignoreWhitespace = Checkbox("Ignore whitespace").apply { addValueChangeListener { recompute() } }
	internal val status = Span()
	internal val output = OutputField("Unified diff")

	init {
		add(
			optionsRow(context, ignoreCase, ignoreWhitespace),
			input,
			changed,
			status,
			output,
		)
	}

	override fun acceptsInputParam() = false

	override fun isEmpty() = input.value.isEmpty() && changed.value.isEmpty()

	override fun render(text: String) {
		val lines = boundedInt(context) ?: return keepOutputUnlessInputsChanged()
		val diff = TextDiff.diff(text, changed.value, lines, ignoreCase.value, ignoreWhitespace.value)
		status.text = diff.status
		output.value = diff.unified
	}

	override fun clearOutputs() {
		status.text = ""
		output.clear()
	}
}
