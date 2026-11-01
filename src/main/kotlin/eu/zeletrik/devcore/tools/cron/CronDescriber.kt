package eu.zeletrik.devcore.tools.cron

private val number = Regex("\\d+")

/** Plain-English descriptions: a time part, then date parts, comma-separated. */
object CronDescriber {
	private val months = listOf(
		"January",
		"February",
		"March",
		"April",
		"May",
		"June",
		"July",
		"August",
		"September",
		"October",
		"November",
		"December",
	)
	private val monthNames = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

	fun describe(schedule: CronSchedule): String {
		val s = schedule.copy(
			fields = schedule.fields.map(::withoutUnitStep),
			writtenDayOfWeek = withoutUnitStep(schedule.writtenDayOfWeek),
		)
		val dates = listOfNotNull(days(s), month(s.month))
		return (listOf(time(s)) + dates).joinToString(", ")
	}

	/** A step of 1 means the same as no step, so a stepped wildcard or range is described without it. */
	private fun withoutUnitStep(field: String) = field.split(",").joinToString(",") { it.removeSuffix("/1") }

	private fun time(s: CronSchedule): String {
		val everyHour = s.second == "0" && s.hour == "*"
		return when {
			isNumber(s.minute) && isNumber(s.hour) && isNumber(s.second) -> clockTime(s)
			everyHour && s.minute == "*" -> "Every minute"
			everyHour && s.minute.startsWith("*/") -> "Every ${s.minute.removePrefix("*/")} minutes"
			everyHour && isNumber(s.minute) -> "At minute ${s.minute} of every hour"
			s.second == "0" && s.minute == "*" -> "Every minute, " + field(s.hour, "hour", "hours")
			else -> timeFields(s)
		}
	}

	/** `At 09:30`, with seconds only when they aren't zero. */
	private fun clockTime(s: CronSchedule): String {
		val seconds = if (s.second.toInt() != 0) ":%02d".format(s.second.toInt()) else ""
		return "At %02d:%02d".format(s.hour.toInt(), s.minute.toInt()) + seconds
	}

	private fun timeFields(s: CronSchedule) = listOfNotNull(
		if (s.second != "0") field(s.second, "second", "seconds") else null,
		if (s.minute != "*") field(s.minute, "minute", "minutes") else null,
		if (s.hour != "*") field(s.hour, "hour", "hours") else null,
	).joinToString(", ").replaceFirstChar { it.uppercase() }

	private fun days(s: CronSchedule): String? {
		val dom = Days.dayOfMonth(s.dayOfMonth)
		val dow = Days.dayOfWeek(s.writtenDayOfWeek, s.flavor)
		return when {
			dom != null && dow != null -> if (s.eitherDay) "$dom or $dow" else "$dom, $dow"
			else -> dom ?: dow
		}
	}

	private fun month(value: String): String? = when {
		value == "*" || value == "?" -> null
		"/" in value -> "in " + andList(expand(value, 1..months.size, ::monthNumber).distinct().map { months[it - 1] })
		"-" in value && "," !in value -> value.split("-").let { (a, b) -> "${monthName(a)} through ${monthName(b)}" }
		else -> "in " + andList(value.split(",").map(::monthName))
	}

	private fun monthNumber(value: String) = value.toIntOrNull() ?: (monthNames.indexOf(value.uppercase()) + 1)

	private fun monthName(value: String): String = months.getOrElse(monthNumber(value) - 1) { value }
}

/** The day-of-month and day-of-week parts. */
private object Days {
	private val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
	private val dayNames = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
	private val ordinals = listOf("first", "second", "third", "fourth", "fifth")

	fun dayOfMonth(value: String): String? = when {
		value == "*" || value == "?" -> null
		value == "L" -> "on the last day of the month"
		value == "LW" -> "on the last weekday of the month"
		value.startsWith("L-") -> "${value.removePrefix("L-")} days before the last day of the month"
		value.endsWith("W") -> "on the weekday nearest day ${value.removeSuffix("W")}"
		isNumber(value) -> "on day $value of the month"
		else -> "on " + field(value, "day", "days") + " of the month"
	}

