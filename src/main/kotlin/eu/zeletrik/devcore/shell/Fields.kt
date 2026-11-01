package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.HasStyle
import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.html.AnchorTarget
import com.vaadin.flow.component.shared.HasValidationProperties
import com.vaadin.flow.component.textfield.IntegerField
import com.vaadin.flow.component.textfield.PasswordField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.textfield.TextFieldBase
import com.vaadin.flow.data.value.ValueChangeMode

// DevCore validates every field itself (TextToolView, boundedInt), so Vaadin's built-in validation stays off.
private fun <T : TextFieldBase<T, String>> T.asInput() = apply {
	setManualValidation(true)
	// The value arrives once typing pauses, not on every keystroke.
	valueChangeMode = ValueChangeMode.LAZY
	setWidthFull()
}

/** Multi-line monospace input. */
fun textArea(label: String) = TextArea(label).asInput().monospace()

/** Single-line monospace input. */
fun textLine(label: String) = TextField(label).asInput().monospace()

/** Masked single-line input; it keeps the proportional font, since nobody reads the value. */
fun secretField(label: String) = PasswordField(label).asInput()

/** Integer input with step buttons; [range] becomes the field's min and max, which [boundedInt] reads back. */
fun numberField(label: String, range: IntRange, initial: Int) = IntegerField(label).apply {
	min = range.first
	max = range.last
	value = initial
	isStepButtonsVisible = true
	setManualValidation(true)
}

/** The value when it is set and within the field's own min and max; otherwise marks the field and returns null. */
fun boundedInt(field: IntegerField): Int? {
	val range = field.min..field.max
	val value = field.value?.takeIf { it in range }
	field.isInvalid = value == null
	if (value == null) field.errorMessage = "Enter a number from ${range.first} to ${range.last}"
	return value
}

/** The single-line field's value as a whole number in [range]; otherwise marks it invalid and returns null. */
fun boundedLong(field: TextField, range: LongRange = Long.MIN_VALUE..Long.MAX_VALUE): Long? {
	val value = field.value.trim().toLongOrNull()?.takeIf { it in range }
	field.isInvalid = value == null
	if (value == null) {
		val full = range.first == Long.MIN_VALUE && range.last == Long.MAX_VALUE
		field.errorMessage = if (full) "Enter a whole number" else "Enter a whole number from ${range.first} to ${range.last}"
	}
	return value
}

/** Shows [message] on the field. */
internal fun HasValidationProperties.markInvalid(message: String) {
	errorMessage = message
	isInvalid = true
}

/** Code and tool output use the browser's monospace font. */
fun <T : HasStyle> T.monospace() = apply { style.set("font-family", "ui-monospace, monospace") }

/** Opens in a new tab without handing the opener window to the target site. */
fun Anchor.openInNewTab() = apply {
	setTarget(AnchorTarget.BLANK)
	element.setAttribute("rel", "noopener noreferrer")
}
