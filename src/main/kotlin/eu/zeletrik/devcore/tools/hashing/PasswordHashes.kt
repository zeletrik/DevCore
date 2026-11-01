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
	const val MIN_BCRYPT_COST = 4
	const val MAX_BCRYPT_COST = 14
	val bcryptCosts = MIN_BCRYPT_COST..MAX_BCRYPT_COST
	const val MAX_ARGON2_MEMORY_KIB = 65_536
	const val MAX_ARGON2_ITERATIONS = 10
	private const val NOT_SUPPORTED = "Not a bcrypt or Argon2 hash"
	private const val TOO_COSTLY = "Cost too high to verify here"
	private const val UNSUPPORTED_PARAMETERS = "Not a supported hash"
	private const val MAX_ARGON2_PARALLELISM = 16

	/** bcrypt reads at most 72 password bytes; Argon2 needs at least 8 KiB of memory per lane. */
	private const val BCRYPT_MAX_BYTES = 72
	private const val ARGON2_MIN_KIB_PER_LANE = 8
	private const val SALT_BYTES = 16
	private val b64 = Base64.getEncoder().withoutPadding()
	private val bcrypt = Regex("""[$](2[aby])[$](\d{2})[$][./A-Za-z0-9]{53}""")
	private val variants = mapOf(
		"id" to Argon2Parameters.ARGON2_id, "i" to Argon2Parameters.ARGON2_i, "d" to Argon2Parameters.ARGON2_d,
	)
	private val argon2 = Regex(
		"""[$]argon2(?<variant>id|i|d)[$]v=(?<version>\d+)""" +
			"""[$]m=(?<memory>\d+),t=(?<iterations>\d+),p=(?<parallelism>\d+)""" +
			"""[$](?<salt>[A-Za-z0-9+/]+)[$](?<hash>[A-Za-z0-9+/]+)""",
	)

	fun bcrypt(password: String, cost: Int): String {
		val bytes = password.toByteArray(Charsets.UTF_8)
		require(bytes.size <= BCRYPT_MAX_BYTES) { "bcrypt uses at most 72 bytes; this password has ${bytes.size}" }
		return OpenBSDBCrypt.generate("2b", bytes, salt(), cost)
	}

	/** PHC string format: `$argon2id$v=19$m=…,t=…,p=1$<salt>$<hash>`, unpadded Base64. */
	fun argon2id(password: String, memoryKib: Int, iterations: Int): String {
		val salt = salt()
		val hash = argon2(
			Argon2Parameters.ARGON2_id,
			Argon2Parameters.ARGON2_VERSION_13,
			memoryKib,
			iterations,
			1,
			salt,
			password,
			32,
		)
		return "\$argon2id\$v=19\$m=$memoryKib,t=$iterations,p=1\$${b64.encodeToString(salt)}\$${b64.encodeToString(hash)}"
	}

	/** Checks the parameters before computing anything, so an expensive hash can't stall the server. */
	fun verify(password: String, hash: String): Boolean {
		val encoded = hash.trim()
		val bcryptMatch = bcrypt.matchEntire(encoded)
		val argon2Match = argon2.matchEntire(encoded)
		return when {
			bcryptMatch != null -> verifyBcrypt(password, encoded, bcryptMatch.groupValues[2].toInt())
			argon2Match != null -> verifyArgon2(password, argon2Match)
			else -> reject(NOT_SUPPORTED)
		}
	}

	private fun verifyBcrypt(password: String, encoded: String, cost: Int): Boolean {
		if (cost < MIN_BCRYPT_COST) reject(NOT_SUPPORTED)
		if (cost > MAX_BCRYPT_COST) reject(TOO_COSTLY)
		return OpenBSDBCrypt.checkPassword(encoded, password.toByteArray(Charsets.UTF_8))
	}

	private fun verifyArgon2(password: String, match: MatchResult): Boolean {
		fun part(name: String) = match.groups[name]!!.value
		val m = part("memory").toIntOrNull() ?: reject(TOO_COSTLY)
		val t = part("iterations").toIntOrNull() ?: reject(TOO_COSTLY)
		// Bouncy Castle allocates max(m, 8·p) KiB, so parallelism needs its own cap before anything runs.
		val p = part("parallelism").toIntOrNull() ?: reject(TOO_COSTLY)
		if (m > MAX_ARGON2_MEMORY_KIB || t > MAX_ARGON2_ITERATIONS || p > MAX_ARGON2_PARALLELISM) reject(TOO_COSTLY)
		if (p < 1 || m < ARGON2_MIN_KIB_PER_LANE * p) reject(UNSUPPORTED_PARAMETERS)
		return try {
			val expectedBytes = Base64.getDecoder().decode(part("hash"))
			val salt = Base64.getDecoder().decode(part("salt"))
			val type = variants.getValue(part("variant"))
			val actual = argon2(type, part("version").toInt(), m, t, p, salt, password, expectedBytes.size)
			MessageDigest.isEqual(expectedBytes, actual)
		} catch (_: RuntimeException) {
			reject(NOT_SUPPORTED)
		}
	}

	/** Every problem with a hash to verify is reported on the hash field. */
	private fun reject(message: String): Nothing = throw FieldError("hash", message)

	private fun argon2(
		type: Int,
		version: Int,
		memoryKib: Int,
		iterations: Int,
		parallelism: Int,
		salt: ByteArray,
		password: String,
		length: Int,
	): ByteArray {
		val parameters = Argon2Parameters.Builder(type).withVersion(version).withMemoryAsKB(memoryKib)
			.withIterations(iterations).withParallelism(parallelism).withSalt(salt).build()
		val out = ByteArray(length)
		Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password.toByteArray(Charsets.UTF_8), out)
		return out
	}

	private fun salt() = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
}
