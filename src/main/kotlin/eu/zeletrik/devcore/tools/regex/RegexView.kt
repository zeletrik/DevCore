package eu.zeletrik.devcore.tools.regex

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.checkbox.CheckboxGroup
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea
import eu.zeletrik.devcore.shell.textLine
import eu.zeletrik.devcore.tools.FieldError

@Route("regex-tester", layout = MainLayout::class)
@SpringComponent
@UIScope
class RegexView : TextToolView<TextArea>(Tool.REGEX, textArea("Text")) {
	internal val pattern = field(textLine("Pattern"), "pattern").apply { helperText = "Java regular expression syntax" }
	internal val flags = CheckboxGroup<RegexFlag>("Flags").apply {
		setItems(*RegexFlag.entries.toTypedArray())
		setItemLabelGenerator { it.label }
		addValueChangeListener { recompute() }
	}
	internal val replace = Checkbox("Replace").apply { addValueChangeListener { showReplace(); recompute() } }
	internal val replacement = field(textLine("Replacement"), "replacement", optional = true).apply { isVisible = false }
	internal val matches = OutputField("Matches")
	internal val replaced = OutputField("Replaced").apply { isVisible = false }

	init {
		add(pattern, flags, replace, replacement, input, matches, replaced)
	}

	override fun isEmpty() = input.value.isEmpty() || pattern.value.isEmpty()

	override fun render(text: String) {
		val compiled = RegexTester.compile(pattern.value, flags.value)
		try {
			val deadline = RegexTester.deadline()
			matches.value = RegexTester.list(compiled, text, deadline)
			replaced.value = if (!replace.value) "" else optional(replacement) {
				RegexTester.replace(compiled, text, replacement.value, deadline)
			} ?: ""
		} catch (_: RegexTester.Timeout) {
			throw FieldError("pattern", RegexTester.STOPPED)
		}
	}

	override fun clearOutputs() {
		matches.clear()
		replaced.clear()
	}

	private fun showReplace() {
		replacement.isVisible = replace.value
		replaced.isVisible = replace.value
	}
}
