package eu.zeletrik.devcore.tools.keys

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64

enum class KeyAlgorithm(val label: String, val bits: Int?) {
	ED25519("Ed25519", bits = null),
	RSA_2048("RSA 2048", bits = 2048),
	RSA_3072("RSA 3072", bits = 3072),
	RSA_4096("RSA 4096", bits = 4096),
}

/** PEM wraps Base64 at 64 characters (RFC 7468); a raw Ed25519 public key is 32 bytes (RFC 8032). */
private const val PEM_LINE = 64
private const val ED25519_KEY_BYTES = 32

/** JDK key generation; PEM from the encoded forms, the OpenSSH line encoded by hand (RFC 4253, RFC 8709). */
object KeyPairs {
	private val mime = Base64.getMimeEncoder(PEM_LINE, "\n".toByteArray())

	fun generate(algorithm: KeyAlgorithm): KeyPair = when (val bits = algorithm.bits) {
		null -> KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
		else -> KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
	}

	fun privatePem(pair: KeyPair) = pem("PRIVATE KEY", pair.private.encoded)

	fun publicPem(pair: KeyPair) = pem("PUBLIC KEY", pair.public.encoded)

	fun openSsh(pair: KeyPair, comment: String): String {
		val key = pair.public
		val (type, blob) = if (key is RSAPublicKey) {
			"ssh-rsa" to wire { string("ssh-rsa".toByteArray()); mpint(key.publicExponent); mpint(key.modulus) }
		} else {
			// An Ed25519 SubjectPublicKeyInfo ends with the 32-byte raw key.
			"ssh-ed25519" to wire {
				string("ssh-ed25519".toByteArray())
				string(key.encoded.takeLast(ED25519_KEY_BYTES).toByteArray())
			}
		}
		val line = "$type ${Base64.getEncoder().encodeToString(blob)}"
		return if (comment.isBlank()) line else "$line ${comment.trim()}"
	}

	private fun pem(label: String, der: ByteArray) =
		"-----BEGIN $label-----\n${mime.encodeToString(der)}\n-----END $label-----"

	private fun wire(write: DataOutputStream.() -> Unit): ByteArray =
		ByteArrayOutputStream().also { DataOutputStream(it).write() }.toByteArray()

	private fun DataOutputStream.string(bytes: ByteArray) {
		writeInt(bytes.size)
		write(bytes)
	}

	private fun DataOutputStream.mpint(value: BigInteger) = string(value.toByteArray())
}
