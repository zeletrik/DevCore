package eu.zeletrik.devcore.tools.random

import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.GeneratorView
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.boundedInt
import eu.zeletrik.devcore.shell.markInvalid
import eu.zeletrik.devcore.shell.numberField
import eu.zeletrik.devcore.shell.optionsRow
import eu.zeletrik.devcore.tools.FieldError

/** Distinct ports in a range; by default the well-known ports 0–1023 are left out. */
@Route("random-port-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class RandomPortView : GeneratorView(Tool.RANDOM_PORT) {
	internal val min = numberField("Min", 1..65535, 1024).apply { addValueChangeListener { generate() } }
	internal val max = numberField("Max", 1..65535, 65535).apply { addValueChangeListener { generate() } }
	internal val count = numberField("Count", 1..100, 1).apply { addValueChangeListener { generate() } }
	internal val output = OutputField("Ports")

	init {
		add(
			optionsRow(min, max, count, generateButton, generateError),
			output,
		)
	}

	override fun produce() {
		val from = boundedInt(min)
		val to = boundedInt(max)
		val n = boundedInt(count)
		if (from == null || to == null || n == null) return
		try {
			output.value = RandomNumbers.generate(from.toLong(), to.toLong(), n, unique = true).joinToString("\n")
		} catch (e: FieldError) {
			(if (e.field == "max") max else count).markInvalid(e.message!!)
		}
	}
}
