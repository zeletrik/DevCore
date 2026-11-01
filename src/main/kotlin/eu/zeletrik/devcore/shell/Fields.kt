package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.textfield.IntegerField
import com.vaadin.flow.component.textfield.PasswordField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode

// DevCore validates every field itself (TextToolView, boundedInt), so Vaadin's built-in validation stays off.

fun textArea(label: String) = TextArea(label).apply {
	setManualValidation(true)
	valueChangeMode = ValueChangeMode.LAZY
	setWidthFull()
	style.set("font-family", "ui-monospace, monospace")
}

fun textLine(label: String) = TextField(label).apply {
	setManualValidation(true)
	valueChangeMode = ValueChangeMode.LAZY
	setWidthFull()
	style.set("font-family", "ui-monospace, monospace")
}

fun secretField(label: String) = PasswordField(label).apply {
	setManualValidation(true)
	valueChangeMode = ValueChangeMode.LAZY
	setWidthFull()
}

fun numberField(label: String, range: IntRange, initial: Int) = IntegerField(label).apply {
	min = range.first
	max = range.last
	value = initial
	isStepButtonsVisible = true
	setManualValidation(true)
}

/** The field's value when it is set and in [range]; otherwise marks the field invalid and returns null. */
fun boundedInt(field: IntegerField, range: IntRange): Int? {
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
