package eu.zeletrik.devcore.tools.datasize

import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.enumSelect
import eu.zeletrik.devcore.shell.textLine

/** A value in one unit shown in every unit. */
@Route("data-size-converter", layout = MainLayout::class)
@SpringComponent
@UIScope
class DataSizeView : TextToolView<TextField>(Tool.DATA_SIZE, textLine("Value")) {
	internal val unit = enumSelect("Unit", DataUnit.entries, DataUnit.MB, DataUnit::label) { recompute() }
	internal val output = OutputField("In every unit")

	init {
		add(
			HorizontalLayout(input, unit).apply {
				defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
				setWidthFull()
			},
			output,
		)
	}

	override fun render(text: String) {
		output.value = DataSizes.convert(text, unit.value)
	}

	override fun clearOutputs() = output.clear()
}
