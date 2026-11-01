package eu.zeletrik.devcore.tools.nato

import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea

/** Spells text with the NATO alphabet, one word per character. */
@Route("nato-alphabet", layout = MainLayout::class)
@SpringComponent
@UIScope
class NatoView : TextToolView<TextArea>(Tool.NATO, textArea("Text")) {
	internal val output = OutputField("Spelling")

	init {
		add(input, output)
	}

	override fun render(text: String) {
		output.value = NatoAlphabet.spell(text)
	}

	override fun clearOutputs() = output.clear()
}
