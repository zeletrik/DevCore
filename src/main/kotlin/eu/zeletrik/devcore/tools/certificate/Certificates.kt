package eu.zeletrik.devcore.tools.certificate

import eu.zeletrik.devcore.tools.subnet.Subnets
import eu.zeletrik.devcore.tools.timestamp.relativeTime
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1TaggedObject
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers
import org.bouncycastle.asn1.nist.NISTNamedCurves
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.pkcs.RSAPublicKey
import org.bouncycastle.asn1.x509.DSAParameter
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extensions
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.OtherName
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x9.ECNamedCurveTable
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.DefaultAlgorithmNameFinder
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.PKCSException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import javax.security.auth.x500.X500Principal

private const val SAN_OTHER = 0
private const val SAN_IP = 7
private const val MAX_DSA_BITS = 3_072
private const val HEX = 16
private const val IPV6_BYTES = 16
private const val NOT_CHECKED = "not checked"

/** Offline decoding only: nothing in a certificate (AIA, OCSP, CRL URLs) is ever fetched. */
object Certificates {
	private val marker = Regex("-----(BEGIN|END) ([A-Z0-9 ]{1,64})-----")
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
	private val bouncyCastle = BouncyCastleProvider()

	fun decode(text: String, now: Instant): String {
		// Only certificate and request blocks are decoded; anything else, private keys included, is never read.
		val blocks = blocks(text).filter { (label, _) -> label == "CERTIFICATE" || label in requestLabels }
		require(blocks.isNotEmpty()) { "No PEM certificate or certificate request found" }
		val certificates = blocks.count { it.first == "CERTIFICATE" }
		var index = 0
		return blocks.joinToString("\n\n") { (label, body) ->
			if (label == "CERTIFICATE") {
				index++
				val unreadable = "Certificate $index could not be read"
				val certificate = try {
					val der = ByteArrayInputStream(der(body, unreadable))
					CertificateFactory.getInstance("X.509").generateCertificate(der) as X509Certificate
				} catch (e: CertificateException) {
					throw IllegalArgumentException(unreadable, e)
				}
				"Certificate $index of $certificates\n" + describe(certificate, now)
			} else {
				val unreadable = "The certificate request could not be read"
				val request = try {
					PKCS10CertificationRequest(der(body, unreadable))
				} catch (e: IOException) {
					throw IllegalArgumentException(unreadable, e)
				}
				"Certificate request\n" + describe(request)
			}
		}
	}

	private fun der(body: String, unreadable: String): ByteArray = try {
		Base64.getMimeDecoder().decode(body.trim())
	} catch (e: IllegalArgumentException) {
		throw IllegalArgumentException(unreadable, e)
	}

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
			"Serial: ${c.serialNumber.toString(HEX).uppercase()}",
			"Not before: $from (${relativeTime(from, now)})",
			"Not after: $until (${relativeTime(until, now)})",
			"Status: $status",
			c.subjectAlternativeNames?.let { names ->
				"Subject alternative names: " + names.joinToString(", ") { AlternativeNames.san(it[0] as Int, it[1]) }
			},
			"Public key: ${PublicKeys.key(SubjectPublicKeyInfo.getInstance(c.publicKey.encoded))}",
			"Signature algorithm: ${signature(c.sigAlgName)}",
			c.keyUsage?.let { bits ->
				"Key usage: " + keyUsages.filterIndexed { i, _ -> bits.getOrElse(i) { false } }.joinToString(", ")
			},
			c.extendedKeyUsage?.let { oids -> "Extended key usage: " + oids.joinToString(", ") { extendedKeyUsages[it] ?: it } },
			"Basic constraints: $constraints",
			"SHA-256 fingerprint: ${fingerprint.formatHex(MessageDigest.getInstance("SHA-256").digest(c.encoded))}",
			"SHA-1 fingerprint: ${fingerprint.formatHex(MessageDigest.getInstance("SHA-1").digest(c.encoded))}",
		).joinToString("\n")
	}

	private fun describe(request: PKCS10CertificationRequest): String {
		val sans = request.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest).flatMap { attribute ->
			attribute.attrValues.map { value ->
				GeneralNames.fromExtensions(Extensions.getInstance(value), Extension.subjectAlternativeName)?.names.orEmpty()
					.toList()
			}.flatten()
		}
		val signature = verify(request)
		return listOfNotNull(
			"Subject: ${request.subject}",
			"Public key: ${PublicKeys.key(request.subjectPublicKeyInfo)}",
			"Signature algorithm: ${signature(DefaultAlgorithmNameFinder().getAlgorithmName(request.signatureAlgorithm))}",
			if (sans.isEmpty()) null else "Requested SANs: " + sans.joinToString(", ") { AlternativeNames.san(it) },
			"Signature: $signature",
		).joinToString("\n")
	}

	/**
	 * valid, invalid, or not checked: Bouncy Castle verifies (brainpool and secp256k1 included, which the JDK lacks),
	 * DSA only up to a 3,072-bit prime since its cost grows with the square of the prime's size.
	 */
	private fun verify(request: PKCS10CertificationRequest): String {
		val spki = request.subjectPublicKeyInfo
		val algorithm = spki.algorithm.algorithm
		val supported = when (algorithm) {
			X9ObjectIdentifiers.id_dsa -> (PublicKeys.dsaBits(spki) ?: Int.MAX_VALUE) <= MAX_DSA_BITS
			PKCSObjectIdentifiers.rsaEncryption,
			X9ObjectIdentifiers.id_ecPublicKey,
			EdECObjectIdentifiers.id_Ed25519,
			EdECObjectIdentifiers.id_Ed448 -> true
			else -> false
		}
		if (!supported) return NOT_CHECKED
		return try {
			val verifier = JcaContentVerifierProviderBuilder().setProvider(bouncyCastle).build(spki)
			if (request.isSignatureValid(verifier)) "valid" else "invalid"
		} catch (_: OperatorCreationException) {
			NOT_CHECKED
		} catch (_: PKCSException) {
			NOT_CHECKED
		} catch (_: IllegalArgumentException) {
			NOT_CHECKED
		}
	}

	/** PEM blocks found in one pass over the markers, so unmatched BEGIN lines can't make the scan quadratic. */
	private fun blocks(text: String): List<Pair<String, String>> {
		val markers = marker.findAll(text).toList()
		val ends = markers.filter { it.groupValues[1] == "END" }.groupBy({ it.groupValues[2] }, { it.range.first })
		val nextEnd = mutableMapOf<String, Int>()
		val found = mutableListOf<Pair<String, String>>()
		var after = 0
		for (begin in markers.filter { it.groupValues[1] == "BEGIN" }) {
			val label = begin.groupValues[2]
			val candidates = ends[label].orEmpty()
			var i = nextEnd[label] ?: 0
			while (i < candidates.size && candidates[i] < begin.range.last) i++
			nextEnd[label] = i
			// A BEGIN inside the previous block, or without a matching END after it, starts nothing.
			val end = candidates.getOrNull(i)
			if (end != null && begin.range.first >= after) {
				found += label to text.substring(begin.range.last + 1, end)
				after = end
			}
		}
		return found
	}

	/** `SHA256withRSA` → `SHA-256 with RSA`. */
	private fun signature(name: String) =
		Regex("(?i)SHA(\\d+)WITH(RSA|ECDSA|DSA)(?:ENCRYPTION)?").matchEntire(name)
			?.let { "SHA-${it.groupValues[1]} with ${it.groupValues[2].uppercase()}" } ?: name
}

