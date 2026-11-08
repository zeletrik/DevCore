package eu.zeletrik.devcore.tools.lorem

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.GeneratorView
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.boundedInt
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.numberField
import eu.zeletrik.devcore.shell.optionsRow

/** Paragraphs, sentences or words of placeholder text. */
@Route("lorem-ipsum-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class LoremView : GeneratorView(Tool.LOREM) {
	internal val unit = choice("Unit", LoremUnit.entries, LoremUnit.PARAGRAPHS, LoremUnit::label) { generate() }
	internal val count = numberField("Count", 1..100, 3).apply { addValueChangeListener { generate() } }
	internal val classic = Checkbox(
		"Start with \"Lorem ipsum dolor sit amet…\"",
		true,
	).apply { addValueChangeListener { generate() } }
	internal val output = OutputField("Text")

	init {
		add(
			optionsRow(unit, count, classic, generateButton, generateError),
			output,
		)
	}

	override fun produce() {
		val n = boundedInt(count) ?: return
		output.value = LoremIpsum.generate(unit.value, n, classic.value)
	}
}
