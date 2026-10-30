package eu.zeletrik.devcore.tools.casing

import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea

@Route("case-converter", layout = MainLayout::class)
@SpringComponent
@UIScope
class CaseConverterView : TextToolView<TextArea>(Tool.CASE_CONVERTER, textArea("Text")) {
	internal val outputs = Case.entries.associateWith { OutputField(it.label) }

	init {
		add(input)
		outputs.values.forEach(::add)
	}

	override fun render(text: String) = outputs.forEach { (case, field) -> field.value = CaseConverter.convert(text, case) }

	override fun clearOutputs() = outputs.values.forEach { it.clear() }
}
