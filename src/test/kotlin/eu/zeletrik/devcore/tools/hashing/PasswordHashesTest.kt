package eu.zeletrik.devcore.tools.hashing

import eu.zeletrik.devcore.tools.FieldError
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.OpenBSDBCrypt
import org.bouncycastle.crypto.params.Argon2Parameters
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHashesTest {
	private val b64 = Base64.getEncoder().withoutPadding()

	/** An Argon2 PHC string made directly with Bouncy Castle, for any variant and parameters. */
	private fun argon2(variant: String, type: Int, password: String, m: Int, t: Int, p: Int): String {
		val salt = ByteArray(16) { it.toByte() }
		val out = ByteArray(32)
		Argon2BytesGenerator().apply {
			init(
				Argon2Parameters.Builder(type).withVersion(19)
					.withMemoryAsKB(m)
					.withIterations(t)
					.withParallelism(p)
					.withSalt(salt)
					.build()
			)
		}.generateBytes(password.toByteArray(), out)
		return "\$argon2$variant\$v=19\$m=$m,t=$t,p=$p\$${b64.encodeToString(salt)}\$${b64.encodeToString(out)}"
	}

	private fun rejects(message: String, hash: String) =
		assertEquals(message, assertFailsWith<FieldError> { PasswordHashes.verify("hunter2", hash) }.message)

	@Test
	fun `bcrypt hashes with the chosen cost and a fresh salt`() {
		val hash = PasswordHashes.bcrypt("hunter2", 10)
		assertEquals(60, hash.length)
		assertTrue(hash.startsWith("\$2b\$10\$"))
		assertTrue(PasswordHashes.verify("hunter2", hash))
		assertNotEquals(hash, PasswordHashes.bcrypt("hunter2", 10))
		mapOf(4 to "\$2b\$04\$", 12 to "\$2b\$12\$", 14 to "\$2b\$14\$").forEach { (cost, prefix) ->
			assertTrue(PasswordHashes.bcrypt("x", cost).startsWith(prefix))
		}
	}

	@Test
	fun `bcrypt counts UTF-8 bytes up to 72`() {
		PasswordHashes.bcrypt("a".repeat(71), 4)
		PasswordHashes.bcrypt("a".repeat(72), 4)
		assertEquals(
			"bcrypt uses at most 72 bytes; this password has 73",
			assertFailsWith<IllegalArgumentException> { PasswordHashes.bcrypt("a".repeat(73), 4) }.message,
		)
		assertEquals(
			"bcrypt uses at most 72 bytes; this password has 74",
			assertFailsWith<IllegalArgumentException> { PasswordHashes.bcrypt("é".repeat(37), 4) }.message,
		)
	}

	@Test
	fun `argon2id in PHC format with a 16-byte salt and a 32-byte hash, no byte limit`() {
		val hash = PasswordHashes.argon2id("hunter2", 19_456, 2)
		val parts = hash.split("$")
		assertEquals(listOf("", "argon2id", "v=19", "m=19456,t=2,p=1"), parts.take(4))
		assertEquals(16, Base64.getDecoder().decode(parts[4]).size)
		assertEquals(32, Base64.getDecoder().decode(parts[5]).size)
		assertTrue(PasswordHashes.verify("hunter2", hash))
		assertFalse(PasswordHashes.verify("hunter3", hash))
		assertTrue("m=32768" in PasswordHashes.argon2id("x", 32_768, 1))
		assertTrue("m=8192" in PasswordHashes.argon2id("x", 8192, 1))
		assertTrue("t=5" in PasswordHashes.argon2id("x", 8192, 5))
		assertTrue("t=10" in PasswordHashes.argon2id("x", 8192, 10))
		PasswordHashes.argon2id("a".repeat(73), 8192, 1)
	}

	@Test
	fun `verify accepts every supported prefix`() {
		listOf("2a", "2b", "2y").forEach { version ->
			val hash = OpenBSDBCrypt.generate(version, "hunter2".toByteArray(), ByteArray(16), 4)
			assertTrue(PasswordHashes.verify("hunter2", hash), version)
			assertFalse(PasswordHashes.verify("hunter3", hash), version)
		}
		mapOf(
			"id" to Argon2Parameters.ARGON2_id,
			"i" to Argon2Parameters.ARGON2_i,
			"d" to Argon2Parameters.ARGON2_d,
		).forEach { (variant, type) ->
			assertTrue(PasswordHashes.verify("hunter2", argon2(variant, type, "hunter2", 8192, 1, 1)), variant)
		}
		assertTrue(PasswordHashes.verify("hunter2", argon2("id", Argon2Parameters.ARGON2_id, "hunter2", 4096, 1, 4)))
	}

	@Test
	fun `verify computes at the caps`() {
		assertTrue(PasswordHashes.verify("x", OpenBSDBCrypt.generate("2b", "x".toByteArray(), ByteArray(16), 14)))
		assertTrue(PasswordHashes.verify("x", argon2("id", Argon2Parameters.ARGON2_id, "x", 65_536, 10, 1)))
	}

	@Test
	fun `verify refuses unsupported hashes and over-cap parameters without computing`() {
		val tooCostly = "Cost too high to verify here"
		val started = System.nanoTime()
		rejects(tooCostly, "\$2b\$15\$" + "a".repeat(53))
		rejects(tooCostly, "\$2b\$31\$" + "a".repeat(53))
		rejects(
			tooCostly,
			"\$argon2id\$v=19\$m=65537,t=1,p=1\$AAAAAAAAAAAAAAAAAAAAAA\$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
		)
		rejects(
			tooCostly,
			"\$argon2id\$v=19\$m=8192,t=11,p=1\$AAAAAAAAAAAAAAAAAAAAAA\$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
		)
		assertTrue(System.nanoTime() - started < 500_000_000L)
		listOf(
			"\$1\$abc\$def",
			"5f4dcc3b5aa765d61d8327deb882cf99",
			"\$2b\$03\$" + "a".repeat(53),
			"\$2b\$10\$short",
			"\$argon2id\$v=19\$m=x",
		).forEach {
			rejects("Not a bcrypt or Argon2 hash", it)
		}
	}
}
