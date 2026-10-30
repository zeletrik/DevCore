package eu.zeletrik.devcore.tools.password

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.checkbox.CheckboxGroup
import com.vaadin.flow.component.html.Span
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
import eu.zeletrik.devcore.shell.textLine

@Route("password-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class PasswordView : GeneratorView(Tool.PASSWORD) {
	internal val passphrase = RadioButtonGroup("Type", false, true).apply {
		setItemLabelGenerator { if (it) "Passphrase" else "Password" }
		value = false
		addValueChangeListener { showOptions(); generate() }
	}
	internal val length = numberField("Length", 4..128, 20).apply { addValueChangeListener { generate() } }
	internal val sets = CheckboxGroup<CharSet>("Character sets").apply {
		setItems(*CharSet.entries.toTypedArray())
		setItemLabelGenerator { it.label }
		value = CharSet.entries.toSet()
		addValueChangeListener { generate() }
	}
	internal val excludeAmbiguous = Checkbox("Exclude ambiguous (I l 1 O 0 o |)").apply { addValueChangeListener { generate() } }
	internal val words = numberField("Words", 3..20, 6).apply { addValueChangeListener { generate() } }
	internal val separator = textLine("Separator").apply { value = "-"; addValueChangeListener { generate() } }
	internal val capitalize = Checkbox("Capitalize").apply { addValueChangeListener { generate() } }
	internal val count = numberField("Count", 1..1000, 1).apply { addValueChangeListener { generate() } }
	internal val entropy = Span()
	internal val output = OutputField("Passwords")
	private val passwordOptions = HorizontalLayout(length, sets, excludeAmbiguous)
	private val passphraseOptions = HorizontalLayout(words, separator, capitalize).apply { isVisible = false }

	init {
		listOf(passwordOptions, passphraseOptions).forEach {
			it.defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
			it.isWrap = true
		}
		add(
			HorizontalLayout(passphrase, count, generateButton).apply { defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE },
			passwordOptions,
			passphraseOptions,
			entropy,
			output,
		)
	}

	private fun showOptions() {
		passwordOptions.isVisible = !passphrase.value
		passphraseOptions.isVisible = passphrase.value
	}

	override fun produce() {
		val n = boundedInt(count, 1..1000) ?: return
		if (passphrase.value) {
			val k = boundedInt(words, 3..20) ?: return
			output.value = List(n) { Passwords.passphrase(k, separator.value, capitalize.value) }.joinToString("\n")
			entropy.text = "≈ ${Passwords.passphraseEntropy(k)} bits each"
		} else {
			val size = boundedInt(length, 4..128) ?: return
			sets.isInvalid = false
			if (sets.value.isEmpty()) return mark(sets, "Select at least one character set")
			output.value = List(n) { Passwords.password(size, sets.value, excludeAmbiguous.value) }.joinToString("\n")
			entropy.text = "≈ ${Passwords.passwordEntropy(size, sets.value, excludeAmbiguous.value)} bits each"
		}
	}
}
