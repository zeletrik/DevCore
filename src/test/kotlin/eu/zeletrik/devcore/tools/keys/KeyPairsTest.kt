package eu.zeletrik.devcore.tools.keys

import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyPairsTest {
	private fun der(pem: String) = Base64.getMimeDecoder().decode(pem.lines().drop(1).dropLast(1).joinToString(""))

	@Test
	fun `Ed25519 by default - PEM blocks, matching OpenSSH line, keys that belong together`() {
		val pair = KeyPairs.generate(KeyAlgorithm.ED25519)
		val private = KeyPairs.privatePem(pair)
		val public = KeyPairs.publicPem(pair)
		assertTrue(private.startsWith("-----BEGIN PRIVATE KEY-----\n") && private.endsWith("\n-----END PRIVATE KEY-----"))
		assertTrue(public.startsWith("-----BEGIN PUBLIC KEY-----\n"))
		val line = KeyPairs.openSsh(pair, "")
		assertTrue(line.startsWith("ssh-ed25519 "))
		val blob = Base64.getDecoder().decode(line.substringAfter(' '))
		assertContentEquals(der(public).takeLast(32).toByteArray(), blob.takeLast(32).toByteArray())
		val signed = Signature.getInstance("Ed25519").apply { initSign(pair.private); update("x".toByteArray()) }.sign()
		val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(der(public)))
		assertTrue(Signature.getInstance("Ed25519").apply { initVerify(key); update("x".toByteArray()) }.verify(signed))
	}

	@Test
	fun `RSA sizes and the ssh-rsa line`() {
		KeyAlgorithm.entries.filter { it.bits != null }.forEach { algorithm ->
			val pair = KeyPairs.generate(algorithm)
			assertEquals(algorithm.bits, (pair.public as RSAPublicKey).modulus.bitLength())
			assertTrue(KeyPairs.openSsh(pair, "").startsWith("ssh-rsa "))
		}
		assertTrue(KeyAlgorithm.entries.all { (it.bits ?: 0) <= 4096 })
	}

	@Test
	fun `comment ends the OpenSSH line`() {
		assertTrue(KeyPairs.openSsh(KeyPairs.generate(KeyAlgorithm.ED25519), "me@host").endsWith(" me@host"))
	}
}
