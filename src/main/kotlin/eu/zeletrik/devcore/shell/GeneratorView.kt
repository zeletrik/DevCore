package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.AttachEvent
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.shared.HasValidationProperties

/**
 * Options → Generate → output. The first output comes on first attach, never in the constructor, so a hidden
 * tool (rerouted before it attaches) computes nothing.
 */
abstract class GeneratorView(tool: Tool) : ToolView(tool) {
	internal val generateButton = Button("Generate") { generate() }.apply { addThemeVariants(ButtonVariant.PRIMARY) }
	private var generated = false

	/** Produce new output for the current options, or keep the previous output when an option is invalid. */
	protected abstract fun produce()

	protected fun generate() {
		produce()
		generated = true
	}

	protected fun mark(field: HasValidationProperties, message: String) {
		field.errorMessage = message
		field.isInvalid = true
	}

	override fun onAttach(attachEvent: AttachEvent) {
		super.onAttach(attachEvent)
		if (!generated) generate()
	}
}
