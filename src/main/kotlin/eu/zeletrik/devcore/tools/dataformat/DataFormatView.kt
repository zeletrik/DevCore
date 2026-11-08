package eu.zeletrik.devcore.tools.dataformat

import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.DeepWork
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.enumSelect
import eu.zeletrik.devcore.shell.textArea

/** JSON, YAML, TOML, properties and CSV in any direction. */
@Route("data-format-converter", layout = MainLayout::class)
@SpringComponent
@UIScope
class DataFormatView : TextToolView<TextArea>(Tool.DATA_FORMAT, textArea("Input")) {
	internal val from = enumSelect("From", Format.entries, Format.JSON, Format::label) { recompute() }
	internal val to = enumSelect("To", Format.entries, Format.YAML, Format::label) { recompute() }
	internal val output = OutputField("Output")

	init {
		add(HorizontalLayout(from, to), input, output)
	}


	override fun render(text: String) {
		val source = from.value
		val target = to.value
		output.value = DeepWork.run { DataFormats.convert(text, source, target) }
	}

	override fun clearOutputs() = output.clear()
}
