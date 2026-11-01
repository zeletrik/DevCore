package eu.zeletrik.devcore.tools.certificate

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.DSAParameter
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.OtherName
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Date
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CertificateFixesTest {
	private val bc = BouncyCastleProvider()
	private val now = Instant.parse("2026-06-01T00:00:00Z")

	private fun pem(der: ByteArray) =
		"-----BEGIN CERTIFICATE REQUEST-----\n" + Base64.getMimeEncoder(
			64,
			"\n".toByteArray(),
		).encodeToString(der) + "\n-----END CERTIFICATE REQUEST-----"

	private fun line(text: String, label: String) =
		text.lines().first { it.startsWith("$label: ") }.removePrefix("$label: ")

	private fun request(keys: KeyPair, algorithm: String, sans: GeneralNames? = null): String {
		val builder = JcaPKCS10CertificationRequestBuilder(X500Name("CN=test"), keys.public)
		sans?.let {
			val extensions = ExtensionsGenerator().apply { addExtension(Extension.subjectAlternativeName, false, it) }.generate()
			builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions)
		}
		return pem(builder.build(JcaContentSignerBuilder(algorithm).setProvider(bc).build(keys.private)).encoded)
	}

	private fun ec(curve: String) =
		KeyPairGenerator.getInstance("EC", bc).apply { initialize(ECGenParameterSpec(curve)) }.generateKeyPair()

	private fun dsa(bits: Int) = KeyPairGenerator.getInstance("DSA").apply { initialize(bits) }.generateKeyPair()

	@Test
	fun `IP and otherName SANs are readable`() {
		val upn = OtherName(ASN1ObjectIdentifier("1.3.6.1.4.1.311.20.2.3"), DERUTF8String("user@corp"))
		val sans = GeneralNames(
			arrayOf(
				GeneralName(GeneralName.iPAddress, "192.168.1.10"),
				GeneralName(GeneralName.iPAddress, "2001:db8::1"),
				GeneralName(GeneralName.otherName, upn),
			)
		)
		val decoded = Certificates.decode(request(ec("secp256r1"), "SHA256withECDSA", sans), now)
		assertEquals("IP 192.168.1.10, IP 2001:db8::1, Other (1.3.6.1.4.1.311.20.2.3)", line(decoded, "Requested SANs"))
	}

	@Test
	fun `brainpool and secp256k1 requests are verified and named`() {
		mapOf(
			"brainpoolP256r1" to "EC brainpoolP256r1",
			"secp256k1" to "EC secp256k1",
			"secp256r1" to "EC P-256",
		).forEach { (curve, label) ->
			val decoded = Certificates.decode(request(ec(curve), "SHA256withECDSA"), now)
			assertEquals(label, line(decoded, "Public key"))
			assertEquals("valid", line(decoded, "Signature"), curve)
		}
	}

	@Test
	fun `DSA up to 3,072 bits is verified`() {
		listOf(2048, 3072).forEach { bits ->
			val decoded = Certificates.decode(request(dsa(bits), "SHA256withDSA"), now)
			assertEquals("DSA $bits bits", line(decoded, "Public key"))
			assertEquals("valid", line(decoded, "Signature"))
		}
	}

	/** A request whose SPKI is any DSA key and whose signature is dummy bytes under [signatureAlgorithm]. */
	private fun crafted(spki: SubjectPublicKeyInfo, signatureAlgorithm: ASN1ObjectIdentifier): String {
		val signer = object : ContentSigner {
			private val sink = ByteArrayOutputStream()
			override fun getAlgorithmIdentifier() = AlgorithmIdentifier(signatureAlgorithm)
			override fun getOutputStream() = sink
			override fun getSignature() = DERSequence(arrayOf(ASN1Integer(1), ASN1Integer(1))).encoded
		}
		return pem(PKCS10CertificationRequestBuilder(X500Name("CN=crafted"), spki).build(signer).encoded)
	}

	@Test
	fun `DSA above 3,072 bits is not checked, quickly`() {
		val p = BigInteger.ONE.shiftLeft(4095).setBit(0)
		val spki = SubjectPublicKeyInfo(
			AlgorithmIdentifier(X9ObjectIdentifiers.id_dsa, DSAParameter(p, BigInteger.valueOf(7), BigInteger.TWO)),
			ASN1Integer(3),
		)
		val started = System.nanoTime()
		val decoded = Certificates.decode(crafted(spki, ASN1ObjectIdentifier("2.16.840.1.101.3.4.3.2")), now)
		assertEquals("not checked", line(decoded, "Signature"))
		assertTrue(System.nanoTime() - started < 1_000_000_000)
	}

	@Test
	fun `a signature that can't be verified is not called invalid`() {
		val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
		val decoded = Certificates.decode(
			crafted(SubjectPublicKeyInfo.getInstance(keys.public.encoded), ASN1ObjectIdentifier("1.2.3.4.5")),
			now,
		)
		assertEquals("not checked", line(decoded, "Signature"))
	}

	@Test
	fun `20,000 unmatched BEGIN lines are scanned in under a second`() {
		val text = "-----BEGIN A-----\n".repeat(20_000)
		val started = System.nanoTime()
		runCatching { Certificates.decode(text, now) }
		assertTrue(System.nanoTime() - started < 1_000_000_000)
	}

	@Test
	fun `other SAN types never print a byte array`() {
		val tagged = GeneralName(GeneralName.x400Address, DERSequence())
		val decoded = runCatching {
			Certificates.decode(request(ec("secp256r1"), "SHA256withECDSA", GeneralNames(tagged)), now)
		}.getOrNull()
			.orEmpty()
		assertTrue("[B@" !in decoded, decoded)
	}

	@Test
	fun `certificate IP and otherName SANs are readable`() {
		val keys = ec("secp256r1")
		val upn = OtherName(ASN1ObjectIdentifier("1.3.6.1.4.1.311.20.2.3"), DERUTF8String("user@corp"))
		val sans = GeneralNames(
			arrayOf(
				GeneralName(GeneralName.iPAddress, "192.168.1.10"),
				GeneralName(GeneralName.iPAddress, "2001:db8::1"),
				GeneralName(GeneralName.otherName, upn),
			)
		)
		val builder = JcaX509v3CertificateBuilder(
			X500Name("CN=test"),
			BigInteger.TEN,
			Date.from(now.minusSeconds(60)),
			Date.from(now.plusSeconds(60)),
			X500Name("CN=test"),
			keys.public,
		)
			.addExtension(Extension.subjectAlternativeName, false, sans)
		val der = builder.build(JcaContentSignerBuilder("SHA256withECDSA").setProvider(bc).build(keys.private)).encoded
		val pem = "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(
			64,
			"\n".toByteArray(),
		).encodeToString(der) + "\n-----END CERTIFICATE-----"
		assertEquals(
			"IP 192.168.1.10, IP 2001:db8::1, Other (1.3.6.1.4.1.311.20.2.3)",
			line(Certificates.decode(pem, now), "Subject alternative names"),
		)
	}
}
