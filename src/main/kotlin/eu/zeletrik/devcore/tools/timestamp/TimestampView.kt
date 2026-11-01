package eu.zeletrik.devcore.tools.timestamp

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea
import eu.zeletrik.devcore.shell.textLine
import eu.zeletrik.devcore.shell.zonePicker
import java.time.Instant
import java.time.ZoneId

@Route("timestamp-converter", layout = MainLayout::class)
@SpringComponent
@UIScope
class TimestampView : TextToolView<TextArea>(Tool.TIMESTAMP, textArea("Timestamp or date")) {
	internal val zone = zonePicker { recompute() }
	internal val now = Button("Now") { input.value = Instant.now().epochSecond.toString() }
	internal val seconds = OutputField("Unix seconds")
	internal val millis = OutputField("Unix milliseconds")
	internal val utc = OutputField("ISO-8601 (UTC)")
	internal val zoned = OutputField("ISO-8601 (selected zone)")
	internal val relative = OutputField("Relative")
	internal val rfc1123 = OutputField("RFC 1123 (GMT)")
	internal val sql = OutputField("SQL date-time (selected zone)")
	internal val format = field(
		textLine("Custom format").apply { helperText = "java.time pattern, e.g. dd.MM.yyyy HH:mm" },
		"format",
		optional = true,
	)
	internal val custom = OutputField("Custom")

	init {
		add(HorizontalLayout(zone, now).apply { defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE })
		add(input, seconds, millis, utc, zoned, relative, rfc1123, sql, format, custom)
	}

	override fun render(text: String) {
		val zoneId = ZoneId.of(zone.value ?: "UTC")
		val instant = Timestamps.parse(text, zoneId)
		val converted = Timestamps.convert(instant, zoneId, Instant.now())
		seconds.value = converted.seconds.toString()
		millis.value = converted.millis.toString()
		utc.value = converted.utc
		zoned.value = converted.zoned
		relative.value = converted.relative
		rfc1123.value = converted.rfc1123
		sql.value = converted.sql
		custom.value =
			if (format.value.isBlank()) "" else optional(format) { Timestamps.custom(instant, zoneId, format.value) } ?: ""
	}

	override fun clearOutputs() =
		listOf(seconds, millis, utc, zoned, relative, rfc1123, sql, custom).forEach { it.clear() }
}
