package eu.zeletrik.devcore.tools.cron

import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textLine
import eu.zeletrik.devcore.shell.zonePicker
import java.time.Instant
import java.time.ZoneId

@Route("cron-explainer", layout = MainLayout::class)
@SpringComponent
@UIScope
class CronView : TextToolView<TextField>(Tool.CRON, textLine("Expression")) {
	internal val flavour = RadioButtonGroup("Flavour", *CronFlavour.entries.toTypedArray()).apply {
		setItemLabelGenerator { it.label }
		value = CronFlavour.UNIX
		addValueChangeListener { recompute() }
	}
	internal val zone = zonePicker { recompute() }
	internal val description = OutputField("Description")
	internal val runs = OutputField("Next 5 runs")

	init {
		add(
			HorizontalLayout(flavour, zone).apply { defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE; isWrap = true },
			input,
			description,
			runs,
		)
	}

	override fun render(text: String) {
		val schedule = CronFlavours.parse(text, flavour.value)
		description.value = CronDescriber.describe(schedule)
		runs.value = CronFlavours.nextRuns(schedule, Instant.now(), ZoneId.of(zone.value ?: "UTC"))
			.ifEmpty { listOf("No upcoming runs") }
			.joinToString("\n")
	}

	override fun clearOutputs() {
		description.clear()
		runs.clear()
	}
}