/** Public key descriptions: algorithm and size, or the curve. */
private object PublicKeys {
	/** From the encoded key itself, so curves the JDK doesn't support (brainpool, secp256k1) are still named. */
	fun key(spki: SubjectPublicKeyInfo): String = when (spki.algorithm.algorithm) {
		PKCSObjectIdentifiers.rsaEncryption ->
			"RSA ${RSAPublicKey.getInstance(spki.parsePublicKey()).modulus.bitLength()} bits"
		X9ObjectIdentifiers.id_ecPublicKey -> "EC " + curveName(spki.algorithm.parameters)
		X9ObjectIdentifiers.id_dsa -> dsaBits(spki)?.let { "DSA $it bits" } ?: "DSA"
		EdECObjectIdentifiers.id_Ed25519 -> "Ed25519"
		EdECObjectIdentifiers.id_Ed448 -> "Ed448"
		else -> DefaultAlgorithmNameFinder().getAlgorithmName(spki.algorithm.algorithm)
	}

	/** The DSA prime's size, or null when the key inherits its parameters from elsewhere. */
	fun dsaBits(spki: SubjectPublicKeyInfo) = DSAParameter.getInstance(spki.algorithm.parameters)?.p?.bitLength()

	/** NIST curves by their NIST names (P-256), the rest by their usual names (brainpoolP256r1, secp256k1). */
	private fun curveName(parameters: ASN1Encodable?): String {
		val oid = parameters as? ASN1ObjectIdentifier ?: return "(explicit parameters)"
		return NISTNamedCurves.getName(oid) ?: ECNamedCurveTable.getName(oid) ?: oid.id
	}
}

/** Subject alternative names, from a certificate (as the JDK reports them) or a request (as Bouncy Castle does). */
private object AlternativeNames {
	private val sanTypes = mapOf(1 to "Email", 2 to "DNS", 4 to "Directory", 6 to "URI", 7 to "IP")
	private val otherTypes = mapOf(3 to "x400Address", 5 to "ediPartyName")

	/** A certificate's SAN as the JDK reports it: strings for most types, DER bytes for otherName and friends. */
	fun san(type: Int, value: Any?): String = when {
		type == SAN_IP && value is String -> "IP " + ipText(InetAddress.ofLiteral(value).address)
		type == SAN_OTHER && value is ByteArray -> "Other (${otherName(value).typeID.id})"
		value is ByteArray -> "Other (${otherTypes[type] ?: "type $type"})"
		else -> "${sanTypes[type] ?: "Other"} $value"
	}

	/** The JDK gives an otherName as its bare SEQUENCE; a tagged GeneralName form is accepted too. */
	private fun otherName(der: ByteArray): OtherName = when (val value = ASN1Primitive.fromByteArray(der)) {
		is ASN1TaggedObject -> OtherName.getInstance(GeneralName.getInstance(value).name)
		else -> OtherName.getInstance(value)
	}

	/** A request's SAN from Bouncy Castle's GeneralName. */
	fun san(name: GeneralName): String = when (name.tagNo) {
		SAN_IP -> "IP " + ipText(ASN1OctetString.getInstance(name.name).octets)
		SAN_OTHER -> "Other (${OtherName.getInstance(name.name).typeID.id})"
		else -> "${sanTypes[name.tagNo] ?: "Other"} ${name.name}"
	}

	/** Never resolved: the raw bytes become text, IPv6 in RFC 5952 form. */
	private fun ipText(bytes: ByteArray): String =
		if (bytes.size == IPV6_BYTES) Subnets.ipv6Text(bytes) else bytes.joinToString(".") { it.toUByte().toString() }
}
