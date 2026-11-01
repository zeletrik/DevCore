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
		addAttachListener { event ->
			event.ui.page.retrieveExtendedClientDetails { details ->
				val browserZone = details.timeZoneId?.takeIf { id -> runCatching { ZoneId.of(id) }.isSuccess }
				if (!chosen && browserZone != null) value = browserZone
			}
		}
	}
}
