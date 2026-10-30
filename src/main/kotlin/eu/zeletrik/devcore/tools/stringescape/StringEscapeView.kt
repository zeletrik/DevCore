package eu.zeletrik.devcore.tools.stringescape

import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.Mode
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.modeGroup
import eu.zeletrik.devcore.shell.textArea

@Route("string-escape", layout = MainLayout::class)
@SpringComponent
@UIScope
class StringEscapeView : TextToolView<TextArea>(Tool.STRING_ESCAPE, textArea("Input")) {
	internal val mode = modeGroup("Escape", "Unescape").apply { addValueChangeListener { recompute() } }
	internal val flavour = RadioButtonGroup("Flavour", *Flavour.entries.toTypedArray()).apply {
		setItemLabelGenerator { it.label }
		value = Flavour.JSON
		addValueChangeListener { recompute() }
	}
	internal val output = OutputField("Output")

	init {
		add(HorizontalLayout(mode, flavour), input, output)
	}

	override fun render(text: String) {
		output.value = when (mode.value) {
			Mode.DECODE -> StringEscapes.unescape(text, flavour.value)
			else -> StringEscapes.escape(text, flavour.value)
		}
	}

	override fun clearOutputs() = output.clear()

	override fun applyMode(mode: String?) {
		this.mode.value = Mode.fromParam(mode)
	}
}
