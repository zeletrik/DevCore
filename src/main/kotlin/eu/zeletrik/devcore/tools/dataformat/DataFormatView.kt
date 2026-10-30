package eu.zeletrik.devcore.tools.dataformat

import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea

@Route("data-format-converter", layout = MainLayout::class)
@SpringComponent
@UIScope
class DataFormatView : TextToolView<TextArea>(Tool.DATA_FORMAT, textArea("Input")) {
	internal val from = formatSelect("From", Format.JSON)
	internal val to = formatSelect("To", Format.YAML)
	internal val output = OutputField("Output")

	init {
		add(HorizontalLayout(from, to), input, output)
	}

	private fun formatSelect(label: String, initial: Format) = Select<Format>().apply {
		this.label = label
		setItems(*Format.entries.toTypedArray())
		setItemLabelGenerator { it.label }
		value = initial
		addValueChangeListener { recompute() }
	}

	override fun render(text: String) {
		output.value = DataFormats.convert(text, from.value, to.value)
	}

	override fun clearOutputs() = output.clear()
}
