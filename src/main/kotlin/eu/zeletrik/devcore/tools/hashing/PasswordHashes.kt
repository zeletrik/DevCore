package eu.zeletrik.devcore.tools.hashing

import eu.zeletrik.devcore.tools.FieldError
import eu.zeletrik.devcore.tools.secureRandom
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.OpenBSDBCrypt
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.util.Base64

enum class HashAlgorithm(val label: String) { BCRYPT("bcrypt"), ARGON2ID("Argon2id") }

/** bcrypt and Argon2 through Bouncy Castle's lightweight API (no JCA provider is registered). */
object PasswordHashes {
	const val MAX_BCRYPT_COST = 14
	const val MAX_ARGON2_MEMORY_KIB = 65_536
	const val MAX_ARGON2_ITERATIONS = 10
	private const val NOT_SUPPORTED = "Not a bcrypt or Argon2 hash"
	private const val TOO_COSTLY = "Cost too high to verify here"
	private val b64 = Base64.getEncoder().withoutPadding()
	private val bcrypt = Regex("""[$](2[aby])[$](\d{2})[$][./A-Za-z0-9]{53}""")
	private val argon2 = Regex("""[$]argon2(id|i|d)[$]v=(\d+)[$]m=(\d+),t=(\d+),p=(\d+)[$]([A-Za-z0-9+/]+)[$]([A-Za-z0-9+/]+)""")

	fun bcrypt(password: String, cost: Int): String {
		val bytes = password.toByteArray(Charsets.UTF_8)
		require(bytes.size <= 72) { "bcrypt uses at most 72 bytes; this password has ${bytes.size}" }
		return OpenBSDBCrypt.generate("2b", bytes, salt(), cost)
	}

	/** PHC string format: `$argon2id$v=19$m=…,t=…,p=1$<salt>$<hash>`, unpadded Base64. */
	fun argon2id(password: String, memoryKib: Int, iterations: Int): String {
		val salt = salt()
		val hash = argon2(Argon2Parameters.ARGON2_id, Argon2Parameters.ARGON2_VERSION_13, memoryKib, iterations, 1, salt, password, 32)
		return "\$argon2id\$v=19\$m=$memoryKib,t=$iterations,p=1\$${b64.encodeToString(salt)}\$${b64.encodeToString(hash)}"
	}

	/** Checks the parameters before computing anything, so an expensive hash can't stall the server. */
	fun verify(password: String, hash: String): Boolean {
		val encoded = hash.trim()
		bcrypt.matchEntire(encoded)?.let { match ->
			val cost = match.groupValues[2].toInt()
			if (cost < 4) throw FieldError("hash", NOT_SUPPORTED)
			if (cost > MAX_BCRYPT_COST) throw FieldError("hash", TOO_COSTLY)
			return OpenBSDBCrypt.checkPassword(encoded, password.toByteArray(Charsets.UTF_8))
		}
		val match = argon2.matchEntire(encoded) ?: throw FieldError("hash", NOT_SUPPORTED)
		val (variant, version, memory, iterations, parallelism, salt, expected) = match.destructured
		val m = memory.toIntOrNull() ?: throw FieldError("hash", TOO_COSTLY)
		val t = iterations.toIntOrNull() ?: throw FieldError("hash", TOO_COSTLY)
		if (m > MAX_ARGON2_MEMORY_KIB || t > MAX_ARGON2_ITERATIONS) throw FieldError("hash", TOO_COSTLY)
		val type = when (variant) {
			"id" -> Argon2Parameters.ARGON2_id
			"i" -> Argon2Parameters.ARGON2_i
			else -> Argon2Parameters.ARGON2_d
		}
		return try {
			val expectedBytes = Base64.getDecoder().decode(expected)
			val actual = argon2(type, version.toInt(), m, t, parallelism.toInt(), Base64.getDecoder().decode(salt), password, expectedBytes.size)
			MessageDigest.isEqual(expectedBytes, actual)
		} catch (_: RuntimeException) {
			throw FieldError("hash", NOT_SUPPORTED)
		}
	}

	private fun argon2(type: Int, version: Int, memoryKib: Int, iterations: Int, parallelism: Int, salt: ByteArray, password: String, length: Int): ByteArray {
		val parameters = Argon2Parameters.Builder(type).withVersion(version).withMemoryAsKB(memoryKib)
			.withIterations(iterations).withParallelism(parallelism).withSalt(salt).build()
		val out = ByteArray(length)
		Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password.toByteArray(Charsets.UTF_8), out)
		return out
	}

	private fun salt() = ByteArray(16).also(secureRandom::nextBytes)
}
