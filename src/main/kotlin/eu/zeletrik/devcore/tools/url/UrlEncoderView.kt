package eu.zeletrik.devcore.tools.url

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

@Route("url-encoder", layout = MainLayout::class)
@SpringComponent
@UIScope
class UrlEncoderView : TextToolView<TextArea>(Tool.URL, textArea("Input")) {
	internal val mode = modeGroup().apply { addValueChangeListener { recompute() } }
	internal val output = OutputField("Output")

	init {
		add(mode, input, output)
	}

	override fun render(text: String) {
		output.value = if (mode.value == Mode.DECODE) UrlCodec.decode(text) else UrlCodec.encode(text)
	}

	override fun clearOutputs() = output.clear()

	override fun applyMode(mode: String?) {
		this.mode.value = Mode.fromParam(mode)
	}
}
