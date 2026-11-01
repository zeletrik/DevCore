package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.AttachEvent
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.html.Span

/**
 * Options → Generate → output. The first output comes on first attach, never in the constructor, so a hidden
 * tool (rerouted before it attaches) computes nothing.
 */
abstract class GeneratorView(tool: Tool) : ToolView(tool) {
	internal val generateButton = Button("Generate") { generate() }.apply { addThemeVariants(ButtonVariant.PRIMARY) }

	/** Shown next to [generateButton] when generating fails unexpectedly; the previous output stays. */
	internal val generateError = Span("Couldn't generate output").apply {
		isVisible = false
		element.setAttribute("role", "alert")
		style.set("color", "var(--aura-red-text, var(--vaadin-input-field-error-color))")
	}
	private var generated = false

	/** Produce new output for the current options, or keep the previous output when an option is invalid. */
	protected abstract fun produce()

	protected fun generate() {
		generateError.isVisible = false
		runCatching(::produce).onFailure(::failed)
		generated = true
	}

	/** The safety net: anything but a JVM error is logged without the input and reported next to the button. */
	private fun failed(error: Throwable) {
		if (error !is StackOverflowError && error !is RuntimeException) throw error
		logFailure(tool, error)
		generateError.isVisible = true
	}

	override fun onAttach(attachEvent: AttachEvent) {
		super.onAttach(attachEvent)
		if (!generated) generate()
	}
}
