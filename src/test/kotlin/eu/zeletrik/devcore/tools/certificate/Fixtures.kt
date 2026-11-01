package eu.zeletrik.devcore.tools.certificate

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AccessDescription
import org.bouncycastle.asn1.x509.AuthorityInformationAccess
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import java.util.Date

/** Certificates built for the tests; [c] is the server certificate most tests share. */
internal object Fixtures {
	private val rsa: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
	private val ec: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
		.generateKeyPair()

	fun pem(label: String, der: ByteArray) =
		"-----BEGIN $label-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)}\n-----END $label-----"

	private fun certificate(
		subject: String = "CN=example.com",
		keys: KeyPair = rsa,
		signature: String = "SHA256withRSA",
		extra: JcaX509v3CertificateBuilder.() -> Unit = {},
	): ByteArray {
		val builder = JcaX509v3CertificateBuilder(
			X500Name("CN=Test CA"), BigInteger.valueOf(0x1A2B),
			Date.from(Instant.parse("2026-01-01T00:00:00Z")), Date.from(Instant.parse("2027-01-01T00:00:00Z")),
			X500Name(subject), keys.public,
		)
		builder.extra()
		return builder.build(JcaContentSignerBuilder(signature).build(keys.private)).encoded
	}

	val c: String by lazy {
		pem("CERTIFICATE", certificate {
			addExtension(
				Extension.subjectAlternativeName,
				false,
				GeneralNames(
					arrayOf(GeneralName(GeneralName.dNSName, "example.com"), GeneralName(GeneralName.dNSName, "www.example.com"))
				),
			)
			addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment))
			addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
			addExtension(Extension.basicConstraints, true, BasicConstraints(false))
		})
	}

	val cDer: ByteArray get() = Base64.getMimeDecoder().decode(c.substringAfter("-----\n").substringBefore("\n-----END"))

	val ecCertificate: String by lazy { pem("CERTIFICATE", certificate(keys = ec, signature = "SHA256withECDSA")) }

	fun chain(): String =
		listOf("CN=one", "CN=two", "CN=three").joinToString("\n") { pem("CERTIFICATE", certificate(subject = it)) }

	fun withNetworkUrls(port: Int): String = pem("CERTIFICATE", certificate {
		val url = GeneralName(GeneralName.uniformResourceIdentifier, "http://127.0.0.1:$port/x")
		addExtension(
			Extension.authorityInfoAccess,
			false,
			AuthorityInformationAccess(
				arrayOf(
					AccessDescription(AccessDescription.id_ad_ocsp, url),
					AccessDescription(AccessDescription.id_ad_caIssuers, url),
				)
			),
		)
		addExtension(
			Extension.cRLDistributionPoints,
			false,
			CRLDistPoint(arrayOf(DistributionPoint(DistributionPointName(GeneralNames(url)), null, null))),
		)
	})

	fun request(label: String = "CERTIFICATE REQUEST", tamper: Boolean = false): String {
		val extensions = ExtensionsGenerator().apply {
			addExtension(Extension.subjectAlternativeName, false, GeneralNames(GeneralName(GeneralName.dNSName, "example.com")))
		}.generate()
		val der = JcaPKCS10CertificationRequestBuilder(X500Name("CN=example.com"), rsa.public)
			.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions)
			.build(JcaContentSignerBuilder("SHA256withRSA").build(rsa.private)).encoded
		if (tamper) der[der.size - 1] = (der[der.size - 1] + 1).toByte()
		return pem(label, der)
	}

	val privateKey: String by lazy { pem("PRIVATE KEY", rsa.private.encoded) }
}
