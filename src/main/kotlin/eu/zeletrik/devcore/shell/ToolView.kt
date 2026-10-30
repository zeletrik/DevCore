package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.textfield.TextFieldBase
import com.vaadin.flow.router.BeforeEnterEvent
import com.vaadin.flow.router.BeforeEnterObserver
import com.vaadin.flow.router.HasDynamicTitle
import eu.zeletrik.devcore.tools.FieldError

const val MAX_INPUT_CHARS = 1_000_000
private const val TOO_LARGE = "Input is too large (max 1,000,000 characters)"

/** Every tool page: its title, a one-line description, and the deep-link `mode` (tools without modes ignore it). */
abstract class ToolView(val tool: Tool) : VerticalLayout(), HasDynamicTitle, BeforeEnterObserver {
	internal val intro = Paragraph(tool.description).apply {
		style.set("margin", "0").set("color", "var(--vaadin-text-color-secondary)")
	}

	init {
		add(intro)
	}

	override fun getPageTitle() = "${tool.title} — DevCore"

	protected open fun applyMode(mode: String?) {}

	override fun beforeEnter(event: BeforeEnterEvent) {
		event.location.queryParameters.getSingleParameter("mode").ifPresent { applyMode(it) }
	}
}

/**
 * Options → inputs → outputs. Owns the input cap, empty and invalid handling, live recompute and the deep-link
 * `input`. Subclasses render outputs and throw IllegalArgumentException (or [FieldError] for a named field).
 */
abstract class TextToolView<F : TextFieldBase<F, String>>(tool: Tool, internal val input: F) : ToolView(tool) {
	private val required = mutableListOf<TextFieldBase<*, String>>()
	private val byKey = mutableMapOf<String, TextFieldBase<*, String>>()

	init {
		input.addValueChangeListener { recompute() }
	}

	protected abstract fun render(text: String)

	protected abstract fun clearOutputs()

	/** Whether the deep-link `input` fills the primary input right now. */
	protected open fun acceptsInputParam() = true

	/** Whether there is nothing to compute; empty input means empty output and no error. */
	protected open fun isEmpty() = input.value.isEmpty()

	/** Registers another input with the same cap, error and recompute rules; [key] matches a [FieldError]. */
	protected fun <T : TextFieldBase<T, String>> field(component: T, key: String, optional: Boolean = false): T {
		component.addValueChangeListener { recompute() }
		if (!optional) required += component
		byKey[key] = component
		return component
	}

	/** Runs work that depends only on an optional field; a failure marks just that field and returns null. */
	protected fun <R> optional(component: TextFieldBase<*, String>, work: () -> R): R? {
		component.isInvalid = false
		if (component.value.length > MAX_INPUT_CHARS) return null.also { mark(component, TOO_LARGE) }
		return try {
			work()
		} catch (e: IllegalArgumentException) {
			null.also { mark(component, e.message ?: "Invalid value") }
		}
	}

	protected fun recompute() {
		val fields = listOf(input) + required
		fields.forEach { it.isInvalid = false }
		val tooLarge = fields.firstOrNull { it.value.length > MAX_INPUT_CHARS }
		when {
			tooLarge != null -> fail(tooLarge, TOO_LARGE)
			isEmpty() -> clearOutputs()
			else -> try {
				render(input.value)
			} catch (e: FieldError) {
				fail(byKey[e.field] ?: input, e.message ?: "Invalid input")
			} catch (e: IllegalArgumentException) {
				fail(input, e.message ?: "Invalid input")
			}
		}
	}

	private fun fail(field: TextFieldBase<*, String>, message: String) {
		mark(field, message)
		clearOutputs()
	}

	private fun mark(field: TextFieldBase<*, String>, message: String) {
		field.errorMessage = message
		field.isInvalid = true
	}

	override fun beforeEnter(event: BeforeEnterEvent) {
		super.beforeEnter(event)
		if (acceptsInputParam()) event.location.queryParameters.getSingleParameter("input").ifPresent { input.value = it }
	}
}

enum class Mode(val label: String) {
	ENCODE("Encode"), DECODE("Decode");

	companion object {
		/** Deep-link `mode` by name; any other value means the tool's default. */
		fun fromParam(value: String?, default: Mode = ENCODE) = entries.firstOrNull { it.name.lowercase() == value } ?: default
	}
}

fun modeGroup(encode: String = Mode.ENCODE.label, decode: String = Mode.DECODE.label) = RadioButtonGroup<Mode>("Mode").apply {
	setItems(*Mode.entries.toTypedArray())
	setItemLabelGenerator { if (it == Mode.ENCODE) encode else decode }
	value = Mode.ENCODE
}
