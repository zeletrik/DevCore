package eu.zeletrik.devcore.tools.xml

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
import eu.zeletrik.devcore.tools.json.Indent

/** Pretty-prints or minifies XML. */
@Route("xml-formatter", layout = MainLayout::class)
@SpringComponent
@UIScope
class XmlFormatterView : TextToolView<TextArea>(Tool.XML, textArea("XML")) {
	internal val indent = choice("Indentation", Indent.entries, Indent.TWO, Indent::label) { recompute() }
	internal val output = OutputField("Formatted")

	init {
		add(indent, input, output)
	}

	override fun render(text: String) {
		output.value = XmlFormatter.format(text, indent.value)
	}

	override fun clearOutputs() = output.clear()
}
