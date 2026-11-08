package eu.zeletrik.devcore.tools.jsonpath

import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.DeepWork
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea
import eu.zeletrik.devcore.shell.textLine

/** Runs a JSONPath over the JSON; matches always come back as an array. */
@Route("jsonpath-tester", layout = MainLayout::class)
@SpringComponent
@UIScope
class JsonPathView : TextToolView<TextArea>(Tool.JSONPATH, textArea("JSON")) {
	internal val path = field(textLine("Path").apply { value = "$" }, "path")
	internal val output = OutputField("Matches")

	init {
		add(path, input, output)
	}

	override fun isEmpty() = input.value.isBlank() || path.value.isEmpty()

	override fun render(text: String) {
		val query = path.value
		output.value = DeepWork.run { JsonPathTester.evaluate(text, query) }
	}

	override fun clearOutputs() = output.clear()
}
