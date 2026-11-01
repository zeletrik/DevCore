package eu.zeletrik.devcore.tools.cron

import org.springframework.scheduling.support.CronExpression
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** The cron dialects: Unix five fields, Spring six, Quartz six or seven. */
enum class CronFlavor(val label: String) { UNIX("Unix"), SPRING("Spring"), QUARTZ("Quartz") }

/** Field positions in Spring's syntax: second, minute, hour, day-of-month, month, day-of-week. */
private const val DAY_OF_MONTH = 3
private const val MONTH = 4
private const val DAY_OF_WEEK = 5
private const val SPRING_FIELDS = 6

/** Quartz may add a year after Spring's six fields; Unix leaves out the second. */
private const val YEAR = 6
private const val UNIX_FIELDS = 5

/** A month has at most five of any weekday, so `#` ordinals run 1–5. */
private const val MAX_ORDINAL = 5

/**
 * An expression in Spring's six-field syntax, plus the classic cron "either day matches" rule when it applies.
 * [writtenDayOfWeek] keeps the user's own day-of-week field (Quartz counts 1–7 from Sunday) for the description.
 */
data class CronSchedule(
	val fields: List<String>,
	val eitherDay: Boolean,
	val flavor: CronFlavor = CronFlavor.SPRING,
	val writtenDayOfWeek: String = fields[DAY_OF_WEEK],
) {
	val second get() = fields[0]
	val minute get() = fields[1]
	val hour get() = fields[2]
	val dayOfMonth get() = fields[DAY_OF_MONTH]
	val month get() = fields[MONTH]
	val dayOfWeek get() = fields[DAY_OF_WEEK]

	/** A Unix job at fixed wall-clock times: neither its minute nor its hour field is a wildcard. */
	val fixedTime get() = flavor == CronFlavor.UNIX && '*' !in minute && '*' !in hour
}

/** Turns each flavor into Spring syntax; Spring's CronExpression does the scheduling. */
object CronFlavors {
	private val macros = mapOf(
		"@yearly" to "0 0 1 1 *", "@annually" to "0 0 1 1 *", "@monthly" to "0 0 1 * *", "@weekly" to "0 0 * * 0",
		"@daily" to "0 0 * * *", "@midnight" to "0 0 * * *", "@hourly" to "0 * * * *",
	)
	private val ordinal = Regex("#(\\d+)")

	/**
	 * One flavor into Spring syntax. Unix and Spring accept the `@hourly`…`@yearly` macros; `@reboot` has no schedule.
	 * Quartz may add a seventh field, the year, but only `*` is supported there.
	 */
	fun parse(expression: String, flavor: CronFlavor): CronSchedule {
		val text = expression.trim()
		require(text != "@reboot") { "@reboot has no schedule" }
		val schedule = when (flavor) {
			CronFlavor.UNIX -> unix(text)
			CronFlavor.SPRING -> CronSchedule(
				fields(
					macros[text]?.let { "0 $it" } ?: text,
					SPRING_FIELDS,
					"A Spring expression has 6 fields: second minute hour day-of-month month day-of-week",
				),
				eitherDay = false,
			)
			CronFlavor.QUARTZ -> quartz(text)
		}
		ordinal.findAll(schedule.dayOfWeek).forEach {
			require(it.groupValues[1].toInt() in 1..MAX_ORDINAL) { "The '#' ordinal must be 1 to 5" }
		}
		expressions(schedule) // validates every field
		return schedule
	}

	private fun unix(text: String): CronSchedule {
		// A zero second in front gives the five Unix fields Spring's positions.
		val six = listOf("0") + fields(
			macros[text] ?: text,
			UNIX_FIELDS,
			"A Unix expression has 5 fields: minute hour day-of-month month day-of-week",
		)
		val written = six[DAY_OF_WEEK]
		// Vixie cron reads `*` in day-of-week as 0–6 (from Sunday); Spring would read 1–7 (from Monday).
		val dayOfWeek = written.replace(Regex("^\\*/"), "0-6/")
		// Like Vixie and cronie: a field starting with `*` (even `*/2`) never triggers the "either day" rule.
		val eitherDay = !six[DAY_OF_MONTH].startsWith("*") && !written.startsWith("*")
		return CronSchedule(six.take(DAY_OF_WEEK) + dayOfWeek, eitherDay, CronFlavor.UNIX, written)
	}

