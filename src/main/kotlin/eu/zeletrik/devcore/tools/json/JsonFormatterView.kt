package eu.zeletrik.devcore.tools.json

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.textArea

/** Pretty-print or minify, optionally expanding JSON stored in strings. */
@Route("json-formatter", layout = MainLayout::class)
@SpringComponent
@UIScope
class JsonFormatterView : TextToolView<TextArea>(Tool.JSON, textArea("JSON")) {
	internal val indent = choice("Indentation", Indent.entries, Indent.TWO, Indent::label) { recompute() }
	internal val expand = Checkbox("Expand embedded JSON").apply {
		helperText = "Shows JSON stored in strings as JSON, such as an SNS message inside an SQS body. " +
			"The result is for reading: those strings are now objects."
		addValueChangeListener { recompute() }
	}
	internal val output = OutputField("Formatted")

	init {
		add(indent, expand, input, output)
	}

	override fun render(text: String) {
		output.value = JsonFormatter.format(text, indent.value, expand.value)
	}

	override fun clearOutputs() = output.clear()
}
