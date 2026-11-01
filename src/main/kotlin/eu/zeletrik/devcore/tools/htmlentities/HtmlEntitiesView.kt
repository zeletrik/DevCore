package eu.zeletrik.devcore.tools.htmlentities

import com.vaadin.flow.component.checkbox.Checkbox
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
import eu.zeletrik.devcore.shell.modeGroup
import eu.zeletrik.devcore.shell.textArea

/** Escape to entities or decode them; non-ASCII can be escaped too. */
@Route("html-entities", layout = MainLayout::class)
@SpringComponent
@UIScope
class HtmlEntitiesView : TextToolView<TextArea>(Tool.HTML_ENTITIES, textArea("Input")) {
	internal val mode = modeGroup().apply { addValueChangeListener { showOptions(); recompute() } }
	internal val nonAscii = Checkbox("Encode non-ASCII").apply { addValueChangeListener { recompute() } }
	internal val output = OutputField("Output")

	init {
		add(HorizontalLayout(mode, nonAscii), input, output)
	}

	override fun render(text: String) {
		output.value = if (mode.value == Mode.DECODE) HtmlEntities.decode(text) else HtmlEntities.encode(text, nonAscii.value)
	}

	override fun clearOutputs() = output.clear()

	override fun applyMode(mode: String?) {
		this.mode.value = Mode.fromParam(mode)
	}

	private fun showOptions() {
		nonAscii.isVisible = mode.value == Mode.ENCODE
	}
}
