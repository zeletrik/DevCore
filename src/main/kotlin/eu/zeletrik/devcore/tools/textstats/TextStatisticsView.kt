package eu.zeletrik.devcore.tools.textstats

import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea

@Route("text-statistics", layout = MainLayout::class)
@SpringComponent
@UIScope
class TextStatisticsView : TextToolView<TextArea>(Tool.TEXT_STATISTICS, textArea("Text")) {
	internal val output = OutputField("Statistics")

	init {
		add(input, output)
	}

	override fun render(text: String) {
		output.value = TextStatistics.describe(text)
	}

	override fun clearOutputs() = output.clear()
}
