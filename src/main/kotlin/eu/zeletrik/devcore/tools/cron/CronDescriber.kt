package eu.zeletrik.devcore.tools.cron

/** Plain-English descriptions: a time part, then date parts, comma-separated. */
object CronDescriber {
	private val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
	private val monthNames = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
	private val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
	private val dayNames = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
	private val ordinals = listOf("first", "second", "third", "fourth", "fifth")
	private val number = Regex("\\d+")

	fun describe(schedule: CronSchedule): String {
		val dates = listOfNotNull(days(schedule), month(schedule.month))
		return (listOf(time(schedule)) + dates).joinToString(", ")
	}

	private fun time(s: CronSchedule): String {
		val second = s.second
		return when {
			isNumber(s.minute) && isNumber(s.hour) && isNumber(second) ->
				"At %02d:%02d".format(s.hour.toInt(), s.minute.toInt()) + if (second.toInt() != 0) ":%02d".format(second.toInt()) else ""
			second == "0" && s.hour == "*" && s.minute == "*" -> "Every minute"
			second == "0" && s.hour == "*" && s.minute.startsWith("*/") -> "Every ${s.minute.removePrefix("*/")} minutes"
			second == "0" && s.hour == "*" && isNumber(s.minute) -> "At minute ${s.minute} of every hour"
			else -> listOfNotNull(
				if (second != "0") field(second, "second", "seconds") else null,
				if (s.minute != "*") field(s.minute, "minute", "minutes") else null,
				if (s.hour != "*") field(s.hour, "hour", "hours") else null,
			).joinToString(", ").replaceFirstChar { it.uppercase() }
		}
	}

	private fun days(s: CronSchedule): String? {
		val dom = dayOfMonth(s.dayOfMonth)
		val dow = dayOfWeek(s.dayOfWeek)
		return when {
			dom != null && dow != null -> if (s.eitherDay) "$dom or $dow" else "$dom, $dow"
			else -> dom ?: dow
		}
	}

	private fun dayOfMonth(value: String): String? = when {
		value == "*" || value == "?" -> null
		value == "L" -> "on the last day of the month"
		value.endsWith("W") -> "on the weekday nearest day ${value.removeSuffix("W")}"
		isNumber(value) -> "on day $value of the month"
		else -> "on " + field(value, "day", "days") + " of the month"
	}

	private fun dayOfWeek(value: String): String? = when {
		value == "*" || value == "?" -> null
		value.endsWith("L") -> "on the last ${dayName(value.removeSuffix("L"))} of the month"
		"#" in value -> "on the ${ordinals[value.substringAfter('#').toInt() - 1]} ${dayName(value.substringBefore('#'))} of the month"
		"-" in value && "," !in value && "/" !in value -> value.split("-").let { (a, b) -> "${dayName(a)} through ${dayName(b)}" }
		else -> "on " + list(value.split(",").map(::dayName))
	}

	private fun month(value: String): String? = when {
		value == "*" || value == "?" -> null
		value.startsWith("*/") -> "every ${value.removePrefix("*/")} months"
		"-" in value && "," !in value && "/" !in value -> value.split("-").let { (a, b) -> "${monthName(a)} through ${monthName(b)}" }
		else -> "in " + list(value.split(",").map(::monthName))
	}

	/** One field the special cases don't cover: `every n units`, `unit a`, `units a through b`, `units a, b and c`. */
	private fun field(value: String, unit: String, units: String): String = when {
		value == "*" -> "every $unit"
		value.startsWith("*/") -> "every ${value.removePrefix("*/")} $units"
		"/" in value -> value.split("/").let { (range, step) -> "every $step $units, " + field(range, unit, units) }
		isNumber(value) -> "$unit $value"
		"," in value -> "$units " + list(value.split(",").map { it.replace("-", " through ") })
		"-" in value -> "$units " + value.replace("-", " through ")
		else -> "$unit $value"
	}

	private fun list(items: List<String>) = if (items.size == 1) items[0] else items.dropLast(1).joinToString(", ") + " and " + items.last()

	private fun isNumber(value: String) = number.matches(value)

	private fun dayName(value: String): String {
		val index = value.toIntOrNull() ?: dayNames.indexOf(value.uppercase())
		return days.getOrElse(index % 7) { value }
	}

	private fun monthName(value: String): String {
		val index = value.toIntOrNull()?.minus(1) ?: monthNames.indexOf(value.uppercase())
		return months.getOrElse(index) { value }
	}
}