	/**
	 * Day-of-week in the user's own numbering: Quartz counts 1–7 from Sunday; Unix and Spring count 0–7, with 0 and 7
	 * both Sunday.
	 */
	fun dayOfWeek(value: String, flavor: CronFlavor): String? {
		val quartz = flavor == CronFlavor.QUARTZ
		val items = value.split(",")
		return when {
			value == "*" || value == "?" -> null
			quartz && value == "L" -> "on Saturday"
			items.any { it.endsWith("L") || "#" in it } ->
				"on " + andList(items.map { weekdayOfMonth(it, quartz) }) + " of the month"
			"/" in value -> {
				// `*` covers 0–6 in Unix (from Sunday), 1–7 in Spring (from Monday) and 1–7 in Quartz (from Sunday).
				val all = if (flavor == CronFlavor.UNIX) 0..6 else 1..7
				val numbers = expand(value, all) { dayNumber(it, quartz) }.distinctBy { dayIndex(it, quartz) }
				"on " + andList(numbers.map { days[dayIndex(it, quartz)] })
			}
			"-" in value && "," !in value ->
				value.split("-").let { (a, b) -> "${dayName(a, quartz)} through ${dayName(b, quartz)}" }
			else -> "on " + andList(items.map { dayName(it, quartz) })
		}
	}

	/** `MON#2` → "the second Monday", `5L` → "the last Thursday" (Quartz), `MON` → "Monday". */
	private fun weekdayOfMonth(item: String, quartz: Boolean): String = when {
		"#" in item -> {
			val ordinal = ordinals.getOrNull(item.substringAfter('#').toInt() - 1) ?: item.substringAfter('#')
			"the $ordinal ${dayName(item.substringBefore('#'), quartz)}"
		}
		item.endsWith("L") -> "the last ${dayName(item.removeSuffix("L"), quartz)}"
		else -> dayName(item, quartz)
	}

	/** A day name as the user's own number: Quartz 1–7 from Sunday, Unix and Spring 0–6; a number passes through. */
	private fun dayNumber(value: String, quartz: Boolean): Int =
		value.toIntOrNull() ?: (dayNames.indexOf(value.uppercase()) + if (quartz) 1 else 0)

	/** Index into [days] (Sunday first) for a number in the user's numbering; 7 wraps to Sunday. */
	private fun dayIndex(number: Int, quartz: Boolean) = Math.floorMod(if (quartz) number - 1 else number, days.size)

	private fun dayName(value: String, quartz: Boolean): String {
		val index = value.toIntOrNull()?.let { dayIndex(it, quartz) } ?: dayNames.indexOf(value.uppercase())
		return days.getOrElse(index) { value }
	}
}

/** Every value a list of wildcards, values and ranges, each optionally stepped, selects within [all]. */
private fun expand(field: String, all: IntRange, value: (String) -> Int): List<Int> =
	field.split(",").flatMap { item ->
		val stride = item.substringAfter('/', "1").toInt()
		val range = item.substringBefore('/')
		val (from, to) = when {
			range == "*" -> all.first to all.last
			"-" in range -> value(range.substringBefore('-')) to value(range.substringAfter('-'))
			"/" in item -> value(range) to all.last
			else -> value(range) to value(range)
		}
		(from..to step stride).toList()
	}.sorted()

/** One field the special cases don't cover: `every n units`, `unit a`, `units a through b`, `units a, b and c`. */
private fun field(value: String, unit: String, units: String): String = when {
	value == "*" -> "every $unit"
	value.startsWith("*/") -> "every ${value.removePrefix("*/")} $units"
	"/" in value -> value.split("/").let { (range, step) -> "every $step $units, " + field(range, unit, units) }
	isNumber(value) -> "$unit $value"
	"," in value -> "$units " + andList(value.split(",").map { it.replace("-", " through ") })
	"-" in value -> "$units " + value.replace("-", " through ")
	else -> "$unit $value"
}

/** `a`, `a and b`, `a, b and c`. */
private fun andList(items: List<String>) =
	if (items.size == 1) items[0] else items.dropLast(1).joinToString(", ") + " and " + items.last()

private fun isNumber(value: String) = number.matches(value)
