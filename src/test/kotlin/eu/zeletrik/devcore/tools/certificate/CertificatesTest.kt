package eu.zeletrik.devcore.tools.certificate

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.DSAParameter
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.OtherName
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import java.math.BigInteger
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
	fun `CA certificates`() {
		val unlimited = Fixtures.certificate(basicConstraints = BasicConstraints(true))
		val zero = Fixtures.certificate(basicConstraints = BasicConstraints(0))
		assertEquals("CA, no path length limit", value(unlimited, "Basic constraints"))
		assertEquals("CA, path length 0", value(zero, "Basic constraints"))
	}

	@Test
	fun `unknown extended key usage OIDs are shown as written`() {
		val usage = ExtendedKeyUsage(KeyPurposeId.getInstance(ASN1ObjectIdentifier("1.2.3.4")))
		assertEquals("1.2.3.4", value(Fixtures.certificate(extendedKeyUsage = usage), "Extended key usage"))
	}

	@Test
	fun `IP and otherName SANs are readable in certificates and requests`() {
		val upn = OtherName(ASN1ObjectIdentifier("1.3.6.1.4.1.311.20.2.3"), DERUTF8String("user@corp"))
		val sans = GeneralNames(
			arrayOf(
				GeneralName(GeneralName.iPAddress, "192.168.1.10"),
				GeneralName(GeneralName.iPAddress, "2001:db8::1"),
				GeneralName(GeneralName.otherName, upn),
			)
		)
		val expected = "IP 192.168.1.10, IP 2001:db8::1, Other (1.3.6.1.4.1.311.20.2.3)"
		assertEquals(expected, value(Fixtures.certificate(sans = sans), "Subject alternative names"))
		assertEquals(expected, value(Fixtures.request(sans = sans), "Requested SANs"))
	}

	@Test
	fun `other SAN types are named`() {
		val x400 = GeneralNames(GeneralName(GeneralName.x400Address, DERSequence()))
		assertEquals("Other (x400Address)", value(Fixtures.certificate(sans = x400), "Subject alternative names"))
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
		assertTrue("Signature: valid" in Certificates.decode(Fixtures.request(label = "NEW CERTIFICATE REQUEST"), now))
		assertTrue("Signature: invalid" in Certificates.decode(Fixtures.request(tamper = true), now))
	}

	@Test
	fun `brainpool and secp256k1 requests are verified and named`() {
		listOf("brainpoolP256r1", "secp256k1").forEach { curve ->
			val request = Fixtures.request(Fixtures.ec(curve), "SHA256withECDSA")
			assertEquals("EC $curve", value(request, "Public key"))
			assertEquals("valid", value(request, "Signature"), curve)
		}
	}

	@Test
	fun `Ed25519 requests are named and verified`() {
		val request = Fixtures.request(Fixtures.ed25519(), "Ed25519")
		assertEquals("Ed25519", value(request, "Public key"))
		assertEquals("valid", value(request, "Signature"))
	}

	@Test
	fun `DSA up to 3,072 bits is verified`() {
		listOf(2048, 3072).forEach { bits ->
			val request = Fixtures.request(Fixtures.dsa(bits), "SHA256withDSA")
			assertEquals("DSA $bits bits", value(request, "Public key"))
			assertEquals("valid", value(request, "Signature"))
		}
	}

	@Test
	fun `DSA above 3,072 bits is not checked, quickly`() {
		val p = BigInteger.ONE.shiftLeft(4095).setBit(0)
		val spki = SubjectPublicKeyInfo(
			AlgorithmIdentifier(X9ObjectIdentifiers.id_dsa, DSAParameter(p, BigInteger.valueOf(7), BigInteger.TWO)),
			ASN1Integer(3),
		)
		val request = Fixtures.crafted(spki, ASN1ObjectIdentifier("2.16.840.1.101.3.4.3.2"))
		val started = System.nanoTime()
		assertEquals("not checked", value(request, "Signature"))
		assertTrue(System.nanoTime() - started < 1_000_000_000)
	}

	@Test
	fun `a signature that can't be verified is not called invalid`() {
		val spki = SubjectPublicKeyInfo.getInstance(Fixtures.rsa.public.encoded)
		val request = Fixtures.crafted(spki, ASN1ObjectIdentifier("1.2.3.4.5"))
		assertEquals("not checked", value(request, "Signature"))
	}

	@Test
	fun `20,000 unmatched BEGIN lines are scanned in under a second`() {
		val text = "-----BEGIN A-----\n".repeat(20_000)
		val started = System.nanoTime()
		assertEquals("No PEM certificate or certificate request found", error(text))
		assertTrue(System.nanoTime() - started < 1_000_000_000)
	}

	@Test
	fun `an END with another label does not close a block`() {
		val text = "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE REQUEST-----"
		assertEquals("No PEM certificate or certificate request found", error(text))
	}

	@Test
	fun errors() {
		assertEquals("No PEM certificate or certificate request found", error("hello"))
		assertEquals("No PEM certificate or certificate request found", error(Fixtures.privateKey))
		val broken = Fixtures.c + "\n-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----"
		assertEquals("Certificate 2 could not be read", error(broken))
		val notBase64 = "-----BEGIN CERTIFICATE-----\nA\n-----END CERTIFICATE-----"
		assertEquals("Certificate 1 could not be read", error(notBase64))
		assertEquals(
			"The certificate request could not be read",
			error("-----BEGIN CERTIFICATE REQUEST-----\nAAAA\n-----END CERTIFICATE REQUEST-----"),
		)
	}
}
