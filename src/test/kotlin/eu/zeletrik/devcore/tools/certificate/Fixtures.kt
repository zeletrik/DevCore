package eu.zeletrik.devcore.tools.certificate

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
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
import java.util.Base64
import java.util.Date

/** Certificates and requests built for the tests; [c] is the server certificate most tests share. */
internal object Fixtures {
	private val bc = BouncyCastleProvider()
	val rsa: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
	private val ec: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
		.generateKeyPair()

	fun pem(label: String, der: ByteArray) =
		"-----BEGIN $label-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)}\n-----END $label-----"

	/** A certificate issued by "CN=Test CA" for all of 2026, with only the extensions given. */
	fun certificate(
		subject: String = "CN=example.com",
		keys: KeyPair = rsa,
		signature: String = "SHA256withRSA",
		sans: GeneralNames? = null,
		keyUsage: KeyUsage? = null,
		extendedKeyUsage: ExtendedKeyUsage? = null,
		basicConstraints: BasicConstraints? = null,
	): String {
		val builder = JcaX509v3CertificateBuilder(
			X500Name("CN=Test CA"), BigInteger.valueOf(0x1A2B),
			Date.from(Instant.parse("2026-01-01T00:00:00Z")), Date.from(Instant.parse("2027-01-01T00:00:00Z")),
			X500Name(subject), keys.public,
		)
		sans?.let { builder.addExtension(Extension.subjectAlternativeName, false, it) }
		keyUsage?.let { builder.addExtension(Extension.keyUsage, true, it) }
		extendedKeyUsage?.let { builder.addExtension(Extension.extendedKeyUsage, false, it) }
		basicConstraints?.let { builder.addExtension(Extension.basicConstraints, true, it) }
		return pem("CERTIFICATE", builder.build(JcaContentSignerBuilder(signature).build(keys.private)).encoded)
	}

	val c: String by lazy {
		certificate(
			sans = GeneralNames(
				arrayOf(GeneralName(GeneralName.dNSName, "example.com"), GeneralName(GeneralName.dNSName, "www.example.com"))
			),
			keyUsage = KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
			extendedKeyUsage = ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth),
			basicConstraints = BasicConstraints(false),
		)
	}

	val cDer: ByteArray get() = Base64.getMimeDecoder().decode(c.substringAfter("-----\n").substringBefore("\n-----END"))

	val ecCertificate: String by lazy { certificate(keys = ec, signature = "SHA256withECDSA") }

	fun chain(): String = listOf("CN=one", "CN=two", "CN=three").joinToString("\n") { certificate(subject = it) }

	/** A PKCS#10 request signed through Bouncy Castle, so curves the JDK lacks sign too. */
	fun request(
		keys: KeyPair = rsa,
		algorithm: String = "SHA256withRSA",
		sans: GeneralNames? = GeneralNames(GeneralName(GeneralName.dNSName, "example.com")),
		label: String = "CERTIFICATE REQUEST",
		tamper: Boolean = false,
	): String {
		val builder = JcaPKCS10CertificationRequestBuilder(X500Name("CN=example.com"), keys.public)
		sans?.let {
			val extensions = ExtensionsGenerator().apply { addExtension(Extension.subjectAlternativeName, false, it) }.generate()
			builder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions)
		}
		val der = builder.build(JcaContentSignerBuilder(algorithm).setProvider(bc).build(keys.private)).encoded
		if (tamper) der[der.size - 1] = (der[der.size - 1] + 1).toByte()
		return pem(label, der)
	}

	/** A request whose public key is [spki] and whose signature is dummy bytes under [signatureAlgorithm]. */
	fun crafted(spki: SubjectPublicKeyInfo, signatureAlgorithm: ASN1ObjectIdentifier): String {
		val signer = object : ContentSigner {
			private val sink = ByteArrayOutputStream()
			override fun getAlgorithmIdentifier() = AlgorithmIdentifier(signatureAlgorithm)
			override fun getOutputStream() = sink
			override fun getSignature() = DERSequence(arrayOf(ASN1Integer(1), ASN1Integer(1))).encoded
		}
		val der = PKCS10CertificationRequestBuilder(X500Name("CN=crafted"), spki).build(signer).encoded
		return pem("CERTIFICATE REQUEST", der)
	}

	fun ec(curve: String): KeyPair =
		KeyPairGenerator.getInstance("EC", bc).apply { initialize(ECGenParameterSpec(curve)) }.generateKeyPair()

	fun dsa(bits: Int): KeyPair = KeyPairGenerator.getInstance("DSA").apply { initialize(bits) }.generateKeyPair()

	fun ed25519(): KeyPair = KeyPairGenerator.getInstance("Ed25519", bc).generateKeyPair()

	val privateKey: String by lazy { pem("PRIVATE KEY", rsa.private.encoded) }
}
