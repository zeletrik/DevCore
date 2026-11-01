package eu.zeletrik.devcore.tools.jwt

import eu.zeletrik.devcore.tools.FieldError
import eu.zeletrik.devcore.tools.json.Indent
import eu.zeletrik.devcore.tools.json.JsonFormatter
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The HMAC algorithms the signer and checker support; [mac] is the JCA name. */
enum class JwtAlgorithm(val mac: String) { HS256("HmacSHA256"), HS384("HmacSHA384"), HS512("HmacSHA512") }

/** HMAC-signed JWTs only (HS256/384/512); the secret is the UTF-8 bytes of the text. */
object JwtSigner {
	const val UNSUPPORTED = "Signature check supports HS256, HS384 and HS512 only"
	private val encoder = Base64.getUrlEncoder().withoutPadding()

	/** "Signature valid", "Signature invalid", or a notice for algorithms this tool can't check. */
	fun check(token: String, secret: String): String {
		val parts = jwtParts(token)
		val header = runCatching { jwtMapper.readTree(Base64.getUrlDecoder().decode(parts[0])) }.getOrNull()
		val alg = header?.get("alg")?.asString()
		val algorithm = JwtAlgorithm.entries.firstOrNull { it.name == alg }
		return when {
			algorithm == null -> UNSUPPORTED
			parts.size == JWS_PARTS && signatureMatches(parts, algorithm, secret) -> "Signature valid"
			else -> "Signature invalid"
		}
	}

	/** As text: a Base64 decoder ignores the spare bits of the last character, so different strings can decode alike. */
	private fun signatureMatches(parts: List<String>, algorithm: JwtAlgorithm, secret: String): Boolean {
		val expected = encoder.encodeToString(mac(algorithm, secret, "${parts[0]}.${parts[1]}"))
		return MessageDigest.isEqual(expected.toByteArray(), parts[2].trimEnd('=').toByteArray())
	}

	/** Header `{"alg":…,"typ":"JWT"}`, the payload minified without touching its numbers or member order. */
	fun sign(payload: String, algorithm: JwtAlgorithm, secret: String): String {
		val minified = JsonFormatter.format(payload, Indent.MINIFIED)
		require(jwtMapper.readTree(minified).isObject) { "Payload must be a JSON object" }
		if (secret.isEmpty()) throw FieldError("secret", "Enter a secret to sign the token")
		val header = encoder.encodeToString("""{"alg":"${algorithm.name}","typ":"JWT"}""".toByteArray())
		val body = encoder.encodeToString(minified.toByteArray(Charsets.UTF_8))
		return "$header.$body.${encoder.encodeToString(mac(algorithm, secret, "$header.$body"))}"
	}

	private fun mac(algorithm: JwtAlgorithm, secret: String, signingInput: String): ByteArray =
		Mac.getInstance(algorithm.mac).apply { init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm.mac)) }
			.doFinal(signingInput.toByteArray(Charsets.US_ASCII))
}
