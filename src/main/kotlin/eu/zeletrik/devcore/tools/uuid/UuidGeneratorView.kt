package eu.zeletrik.devcore.tools.uuid

import com.vaadin.flow.component.AttachEvent
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.ToolView
import eu.zeletrik.devcore.shell.boundedInt
import eu.zeletrik.devcore.shell.numberField

@Route("uuid-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class UuidGeneratorView : ToolView(Tool.UUID) {
	internal val uppercase = Checkbox("Uppercase").apply { addValueChangeListener { generate() } }
	internal val hyphens = Checkbox("Hyphens", true).apply { addValueChangeListener { generate() } }
	internal val version = RadioButtonGroup("Version", *IdVersion.entries.toTypedArray()).apply {
		setItemLabelGenerator { it.label }
		value = IdVersion.V4
		addValueChangeListener {
			val uuid = it.value != IdVersion.ULID
			uppercase.isEnabled = uuid
			hyphens.isEnabled = uuid
			generate()
		}
	}
	internal val count = numberField("Count", 1..1000, 1).apply { addValueChangeListener { generate() } }
	internal val generateButton = Button("Generate") { generate() }.apply { addThemeVariants(ButtonVariant.PRIMARY) }
	internal val output = OutputField("UUIDs")

	init {
		add(
			HorizontalLayout(version, count, uppercase, hyphens, generateButton)
				.apply { defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE; isWrap = true },
			output,
		)
	}

	// First output on attach, not in the constructor: a hidden tool is rerouted before it ever attaches.
	override fun onAttach(attachEvent: AttachEvent) {
		super.onAttach(attachEvent)
		if (output.value.isEmpty()) generate()
	}

	private fun generate() {
		val n = boundedInt(count, 1..1000) ?: return
		output.value = Uuids.generate(version.value, n, System.currentTimeMillis(), uppercase.value, hyphens.value)
	}
}
