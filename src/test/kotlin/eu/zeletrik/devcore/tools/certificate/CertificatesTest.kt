package eu.zeletrik.devcore.tools.certificate

import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CertificatesTest {
	private val now = Instant.parse("2026-10-29T10:00:00Z")

	private fun lines(text: String, at: Instant = now) = Certificates.decode(text, at).lines()

	private fun value(text: String, label: String, at: Instant = now) =
		lines(text, at).first { it.startsWith("$label: ") }.substringAfter(": ")

	private fun error(text: String) = assertFailsWith<IllegalArgumentException> { Certificates.decode(text, now) }.message

	@Test
	fun `reference certificate C`() {
		val c = Fixtures.c
		assertEquals("Certificate 1 of 1", lines(c).first())
		assertEquals("CN=example.com", value(c, "Subject"))
		assertEquals("CN=Test CA", value(c, "Issuer"))
		assertEquals("1A2B", value(c, "Serial"))
		assertTrue(value(c, "Not before").startsWith("2026-01-01T00:00:00Z ("))
		assertTrue(value(c, "Not after").startsWith("2027-01-01T00:00:00Z ("))
		assertEquals("DNS example.com, DNS www.example.com", value(c, "Subject alternative names"))
		assertEquals("RSA 2048 bits", value(c, "Public key"))
		assertEquals("SHA-256 with RSA", value(c, "Signature algorithm"))
		assertEquals("Digital Signature, Key Encipherment", value(c, "Key usage"))
		assertEquals("TLS server authentication", value(c, "Extended key usage"))
		assertEquals("not a CA", value(c, "Basic constraints"))
		val hex = HexFormat.ofDelimiter(":").withUpperCase()
		assertEquals(
			hex.formatHex(MessageDigest.getInstance("SHA-256").digest(Fixtures.cDer)),
			value(c, "SHA-256 fingerprint"),
		)
		assertEquals(hex.formatHex(MessageDigest.getInstance("SHA-1").digest(Fixtures.cDer)), value(c, "SHA-1 fingerprint"))
	}

	@Test
	fun `status follows RFC 5280, both bounds inclusive`() {
		val c = Fixtures.c
		assertEquals("Valid", value(c, "Status"))
		assertEquals("Valid", value(c, "Status", Instant.parse("2026-01-01T00:00:00Z")))
		assertEquals("Valid", value(c, "Status", Instant.parse("2027-01-01T00:00:00Z")))
		assertEquals("Expired", value(c, "Status", Instant.parse("2027-01-01T00:00:01Z")))
		assertEquals("Not yet valid", value(c, "Status", Instant.parse("2025-12-31T23:59:59Z")))
	}

	@Test
	fun `EC keys show their curve`() {
		assertEquals("EC P-256", value(Fixtures.ecCertificate, "Public key"))
	}

	@Test
	fun `chains are numbered, private keys and surrounding text are ignored`() {
		val decoded = Certificates.decode("Here is my chain:\n" + Fixtures.chain() + "\n" + Fixtures.privateKey, now)
		assertEquals(
			listOf("Certificate 1 of 3", "Certificate 2 of 3", "Certificate 3 of 3"),
			decoded.lines().filter { it.startsWith("Certificate ") },
		)
		assertEquals(
			listOf("CN=one", "CN=two", "CN=three"),
			decoded.lines().filter { it.startsWith("Subject: ") }.map { it.substringAfter(": ") },
		)
		assertFalse("PRIVATE" in decoded)
		val brokenKey = Fixtures.c + "\n-----BEGIN PRIVATE KEY-----\n!!! not base64 !!!\n-----END PRIVATE KEY-----"
		assertEquals("Certificate 1 of 1", lines(brokenKey).first())
	}

	@Test
	fun `certificate requests, both labels, and their signature`() {
		val request = Certificates.decode(Fixtures.request(), now)
		assertEquals("Certificate request", request.lines().first())
		assertTrue("Subject: CN=example.com" in request && "Public key: RSA 2048 bits" in request)
		assertTrue("Signature algorithm: SHA-256 with RSA" in request && "Requested SANs: DNS example.com" in request)
		assertTrue("Signature: valid" in request)
		assertTrue("Signature: valid" in Certificates.decode(Fixtures.request("NEW CERTIFICATE REQUEST"), now))
		assertTrue("Signature: invalid" in Certificates.decode(Fixtures.request(tamper = true), now))
	}

	@Test
	fun errors() {
		assertEquals("No PEM certificate or certificate request found", error("hello"))
		assertEquals("No PEM certificate or certificate request found", error(Fixtures.privateKey))
		val broken = Fixtures.c + "\n-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----"
		assertEquals("Certificate 2 could not be read", error(broken))
		assertEquals(
			"The certificate request could not be read",
			error("-----BEGIN CERTIFICATE REQUEST-----\nAAAA\n-----END CERTIFICATE REQUEST-----"),
		)
	}

	@Test
	fun `AIA, OCSP and CRL URLs are never fetched`() {
		ServerSocket(0).use { server ->
			server.soTimeout = 300
			assertEquals("Valid", value(Fixtures.withNetworkUrls(server.localPort), "Status"))
			assertFailsWith<SocketTimeoutException> { server.accept() }
		}
	}
}
