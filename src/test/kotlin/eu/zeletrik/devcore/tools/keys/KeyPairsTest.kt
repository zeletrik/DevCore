package eu.zeletrik.devcore.tools.keys

import java.io.DataInputStream
import java.math.BigInteger
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyPairsTest {
	private fun der(pem: String) = Base64.getMimeDecoder().decode(pem.lines().drop(1).dropLast(1).joinToString(""))

	/** The RFC 4251 strings of an OpenSSH key blob, in order. */
	private fun strings(line: String): List<ByteArray> {
		val input = DataInputStream(Base64.getDecoder().decode(line.substringAfter(' ')).inputStream())
		return generateSequence {
			if (input.available() > 0) ByteArray(input.readInt()).also { input.readFully(it) } else null
		}.toList()
	}

	@Test
	fun `Ed25519 by default - PEM blocks, and the OpenSSH line carries the raw key`() {
		val pair = KeyPairs.generate(KeyAlgorithm.ED25519)
		val private = KeyPairs.privatePem(pair)
		val public = KeyPairs.publicPem(pair)
		assertTrue(private.startsWith("-----BEGIN PRIVATE KEY-----\n") && private.endsWith("\n-----END PRIVATE KEY-----"))
		assertTrue(public.startsWith("-----BEGIN PUBLIC KEY-----\n") && public.endsWith("\n-----END PUBLIC KEY-----"))
		val line = KeyPairs.openSsh(pair, "")
		assertTrue(line.startsWith("ssh-ed25519 "))
		val (type, key) = strings(line)
		assertEquals("ssh-ed25519", String(type))
		assertContentEquals(der(public).takeLast(32).toByteArray(), key)
	}

	@Test
	fun `the ssh-rsa line holds the type, exponent and modulus as RFC 4253 strings`() {
		val pair = KeyPairs.generate(KeyAlgorithm.RSA_2048)
		val line = KeyPairs.openSsh(pair, "")
		assertTrue(line.startsWith("ssh-rsa "))
		val (type, exponent, modulus) = strings(line)
		val key = pair.public as RSAPublicKey
		assertEquals("ssh-rsa", String(type))
		assertEquals(key.publicExponent, BigInteger(exponent))
		assertEquals(key.modulus, BigInteger(modulus))
	}

	@Test
	fun `a comment ends the OpenSSH line, a blank one adds nothing`() {
		val pair = KeyPairs.generate(KeyAlgorithm.ED25519)
		assertTrue(KeyPairs.openSsh(pair, "me@host").endsWith(" me@host"))
		assertEquals(KeyPairs.openSsh(pair, ""), KeyPairs.openSsh(pair, "  "))
		assertFalse(KeyPairs.openSsh(pair, "  ").endsWith(" "))
	}
}
