package eu.zeletrik.devcore.tools.numberbase

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.enumSelect
import eu.zeletrik.devcore.shell.optionsRow
import eu.zeletrik.devcore.shell.textLine

/** An integer of any size in binary, octal, decimal and hex; the input base is detected from a prefix unless chosen. */
@Route("number-base-converter", layout = MainLayout::class)
@SpringComponent
@UIScope
class NumberBaseView : TextToolView<TextField>(Tool.NUMBER_BASE, textLine("Number")) {
	internal val base = enumSelect("Input base", InputBase.entries, InputBase.AUTO, InputBase::label) { recompute() }
	internal val uppercase = Checkbox("Uppercase").apply { addValueChangeListener { recompute() } }
	internal val binary = OutputField("Binary")
	internal val octal = OutputField("Octal")
	internal val decimal = OutputField("Decimal")
	internal val hex = OutputField("Hexadecimal")

	init {
		add(
			optionsRow(base, uppercase),
			input,
			binary,
			octal,
			decimal,
			hex,
		)
	}

	override fun render(text: String) {
		val bases = NumberBases.convert(text, base.value, uppercase.value)
		binary.value = bases.binary
		octal.value = bases.octal
		decimal.value = bases.decimal
		hex.value = bases.hex
	}

	override fun clearOutputs() = listOf(binary, octal, decimal, hex).forEach { it.clear() }
}
