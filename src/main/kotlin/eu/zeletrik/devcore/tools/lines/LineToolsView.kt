package eu.zeletrik.devcore.tools.lines

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.enumSelect
import eu.zeletrik.devcore.shell.optionsRow
import eu.zeletrik.devcore.shell.textArea

/** Trim, dedupe and reorder lines. */
@Route("line-tools", layout = MainLayout::class)
@SpringComponent
@UIScope
class LineToolsView : TextToolView<TextArea>(Tool.LINE_TOOLS, textArea("Lines")) {
	internal val order = enumSelect("Order", Order.entries, Order.AS_IS, Order::label) { recompute() }
	internal val trim = option("Trim lines")
	internal val removeEmpty = option("Remove empty lines")
	internal val removeDuplicates = option("Remove duplicates")
	internal val ignoreCase = option("Ignore case")
	internal val output = OutputField("Output")

	init {
		add(
			optionsRow(order, trim, removeEmpty, removeDuplicates, ignoreCase),
			input,
			output,
		)
	}

	private fun option(label: String) = Checkbox(label).apply { addValueChangeListener { recompute() } }

	override fun render(text: String) {
		output.value = LineTools.apply(
			text,
			LineOptions(trim.value, removeEmpty.value, removeDuplicates.value, ignoreCase.value, order.value),
		)
	}

	override fun clearOutputs() = output.clear()
}
