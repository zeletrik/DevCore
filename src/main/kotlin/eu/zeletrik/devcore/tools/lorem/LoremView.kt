package eu.zeletrik.devcore.tools.lorem

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.GeneratorView
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.boundedInt
import eu.zeletrik.devcore.shell.numberField

@Route("lorem-ipsum", layout = MainLayout::class)
@SpringComponent
@UIScope
class LoremView : GeneratorView(Tool.LOREM) {
	internal val unit = RadioButtonGroup("Unit", *LoremUnit.entries.toTypedArray()).apply {
		setItemLabelGenerator { it.label }
		value = LoremUnit.PARAGRAPHS
		addValueChangeListener { generate() }
	}
	internal val count = numberField("Count", 1..100, 3).apply { addValueChangeListener { generate() } }
	internal val classic = Checkbox("Start with \"Lorem ipsum dolor sit amet…\"", true).apply { addValueChangeListener { generate() } }
	internal val output = OutputField("Text")

	init {
		add(
			HorizontalLayout(unit, count, classic, generateButton).apply { defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE; isWrap = true },
			output,
		)
	}

	override fun produce() {
		val n = boundedInt(count, 1..100) ?: return
		output.value = LoremIpsum.generate(unit.value, n, classic.value)
	}
}
