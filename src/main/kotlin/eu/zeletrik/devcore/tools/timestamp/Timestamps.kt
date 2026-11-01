package eu.zeletrik.devcore.tools.timestamp

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale

/** One instant in every form the Timestamp Converter shows. */
data class Converted(
	val seconds: Long,
	val millis: Long,
	val utc: String,
	val zoned: String,
	val relative: String,
	val rfc1123: String,
	val sql: String,
)

/** Reads timestamps in the forms [FORMATS] lists and writes them as [Converted]. */
object Timestamps {
	/** 1e11 s is year 5138 and 1e11 ms is 1973, so the cut-off is unambiguous for real data. */
	private const val MILLIS_CUTOFF = 100_000_000_000L
	const val FORMATS = "Not a timestamp. Use Unix seconds or milliseconds, an ISO-8601 date-time with or without " +
		"offset (2026-10-28T10:00:00Z), an ISO-8601 date (2026-10-28), RFC 1123 (Wed, 28 Oct 2026 10:00:00 GMT) " +
		"or an SQL date-time (2026-10-28 10:00:00)"
	private val integer = Regex("-?\\d+")
	private val sql = DateTimeFormatterBuilder()
		.appendPattern("uuuu-MM-dd HH:mm:ss")
		.optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
		.toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
	private val sqlOut = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)

	/** ISO-8601 with a basic offset (`+0200`, as `date +%FT%T%z` writes it) and optional seconds and fraction. */
	private val basicOffset = DateTimeFormatterBuilder()
		.appendPattern("uuuu-MM-dd'T'HH:mm")
		.optionalStart()
			.appendPattern(":ss")
			.optionalStart()
			.appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
			.optionalEnd()
			.optionalEnd()
		.appendOffset("+HHmm", "Z")
		.toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

	/**
	 * Unix seconds or milliseconds (told apart at 1e11), or any form in [FORMATS]; a form without an offset is read in
	 * [zone].
	 */
	fun parse(text: String, zone: ZoneId): Instant {
		val value = text.trim()
		if (integer.matches(value)) {
			val n = value.toLongOrNull() ?: throw outOfRange()
			return if (n <= -MILLIS_CUTOFF || n >= MILLIS_CUTOFF) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n)
		}
		return runCatching { Instant.parse(value) }.getOrNull()
			?: runCatching { ZonedDateTime.parse(value, ISO_DATE_TIME).toInstant() }.getOrNull()
			?: runCatching { OffsetDateTime.parse(value, basicOffset).toInstant() }.getOrNull()
			?: runCatching { ZonedDateTime.parse(value, RFC_1123_DATE_TIME).toInstant() }.getOrNull()
			?: runCatching { LocalDateTime.parse(value).atZone(zone).toInstant() }.getOrNull()
			?: runCatching { LocalDateTime.parse(value, sql).atZone(zone).toInstant() }.getOrNull()
			?: runCatching { LocalDate.parse(value).atStartOfDay(zone).toInstant() }.getOrNull()
			?: throw IllegalArgumentException(FORMATS)
	}

	fun convert(instant: Instant, zone: ZoneId, now: Instant): Converted = try {
		Converted(
			seconds = instant.epochSecond,
			millis = instant.toEpochMilli(),
			utc = ISO_INSTANT.format(instant),
			zoned = ISO_OFFSET_DATE_TIME.format(instant.atZone(zone)),
			relative = relativeTime(instant, now),
			rfc1123 = RFC_1123_DATE_TIME.format(instant.atZone(ZoneOffset.UTC)),
			sql = sqlOut.format(instant.atZone(zone)),
		)
	} catch (e: ArithmeticException) {
		throw outOfRange(e)
	} catch (e: DateTimeException) {
		throw outOfRange(e)
	}

	/** The instant in [zone] with a java.time pattern such as `dd.MM.yyyy HH:mm`. */
	fun custom(instant: Instant, zone: ZoneId, pattern: String): String = try {
		DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(instant.atZone(zone))
	} catch (e: IllegalArgumentException) {
		throw IllegalArgumentException("Not a usable pattern: ${e.message}", e)
	} catch (e: DateTimeException) {
		throw IllegalArgumentException("Not a usable pattern: ${e.message}", e)
	}

	private fun outOfRange(cause: Throwable? = null) = IllegalArgumentException("Timestamp out of range", cause)
}

/** Largest whole unit among seconds, minutes, hours and days — no calendar math. */
fun relativeTime(instant: Instant, now: Instant): String {
	val delta = Duration.between(now, instant)
	val size = delta.abs()
	if (size < Duration.ofSeconds(1)) return "now"
	val (amount, unit) = when {
		size < Duration.ofMinutes(1) -> size.seconds to "second"
		size < Duration.ofHours(1) -> size.toMinutes() to "minute"
		size < Duration.ofDays(1) -> size.toHours() to "hour"
		else -> size.toDays() to "day"
	}
	val text = "$amount $unit${if (amount == 1L) "" else "s"}"
	return if (delta.isNegative) "$text ago" else "in $text"
}
