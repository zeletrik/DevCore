package eu.zeletrik.devcore.tools.chmod

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textLine

/** Octal, symbolic and the checkbox grid stay in sync; a checkbox change writes the octal mode into the input. */
@Route("chmod-calculator", layout = MainLayout::class)
@SpringComponent
@UIScope
class ChmodView : TextToolView<TextField>(Tool.CHMOD, textLine("Mode")) {
	private val labels = listOf("Owner", "Group", "Others").flatMap { who ->
		listOf("Read", "Write", "Execute").map { "$who $it" }
	} + listOf("Setuid", "Setgid", "Sticky")

	/** Bit → checkbox, in the order of [Permissions.bits]; only a click writes back, never a server-side update. */
	internal val boxes: Map<Int, Checkbox> = Permissions.bits.zip(labels.map { Checkbox(it) }).toMap()
		.onEach { (_, box) -> box.addValueChangeListener { if (it.isFromClient) fromBoxes() } }
	internal val octal = OutputField("Octal")
	internal val symbolic = OutputField("Symbolic")
	internal val command = OutputField("Command")

	init {
		val label = Span("Permissions").apply { setId("chmod-permissions") }
		val grid = Div().apply {
			// One labelled group, so assistive technology announces the twelve checkboxes as a unit.
			element.setAttribute("role", "group")
			element.setAttribute("aria-labelledby", "chmod-permissions")
			style.set("display", "grid")
				.set("grid-template-columns", "repeat(3, minmax(8rem, max-content))")
				.set("gap", "0.25rem 1rem")
			boxes.values.forEach(::add)
		}
		add(input, label, grid, octal, symbolic, command)
	}

	private fun fromBoxes() {
		val mode = boxes.filterValues { it.value }.keys.fold(0) { acc, bit -> acc or bit }
		input.value = Permissions.octal(mode)
	}

	override fun render(text: String) {
		val mode = Permissions.parse(text)
		val octalText = Permissions.octal(mode)
		octal.value = octalText
		symbolic.value = Permissions.symbolic(mode)
		command.value = "chmod $octalText <file>"
		boxes.forEach { (bit, box) -> box.value = mode and bit != 0 }
	}

	override fun clearOutputs() = listOf(octal, symbolic, command).forEach { it.clear() }
}
