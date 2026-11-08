package eu.zeletrik.devcore.tools.timezone

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.combobox.MultiSelectComboBox
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.optionsRow
import eu.zeletrik.devcore.shell.textLine
import eu.zeletrik.devcore.shell.zoneId
import eu.zeletrik.devcore.shell.zonePicker
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A local date-time in the source zone shown in each target zone. */
@Route("time-zone-converter", layout = MainLayout::class)
@SpringComponent
@UIScope
class TimeZoneView : TextToolView<TextField>(Tool.TIME_ZONE, textLine("Date and time")) {
	internal val source = zonePicker("Source zone") { recompute() }
	internal val targets = MultiSelectComboBox<String>("Target zones").apply {
		setItems(ZoneId.getAvailableZoneIds().sorted())
		value = setOf("UTC")
		setWidthFull()
		addValueChangeListener { recompute() }
	}
	internal val now = Button("Now") {
		input.value = LocalDateTime.now(source.zoneId).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
	}
	internal val output = OutputField("In each zone")

	init {
		add(
			optionsRow(source, now),
			targets,
			input,
			output,
		)
	}

	override fun render(text: String) {
		output.value = TimeZones.convert(text, source.zoneId, targets.value.map(ZoneId::of))
	}

	override fun clearOutputs() = output.clear()
}
