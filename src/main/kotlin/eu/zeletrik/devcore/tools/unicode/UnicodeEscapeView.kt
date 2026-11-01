package eu.zeletrik.devcore.tools.unicode

import com.vaadin.flow.component.checkbox.Checkbox
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
import eu.zeletrik.devcore.shell.optionsRow
import eu.zeletrik.devcore.shell.textArea

/** Escapes text as UTF-16, braced or U+ code points, and decodes any of them. */
@Route("unicode-escape", layout = MainLayout::class)
@SpringComponent
@UIScope
class UnicodeEscapeView : TextToolView<TextArea>(Tool.UNICODE_ESCAPE, textArea("Input")) {
	internal val mode = modeGroup("Escape", "Unescape").apply { addValueChangeListener { showOptions(); recompute() } }
	internal val format =
		choice("Format", EscapeFormat.entries, EscapeFormat.UTF16, EscapeFormat::label) { showOptions(); recompute() }
	internal val escapeAscii = Checkbox("Escape ASCII too").apply { addValueChangeListener { recompute() } }
	internal val output = OutputField("Output")

	init {
		add(
			optionsRow(mode, format, escapeAscii),
			input,
			output,
		)
	}

	override fun render(text: String) {
		output.value = when (mode.value) {
			Mode.DECODE -> UnicodeEscapes.decode(text)
			else -> UnicodeEscapes.encode(text, format.value, escapeAscii.value)
		}
	}

	override fun clearOutputs() = output.clear()

	override fun applyMode(mode: String?) {
		this.mode.value = Mode.fromParam(mode)
	}

	private fun showOptions() {
		val encoding = mode.value == Mode.ENCODE
		format.isVisible = encoding
		escapeAscii.isVisible = encoding && format.value != EscapeFormat.CODE_POINTS
	}
}
