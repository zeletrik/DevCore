package eu.zeletrik.devcore.tools.jwt

import eu.zeletrik.devcore.tools.json.JsonFormatter
import eu.zeletrik.devcore.tools.timestamp.relativeTime
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.RoundingMode
import java.time.Instant
import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.temporal.ChronoUnit
import java.util.Base64

data class DecodedJwt(val header: String, val payload: String, val signature: String, val times: String, val status: String)

/** Decodes only — nothing here verifies a signature. */
object JwtDecoder {
	private val mapper = JsonMapper()
	private val bearer = Regex("^bearer\\s+", RegexOption.IGNORE_CASE)
	private val timeClaims = listOf("exp", "iat", "nbf")

	fun decode(text: String, now: Instant): DecodedJwt {
		val parts = text.trim().replace(bearer, "").split('.')
		if (parts.size == 5) throw IllegalArgumentException("Encrypted JWTs (JWE) are not supported")
		if (parts.size != 3) throw IllegalArgumentException("Not a JWT: expected 3 dot-separated parts")
		val (headerJson, _) = jsonObject(parts[0], "Header")
		val (payloadJson, claims) = jsonObject(parts[1], "Payload")

		val times = timeClaims.associateWith { claims.get(it)?.let(::instantOf) }
		val exp = times["exp"]
		val nbf = times["nbf"]
		val status = when {
			exp != null && !now.isBefore(exp) -> "Expired"
			nbf != null && now.isBefore(nbf) -> "Not yet valid"
			exp != null -> "Active"
			else -> "No expiry"
		}
		val timeLines = times.mapNotNull { (claim, instant) ->
			instant?.let { "$claim: ${ISO_INSTANT.format(it)} (${relativeTime(it, now)})" }
		}
		return DecodedJwt(headerJson, payloadJson, parts[2], timeLines.joinToString("\n"), status)
	}

	private fun jsonObject(part: String, name: String): Pair<String, JsonNode> {
		val error = IllegalArgumentException("$name is not a base64url-encoded JSON object")
		val json = try {
			String(Base64.getUrlDecoder().decode(part), Charsets.UTF_8)
		} catch (e: IllegalArgumentException) {
			throw error
		}
		val node = try {
			mapper.readTree(json)
		} catch (e: JacksonException) {
			throw error
		}
		if (node == null || !node.isObject) throw error
		return JsonFormatter.format(json) to node
	}

	/** NumericDate, fractional seconds allowed; anything else (strings, absurd values) stays uninterpreted. */
	private fun instantOf(node: JsonNode): Instant? {
		if (!node.isNumber) return null
		return runCatching {
			val value = node.decimalValue()
			val seconds = value.setScale(0, RoundingMode.FLOOR)
			val nanos = value.subtract(seconds).movePointRight(9).toLong()
			Instant.ofEpochSecond(seconds.longValueExact(), nanos).truncatedTo(ChronoUnit.MILLIS)
		}.getOrNull()
	}
}
