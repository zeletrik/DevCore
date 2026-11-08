package eu.zeletrik.devcore.tools.markdown

import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea

/** CommonMark with tables, strikethrough and autolinks, as HTML source. */
@Route("markdown-to-html", layout = MainLayout::class)
@SpringComponent
@UIScope
class MarkdownView : TextToolView<TextArea>(Tool.MARKDOWN, textArea("Markdown")) {
	internal val output = OutputField("HTML source")

	init {
		add(input, output)
	}

	override fun render(text: String) {
		output.value = MarkdownToHtml.convert(text)
	}

	override fun clearOutputs() = output.clear()
}
