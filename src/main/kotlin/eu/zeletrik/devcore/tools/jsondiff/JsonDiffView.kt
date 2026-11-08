package eu.zeletrik.devcore.tools.jsondiff

import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.DeepWork
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.textArea

/** Lists what changed between two JSON documents, one path per line, whatever their key order and formatting. */
@Route("json-diff", layout = MainLayout::class)
@SpringComponent
@UIScope
class JsonDiffView : TextToolView<TextArea>(Tool.JSON_DIFF, textArea("Original")) {
	internal val numbers = choice("Numbers", Numbers.entries, Numbers.BY_VALUE, Numbers::label) { recompute() }
	internal val changed = field(textArea("Changed"), "changed")
	internal val status = Span()
	internal val output = OutputField("Differences").apply {
		helperText = "Paths of removed and changed values point into Original, paths of added values into Changed."
	}

	init {
		add(numbers, input, changed, status, output)
	}

	override fun acceptsInputParam() = false

	override fun isEmpty() = input.value.isBlank() && changed.value.isBlank()

	override fun render(text: String) {
		val other = changed.value
		val match = numbers.value
		val differences = DeepWork.run { JsonDiff.diff(text, other, match) } ?: return clearOutputs()
		status.text = differences.status
		output.value = differences.text
	}

	override fun clearOutputs() {
		status.text = ""
		output.clear()
	}
}
