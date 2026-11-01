package eu.zeletrik.devcore.tools.jwt

import eu.zeletrik.devcore.tools.FieldError
import eu.zeletrik.devcore.tools.json.Indent
import eu.zeletrik.devcore.tools.json.JsonFormatter
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class JwtAlgorithm(val mac: String) { HS256("HmacSHA256"), HS384("HmacSHA384"), HS512("HmacSHA512") }

/** HMAC-signed JWTs only (HS256/384/512); the secret is the UTF-8 bytes of the text. */
object JwtSigner {
	const val UNSUPPORTED = "Signature check supports HS256, HS384 and HS512 only"
	private val mapper = JsonMapper()
	private val encoder = Base64.getUrlEncoder().withoutPadding()
	private val bearer = Regex("^bearer\\s+", RegexOption.IGNORE_CASE)

	/** "Signature valid", "Signature invalid", or a notice for algorithms this tool can't check. */
	fun check(token: String, secret: String): String {
		val parts = token.trim().replace(bearer, "").split('.')
		val alg = runCatching { mapper.readTree(Base64.getUrlDecoder().decode(parts[0])).get("alg")?.asString() }.getOrNull()
		val algorithm = JwtAlgorithm.entries.firstOrNull { it.name == alg }
		val valid = algorithm != null && parts.size == JWS_PARTS && run {
			// Compared as text: a decoder ignores the spare bits of the last character, so different strings can decode alike.
			val expected = encoder.encodeToString(mac(algorithm, secret, "${parts[0]}.${parts[1]}"))
			MessageDigest.isEqual(expected.toByteArray(), parts[2].trimEnd('=').toByteArray())
		}
		return when {
			algorithm == null -> UNSUPPORTED
			valid -> "Signature valid"
			else -> "Signature invalid"
		}
	}

	/** Header `{"alg":…,"typ":"JWT"}`, the payload minified without touching its numbers or member order. */
	fun sign(payload: String, algorithm: JwtAlgorithm, secret: String): String {
		val minified = JsonFormatter.format(payload, Indent.MINIFIED)
		require(mapper.readTree(minified).isObject) { "Payload must be a JSON object" }
		if (secret.isEmpty()) throw FieldError("secret", "Enter a secret to sign the token")
		val header = encoder.encodeToString("""{"alg":"${algorithm.name}","typ":"JWT"}""".toByteArray())
		val body = encoder.encodeToString(minified.toByteArray(Charsets.UTF_8))
		return "$header.$body.${encoder.encodeToString(mac(algorithm, secret, "$header.$body"))}"
	}

	private fun mac(algorithm: JwtAlgorithm, secret: String, signingInput: String): ByteArray =
		Mac.getInstance(algorithm.mac).apply { init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm.mac)) }
			.doFinal(signingInput.toByteArray(Charsets.US_ASCII))
}
