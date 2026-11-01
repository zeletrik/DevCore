package eu.zeletrik.devcore.tools.stringescape

import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.Mode
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.modeGroup
import eu.zeletrik.devcore.shell.textArea

/** Escapes or unescapes a string literal's content under the chosen language's rules. */
@Route("string-escape", layout = MainLayout::class)
@SpringComponent
@UIScope
class StringEscapeView : TextToolView<TextArea>(Tool.STRING_ESCAPE, textArea("Input")) {
	internal val mode = modeGroup("Escape", "Unescape").apply { addValueChangeListener { recompute() } }
	internal val flavor = choice("Flavor", Flavor.entries, Flavor.JSON, Flavor::label) { recompute() }
	internal val output = OutputField("Output")

	init {
		add(HorizontalLayout(mode, flavor), input, output)
	}

	override fun render(text: String) {
		output.value = when (mode.value) {
			Mode.DECODE -> StringEscapes.unescape(text, flavor.value)
			else -> StringEscapes.escape(text, flavor.value)
		}
	}

	override fun clearOutputs() = output.clear()

	override fun applyMode(mode: String?) {
		this.mode.value = Mode.fromParam(mode)
	}
}
