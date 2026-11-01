package eu.zeletrik.devcore.tools.timezone

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One local date-time shown across zones: `<zone>  <yyyy-MM-dd HH:mm[:ss]>  <±HH:MM>`, two spaces between columns. */
object TimeZones {
	private val MINUS = Char(0x2212)
	private val offset = DateTimeFormatter.ofPattern("xxx", Locale.ROOT)
	private val withSeconds = Regex("\\d{1,2}:\\d{2}:\\d{2}")

	fun convert(text: String, source: ZoneId, targets: List<ZoneId>): String {
		val value = text.trim()
		val local = try {
			LocalDateTime.parse(value.replaceFirst(' ', 'T'))
		} catch (e: DateTimeParseException) {
			throw IllegalArgumentException("Use an ISO-8601 local date-time, e.g. 2026-10-29T14:30 or 2026-10-29 14:30")
		}
		val time = DateTimeFormatter.ofPattern(if (withSeconds.containsMatchIn(value)) "HH:mm:ss" else "HH:mm", Locale.ROOT)
		// A gap moves forward by its length; an overlap takes the earlier offset.
		val start = ZonedDateTime.ofLocal(local, source, null)
		val gap = source.rules.getValidOffsets(local).isEmpty()
		val note =
			if (gap) " (adjusted: ${local.format(time)} doesn't exist in ${source.id} on ${local.toLocalDate()})" else ""
		fun line(at: ZonedDateTime) = "${at.zone.id}  ${at.toLocalDate()} ${at.format(time)}  ${at.format(offset)}"
		val others = targets.map { zone ->
			val there = start.withZoneSameInstant(zone)
			val days = ChronoUnit.DAYS.between(start.toLocalDate(), there.toLocalDate())
			line(there) + when {
				days == 0L -> ""
				days > 0 -> " (+$days day${if (days == 1L) "" else "s"})"
				else -> " ($MINUS${-days} day${if (days == -1L) "" else "s"})"
			}
		}
		return (listOf(line(start) + note) + others).joinToString("\n")
	}
}
