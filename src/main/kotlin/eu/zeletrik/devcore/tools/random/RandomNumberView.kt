package eu.zeletrik.devcore.tools.random

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.GeneratorView
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.boundedInt
import eu.zeletrik.devcore.shell.boundedLong
import eu.zeletrik.devcore.shell.numberField
import eu.zeletrik.devcore.shell.textLine
import eu.zeletrik.devcore.tools.FieldError

@Route("random-number-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class RandomNumberView : GeneratorView(Tool.RANDOM_NUMBER) {
	internal val min = textLine("Min").apply { value = "1"; addValueChangeListener { generate() } }
	internal val max = textLine("Max").apply { value = "100"; addValueChangeListener { generate() } }
	internal val count = numberField("Count", 1..1000, 1).apply { addValueChangeListener { generate() } }
	internal val unique = Checkbox("Unique").apply { addValueChangeListener { generate() } }
	internal val output = OutputField("Numbers")

	init {
		add(
			HorizontalLayout(min, max, count, unique, generateButton, generateError).apply {
				defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
				isWrap = true
			},
			output,
		)
	}

	override fun produce() {
		val from = boundedLong(min)
		val to = boundedLong(max)
		val n = boundedInt(count, 1..1000)
		if (from == null || to == null || n == null) return
		try {
			output.value = RandomNumbers.generate(from, to, n, unique.value).joinToString("\n")
		} catch (e: FieldError) {
			mark(if (e.field == "max") max else count, e.message!!)
		}
	}
}
