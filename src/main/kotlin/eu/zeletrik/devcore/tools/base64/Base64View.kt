package eu.zeletrik.devcore.tools.base64

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

@Route("base64", layout = MainLayout::class)
@SpringComponent
@UIScope
class Base64View : TextToolView<TextArea>(Tool.BASE64, textArea("Input")) {
	internal val mode = modeGroup().apply { addValueChangeListener { recompute() } }
	internal val urlSafe = RadioButtonGroup("Variant", false, true).apply {
		setItemLabelGenerator { if (it) "URL-safe" else "Standard" }
		value = false
		addValueChangeListener { recompute() }
	}
	internal val output = OutputField("Output")

	init {
		add(HorizontalLayout(mode, urlSafe), input, output)
	}

	override fun render(text: String) {
		output.value = when (mode.value) {
			Mode.DECODE -> Base64Codec.decode(text, urlSafe.value)
			else -> Base64Codec.encode(text, urlSafe.value)
		}
	}

	override fun clearOutputs() = output.clear()

	override fun applyMode(mode: String?) {
		this.mode.value = Mode.fromParam(mode)
	}
}
