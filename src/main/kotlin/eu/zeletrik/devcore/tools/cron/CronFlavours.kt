package eu.zeletrik.devcore.tools.cron

import org.springframework.scheduling.support.CronExpression
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class CronFlavour(val label: String) { UNIX("Unix"), SPRING("Spring"), QUARTZ("Quartz") }

/** An expression in Spring's six-field syntax, plus the classic cron "either day matches" rule when it applies. */
data class CronSchedule(val fields: List<String>, val eitherDay: Boolean) {
	val second get() = fields[0]
	val minute get() = fields[1]
	val hour get() = fields[2]
	val dayOfMonth get() = fields[3]
	val month get() = fields[4]
	val dayOfWeek get() = fields[5]
}

/** Turns each flavour into Spring syntax; Spring's CronExpression does the scheduling. */
object CronFlavours {
	private val macros = mapOf(
		"@yearly" to "0 0 1 1 *", "@annually" to "0 0 1 1 *", "@monthly" to "0 0 1 * *", "@weekly" to "0 0 * * 0",
		"@daily" to "0 0 * * *", "@midnight" to "0 0 * * *", "@hourly" to "0 * * * *",
	)

	fun parse(expression: String, flavour: CronFlavour): CronSchedule {
		val text = expression.trim()
		require(text != "@reboot") { "@reboot has no schedule" }
		val schedule = when (flavour) {
			CronFlavour.UNIX -> unix(text)
			CronFlavour.SPRING -> CronSchedule(fields(macros[text]?.let { "0 $it" } ?: text, 6, "A Spring expression has 6 fields: second minute hour day-of-month month day-of-week"), eitherDay = false)
			CronFlavour.QUARTZ -> quartz(text)
		}
		expressions(schedule) // validates every field
		return schedule
	}

	private fun unix(text: String): CronSchedule {
		val five = fields(macros[text] ?: text, 5, "A Unix expression has 5 fields: minute hour day-of-month month day-of-week")
		return CronSchedule(listOf("0") + five, eitherDay = five[2] != "*" && five[4] != "*")
	}

	private fun quartz(text: String): CronSchedule {
		val parts = text.split(Regex("\\s+"))
		require(parts.size in 6..7) { "A Quartz expression has 6 or 7 fields: second minute hour day-of-month month day-of-week [year]" }
		require(parts.size == 6 || parts[6] == "*") { "The year field isn't supported" }
		require((parts[3] == "?") != (parts[5] == "?")) { "Quartz needs exactly one of day-of-month and day-of-week to be '?'" }
		// Quartz counts weekdays 1–7 from Sunday; Spring counts 0–6.
		val dayOfWeek = parts[5].replace(Regex("(?<![#/0-9])[1-7]")) { (it.value.toInt() - 1).toString() }
		return CronSchedule(parts.take(5) + dayOfWeek, eitherDay = false)
	}

	private fun fields(text: String, count: Int, message: String): List<String> =
		text.split(Regex("\\s+")).also { require(it.size == count && it.none(String::isEmpty)) { message } }

	private fun expressions(schedule: CronSchedule): List<CronExpression> {
		val f = schedule.fields
		val sets = if (schedule.eitherDay) listOf(f.take(5) + "*", f.take(3) + "*" + f[4] + f[5]) else listOf(f)
		return sets.map { CronExpression.parse(it.joinToString(" ")) }
	}

	/** The next [count] runs after [now] in [zone], earliest first; empty when the expression never fires. */
	fun nextRuns(schedule: CronSchedule, now: Instant, zone: ZoneId, count: Int = 5): List<String> {
		val from = now.atZone(zone)
		return expressions(schedule)
			.flatMap { expression -> generateSequence(expression.next(from)) { expression.next(it) }.take(count).toList() }
			.distinct()
			.sortedWith(compareBy<ZonedDateTime> { it.toInstant() })
			.take(count)
			.map { it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) }
	}
}
