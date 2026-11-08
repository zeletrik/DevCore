package eu.zeletrik.devcore.tools.urlparser

import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textLine

/** Splits a URL into its parts and decodes the query parameters. */
@Route("url-parser", layout = MainLayout::class)
@SpringComponent
@UIScope
class UrlParserView : TextToolView<TextField>(Tool.URL_PARSER, textLine("URL")) {
	internal val output = OutputField("Details")

	init {
		add(input, output)
	}

	override fun render(text: String) {
		output.value = UrlParser.describe(text)
	}

	override fun clearOutputs() = output.clear()
}
