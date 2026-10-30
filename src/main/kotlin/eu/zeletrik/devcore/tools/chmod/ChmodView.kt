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
	private var syncing = false

	/** Bit → checkbox, in `rwxrwxrwx` order, then setuid, setgid, sticky. */
	internal val boxes: Map<Int, Checkbox> = buildMap {
		listOf("Owner", "Group", "Others").forEachIndexed { who, name ->
			listOf("Read", "Write", "Execute").forEachIndexed { what, permission ->
				put(1 shl (8 - (who * 3 + what)), Checkbox("$name $permission"))
			}
		}
		put(0x800, Checkbox("Setuid"))
		put(0x400, Checkbox("Setgid"))
		put(0x200, Checkbox("Sticky"))
	}.onEach { (_, box) -> box.addValueChangeListener { if (!syncing && it.isFromClient) fromBoxes() } }
	internal val octal = OutputField("Octal")
	internal val symbolic = OutputField("Symbolic")
	internal val command = OutputField("Command")

	init {
		val grid = Div().apply {
			style.set("display", "grid").set("grid-template-columns", "repeat(3, minmax(8rem, max-content))").set("gap", "0.25rem 1rem")
			boxes.values.forEach(::add)
		}
		add(input, Span("Permissions"), grid, octal, symbolic, command)
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
		syncing = true
		boxes.forEach { (bit, box) -> box.value = mode and bit != 0 }
		syncing = false
	}

	override fun clearOutputs() = listOf(octal, symbolic, command).forEach { it.clear() }
}
