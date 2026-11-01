package eu.zeletrik.devcore.tools.uuid

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.GeneratorView
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.boundedInt
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.numberField
import eu.zeletrik.devcore.shell.optionsRow

/** Random v4, time-ordered v7 or ULIDs, in batches. */
@Route("uuid-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class UuidGeneratorView : GeneratorView(Tool.UUID) {
	internal val uppercase = Checkbox("Uppercase").apply { addValueChangeListener { generate() } }
	internal val hyphens = Checkbox("Hyphens", true).apply { addValueChangeListener { generate() } }
	internal val version: RadioButtonGroup<IdVersion> =
		choice("Version", IdVersion.entries, IdVersion.V4, IdVersion::label) {
		// ULIDs are always uppercase and never hyphenated, so those options only apply to UUIDs.
		val uuid = version.value != IdVersion.ULID
		uppercase.isEnabled = uuid
		hyphens.isEnabled = uuid
		output.relabel(if (uuid) "UUIDs" else "ULIDs")
		generate()
	}
	internal val count = numberField("Count", 1..1000, 1).apply { addValueChangeListener { generate() } }
	internal val output = OutputField("UUIDs")

	init {
		add(optionsRow(version, count, uppercase, hyphens, generateButton, generateError), output)
	}

	override fun produce() {
		val n = boundedInt(count) ?: return
		output.value = Uuids.generate(version.value, n, System.currentTimeMillis(), uppercase.value, hyphens.value)
	}
}
