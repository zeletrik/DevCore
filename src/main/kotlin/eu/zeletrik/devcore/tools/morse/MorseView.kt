package eu.zeletrik.devcore.tools.morse

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

/** Text to Morse and back. */
@Route("morse-code", layout = MainLayout::class)
@SpringComponent
@UIScope
class MorseView : TextToolView<TextArea>(Tool.MORSE, textArea("Input")) {
	internal val mode = modeGroup().apply { addValueChangeListener { recompute() } }
	internal val output = OutputField("Output")

	init {
		add(mode, input, output)
	}

	override fun render(text: String) {
		output.value = if (mode.value == Mode.DECODE) MorseCode.decode(text) else MorseCode.encode(text)
	}

	override fun clearOutputs() = output.clear()

	override fun applyMode(mode: String?) {
		this.mode.value = Mode.fromParam(mode)
	}
}
