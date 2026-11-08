package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.combobox.ComboBox
import java.time.ZoneId

/** The picked zone; UTC when the box has been cleared. */
val ComboBox<String>.zoneId: ZoneId get() = ZoneId.of(value ?: "UTC")

/** IANA zones, UTC until the browser reports its own; a zone the user picked is never overwritten. */
fun zonePicker(label: String = "Time zone", onChange: () -> Unit): ComboBox<String> {
	var chosen = false
	return ComboBox(label, ZoneId.getAvailableZoneIds().sorted()).apply {
		value = "UTC"
		addValueChangeListener {
			if (it.isFromClient) chosen = true
			onChange()
		}
		fun preselect(id: String?) {
			val browserZone = id?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
			if (!chosen && browserZone != null) value = browserZone
		}
		addAttachListener { event ->
			// The browser sends its details with the first request; a placeholder without them takes one round trip.
			val details = event.ui.page.extendedClientDetails
			if (details.timeZoneId != null) preselect(details.timeZoneId) else details.refresh { preselect(it.timeZoneId) }
		}
	}
}
