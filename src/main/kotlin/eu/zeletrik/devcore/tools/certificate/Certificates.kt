package eu.zeletrik.devcore.tools.certificate

import eu.zeletrik.devcore.tools.timestamp.relativeTime
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extensions
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.operator.DefaultAlgorithmNameFinder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import javax.security.auth.x500.X500Principal

/** Offline decoding only: nothing in a certificate (AIA, OCSP, CRL URLs) is ever fetched. */
object Certificates {
	private val block = Regex("-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----", RegexOption.DOT_MATCHES_ALL)
	private val requestLabels = setOf("CERTIFICATE REQUEST", "NEW CERTIFICATE REQUEST")
	private val fingerprint = HexFormat.ofDelimiter(":").withUpperCase()
	private val keyUsages = listOf(
		"Digital Signature", "Non-Repudiation", "Key Encipherment", "Data Encipherment", "Key Agreement",
		"Certificate Sign", "CRL Sign", "Encipher Only", "Decipher Only",
	)
	private val extendedKeyUsages = mapOf(
		"1.3.6.1.5.5.7.3.1" to "TLS server authentication", "1.3.6.1.5.5.7.3.2" to "TLS client authentication",
		"1.3.6.1.5.5.7.3.3" to "Code signing", "1.3.6.1.5.5.7.3.4" to "Email protection",
		"1.3.6.1.5.5.7.3.8" to "Time stamping", "1.3.6.1.5.5.7.3.9" to "OCSP signing",
	)
	private val sanTypes = mapOf(1 to "Email", 2 to "DNS", 4 to "Directory", 6 to "URI", 7 to "IP")

	fun decode(text: String, now: Instant): String {
		// Only certificate and request blocks are decoded; anything else, private keys included, is never read.
		val blocks = block.findAll(text).map { it.groupValues[1] to it.groupValues[2] }
			.filter { (label, _) -> label == "CERTIFICATE" || label in requestLabels }.toList()
		require(blocks.isNotEmpty()) { "No PEM certificate or certificate request found" }
		val certificates = blocks.count { it.first == "CERTIFICATE" }
		var index = 0
		return blocks.joinToString("\n\n") { (label, body) ->
			if (label == "CERTIFICATE") {
				index++
				val certificate = try {
					CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der(body))) as X509Certificate
				} catch (e: Exception) {
					throw IllegalArgumentException("Certificate $index could not be read")
				}
				"Certificate $index of $certificates\n" + describe(certificate, now)
			} else {
				val request = try {
					PKCS10CertificationRequest(der(body))
				} catch (e: Exception) {
					throw IllegalArgumentException("The certificate request could not be read")
				}
				"Certificate request\n" + describe(request)
			}
		}
	}

	private fun der(body: String) = Base64.getMimeDecoder().decode(body.trim())

	private fun describe(c: X509Certificate, now: Instant): String {
		val from = c.notBefore.toInstant()
		val until = c.notAfter.toInstant()
		val status = when {
			now < from -> "Not yet valid"
			now > until -> "Expired"
			else -> "Valid"
		}
		val constraints = when (val path = c.basicConstraints) {
			-1 -> "not a CA"
			Int.MAX_VALUE -> "CA, no path length limit"
			else -> "CA, path length $path"
		}
		return listOfNotNull(
			"Subject: ${c.subjectX500Principal.getName(X500Principal.RFC2253)}",
			"Issuer: ${c.issuerX500Principal.getName(X500Principal.RFC2253)}",
			"Serial: ${c.serialNumber.toString(16).uppercase()}",
			"Not before: $from (${relativeTime(from, now)})",
			"Not after: $until (${relativeTime(until, now)})",
			"Status: $status",
			c.subjectAlternativeNames?.let { names -> "Subject alternative names: " + names.joinToString(", ") { "${sanTypes[it[0] as Int] ?: "Other"} ${it[1]}" } },
			"Public key: ${key(c.publicKey)}",
			"Signature algorithm: ${signature(c.sigAlgName)}",
			c.keyUsage?.let { bits -> "Key usage: " + keyUsages.filterIndexed { i, _ -> bits.getOrElse(i) { false } }.joinToString(", ") },
			c.extendedKeyUsage?.let { oids -> "Extended key usage: " + oids.joinToString(", ") { extendedKeyUsages[it] ?: it } },
			"Basic constraints: $constraints",
			"SHA-256 fingerprint: ${fingerprint.formatHex(MessageDigest.getInstance("SHA-256").digest(c.encoded))}",
			"SHA-1 fingerprint: ${fingerprint.formatHex(MessageDigest.getInstance("SHA-1").digest(c.encoded))}",
		).joinToString("\n")
	}

	private fun describe(request: PKCS10CertificationRequest): String {
		val sans = request.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest).flatMap { attribute ->
			attribute.attrValues.map { value ->
				GeneralNames.fromExtensions(Extensions.getInstance(value), Extension.subjectAlternativeName)?.names.orEmpty().toList()
			}.flatten()
		}
		val valid = runCatching { request.isSignatureValid(JcaContentVerifierProviderBuilder().build(request.subjectPublicKeyInfo)) }.getOrDefault(false)
		return listOfNotNull(
			"Subject: ${request.subject}",
			"Public key: ${key(JcaPKCS10CertificationRequest(request).publicKey)}",
			"Signature algorithm: ${signature(DefaultAlgorithmNameFinder().getAlgorithmName(request.signatureAlgorithm))}",
			if (sans.isEmpty()) null else "Requested SANs: " + sans.joinToString(", ") { "${sanTypes[it.tagNo] ?: "Other"} ${it.name}" },
			"Signature: ${if (valid) "valid" else "invalid"}",
		).joinToString("\n")
	}

	private fun key(key: PublicKey) = when (key) {
		is RSAPublicKey -> "RSA ${key.modulus.bitLength()} bits"
		is ECPublicKey -> "EC P-${key.params.curve.field.fieldSize}"
		else -> key.algorithm
	}

	/** `SHA256withRSA` → `SHA-256 with RSA`. */
	private fun signature(name: String) =
		Regex("(?i)SHA(\\d+)WITH(RSA|ECDSA|DSA)(?:ENCRYPTION)?").matchEntire(name)
			?.let { "SHA-${it.groupValues[1]} with ${it.groupValues[2].uppercase()}" } ?: name

}
