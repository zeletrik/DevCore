package eu.zeletrik.devcore.tools.numberbase

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textLine

@Route("number-base", layout = MainLayout::class)
@SpringComponent
@UIScope
class NumberBaseView : TextToolView<TextField>(Tool.NUMBER_BASE, textLine("Number")) {
	internal val base = RadioButtonGroup("Input base", *InputBase.entries.toTypedArray()).apply {
		setItemLabelGenerator { it.label }
		value = InputBase.AUTO
		addValueChangeListener { recompute() }
	}
	internal val uppercase = Checkbox("Uppercase hex").apply { addValueChangeListener { recompute() } }
	internal val binary = OutputField("Binary")
	internal val octal = OutputField("Octal")
	internal val decimal = OutputField("Decimal")
	internal val hex = OutputField("Hexadecimal")

	init {
		add(HorizontalLayout(base, uppercase).apply { defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE }, input, binary, octal, decimal, hex)
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