	private fun quartz(text: String): CronSchedule {
		val parts = text.split(Regex("\\s+"))
		require(parts.size in SPRING_FIELDS..YEAR + 1) {
			"A Quartz expression has 6 or 7 fields: second minute hour day-of-month month day-of-week [year]"
		}
		require(parts.size == SPRING_FIELDS || parts[YEAR] == "*") { "The year field isn't supported" }
		require((parts[DAY_OF_MONTH] == "?") != (parts[DAY_OF_WEEK] == "?")) {
			"Quartz needs exactly one of day-of-month and day-of-week to be '?'"
		}
		// Quartz counts weekdays 1–7 from Sunday; Spring counts 0–6. A bare `L` is the last day of the week, Saturday.
		val written = parts[DAY_OF_WEEK]
		val dayOfWeek = when (written) {
			"L" -> "SAT"
			// Only a bare weekday digit moves down one: not an ordinal after `#`, not a step after `/`, not part of a
			// longer number.
			else ->
				written.replace(Regex("^\\*/"), "1-7/").replace(Regex("(?<![#/0-9])[1-7]")) { (it.value.toInt() - 1).toString() }
		}
		return CronSchedule(parts.take(DAY_OF_WEEK) + dayOfWeek, eitherDay = false, CronFlavor.QUARTZ, written)
	}

	private fun fields(text: String, count: Int, message: String): List<String> =
		text.split(Regex("\\s+")).also { require(it.size == count) { message } }

	/** One Spring expression per day rule: under "either day", two that each wildcard one day field; their runs merge. */
	private fun expressions(schedule: CronSchedule): List<CronExpression> {
		val f = schedule.fields
		val sets = if (schedule.eitherDay) listOf(f.with(DAY_OF_WEEK, "*"), f.with(DAY_OF_MONTH, "*")) else listOf(f)
		return sets.map { CronExpression.parse(it.joinToString(" ")) }
	}

	private fun List<String>.with(index: Int, field: String) = toMutableList().also { it[index] = field }

	/** The next [count] runs after [now] in [zone], earliest first; empty when the expression never fires. */
	fun nextRuns(schedule: CronSchedule, now: Instant, zone: ZoneId, count: Int = 5): List<String> {
		val runs =
			if (schedule.fixedTime) wallClockRuns(schedule, now, zone, count) else zonedRuns(schedule, now, zone, count)
		return runs.distinct()
			.sortedBy { it.toInstant() }
			.take(count)
			.map { it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) }
	}

	private fun zonedRuns(schedule: CronSchedule, now: Instant, zone: ZoneId, count: Int): List<ZonedDateTime> {
		val from = now.atZone(zone)
		return expressions(schedule).flatMap { expression ->
			generateSequence(expression.next(from)) { expression.next(it) }.take(count).toList()
		}
	}

	/**
	 * cronie's rule for fixed-time jobs at DST changes: a run whose local time falls in a gap happens when the gap
	 * ends, and a local time that occurs twice runs once, at its first occurrence.
	 */
	private fun wallClockRuns(schedule: CronSchedule, now: Instant, zone: ZoneId, count: Int): List<ZonedDateTime> {
		val rules = zone.rules
		val from = now.atZone(zone).toLocalDateTime()
		return expressions(schedule).flatMap { expression ->
			generateSequence(expression.next(from)) { expression.next(it) }
				.map { local: LocalDateTime ->
					val offsets = rules.getValidOffsets(local)
					if (offsets.isEmpty()) {
						rules.getTransition(local).instant.atZone(zone)
					} else {
						ZonedDateTime.ofLocal(local, zone, offsets.first())
					}
				}
				.filter { it.toInstant().isAfter(now) }
				.take(count)
				.toList()
		}
	}
}
