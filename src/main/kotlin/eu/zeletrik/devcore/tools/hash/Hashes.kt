package eu.zeletrik.devcore.tools.hash

import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Digests and HMACs of a text's UTF-8 bytes as hex; the names are the JCA ones the UI lists. */
object Hashes {
	val ALGORITHMS = listOf("MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512")

	/** HMAC of the text's UTF-8 bytes, keyed with the key's UTF-8 bytes, with the same algorithm names. */
	fun hmac(text: String, key: String, algorithm: String, uppercase: Boolean = false): String {
		val name = "Hmac" + algorithm.replace("-", "")
		val mac = Mac.getInstance(name).apply { init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), name)) }
		return hex(uppercase).formatHex(mac.doFinal(text.toByteArray(Charsets.UTF_8)))
	}

	fun digest(text: String, algorithm: String, uppercase: Boolean = false): String =
		hex(uppercase).formatHex(MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8)))

	private fun hex(uppercase: Boolean) = if (uppercase) HexFormat.of().withUpperCase() else HexFormat.of()
}
