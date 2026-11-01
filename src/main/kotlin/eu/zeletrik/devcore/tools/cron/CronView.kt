package eu.zeletrik.devcore.tools.cron

import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.optionsRow
import eu.zeletrik.devcore.shell.textLine
import eu.zeletrik.devcore.shell.zoneId
import eu.zeletrik.devcore.shell.zonePicker
import java.time.Instant
import java.time.ZoneId

/** A cron expression in the chosen flavor, described in English and with its next five runs in a zone. */
@Route("cron-explainer", layout = MainLayout::class)
@SpringComponent
@UIScope
class CronView : TextToolView<TextField>(Tool.CRON, textLine("Expression")) {
	internal val flavor = choice("Flavor", CronFlavor.entries, CronFlavor.UNIX, CronFlavor::label) { recompute() }
	internal val zone = zonePicker { recompute() }
	internal val description = OutputField("Description")
	internal val runs = OutputField("Next 5 runs")

	init {
		add(
			optionsRow(flavor, zone),
			input,
			description,
			runs,
		)
	}

	override fun render(text: String) {
		val schedule = CronFlavors.parse(text, flavor.value)
		description.value = CronDescriber.describe(schedule)
		runs.value = CronFlavors.nextRuns(schedule, Instant.now(), zone.zoneId)
			.ifEmpty { listOf("No upcoming runs") }
			.joinToString("\n")
	}

	override fun clearOutputs() {
		description.clear()
		runs.clear()
	}
}
