package eu.zeletrik.devcore.tools.subnet

import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address

private const val IPV4_BITS = Int.SIZE_BITS
private const val IPV4_OCTETS = 4
private const val IPV4_MAX = 0xFFFFFFFFL
private const val OCTET_MAX = 0xFF
private const val IPV6_BITS = 128
private const val IPV6_GROUPS = 8
private const val GROUP_MAX = 0xFFFF

/** The 16 bits above an IPv4 address that make it IPv4-mapped IPv6 (`::ffff:a.b.c.d`). */
private const val IPV4_MAPPED = 0xFFFFL

private const val NOT_AN_ADDRESS = "Not an IPv4 or IPv6 address"

/** IPv4 and IPv6 ranges from literals only: a host name is never looked up (that would be an outbound call). */
object Subnets {
	/**
	 * `a.b.c.d`, `a.b.c.d/n`, `a.b.c.d m.m.m.m` or an IPv6 literal with an optional `/n`. A `%zone` index names an
	 * interface, so it is refused.
	 */
	fun describe(text: String): String {
		val input = text.trim()
		val parts = input.split(Regex("\\s+"))
		return when {
			"/" in input -> {
				val (address, prefix) = input.split("/", limit = 2)
				val bits = prefix.toIntOrNull()
				if (":" in address) Ipv6.describe(
					address,
					bits ?: throw IllegalArgumentException("Prefix must be 0–128")
				)
				else Ipv4.describe(Ipv4.parse(address), bits ?: throw IllegalArgumentException("Prefix must be 0–32"))
			}

			parts.size == 2 -> Ipv4.describe(Ipv4.parse(parts[0]), Ipv4.prefixOf(Ipv4.parse(parts[1])))
			":" in input -> Ipv6.describe(input, IPV6_BITS)
			else -> Ipv4.describe(Ipv4.parse(input), IPV4_BITS)
		}
	}

	/** An IPv6 address's 16 bytes in RFC 5952 form (shared with the Certificate Decoder's SAN lines). */
	fun ipv6Text(address: ByteArray): String = Ipv6.compressed(BigInteger(1, address))
}

/** IPv4 as a Long: parsing, netmasks and the description lines. */
private object Ipv4 {
	private val dottedQuad = Regex("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})")

	fun parse(text: String): Long {
		val octets = dottedQuad.matchEntire(text)?.groupValues
			?.drop(1)
			?.map(String::toInt) ?: throw IllegalArgumentException(NOT_AN_ADDRESS)
		require(octets.all { it in 0..OCTET_MAX }) { NOT_AN_ADDRESS }
		return octets.fold(0L) { acc, octet -> (acc shl Byte.SIZE_BITS) or octet.toLong() }
	}

	fun prefixOf(netmask: Long): Int {
		val bits = netmask.countOneBits()
		require(netmask == maskOf(bits)) { "Netmask must be contiguous" }
		return bits
	}

	private fun maskOf(prefix: Int) = if (prefix == 0) 0L else (IPV4_MAX shl (IPV4_BITS - prefix)) and IPV4_MAX

	fun describe(address: Long, prefix: Int): String {
		require(prefix in 0..IPV4_BITS) { "Prefix must be 0–32" }
		val mask = maskOf(prefix)
		val network = address and mask
		val broadcast = network or (mask.inv() and IPV4_MAX)
		val total = 1L shl (IPV4_BITS - prefix)
		// A /32 is one host; a /31 is a point-to-point link whose two addresses are both hosts (RFC 3021).
		val pointToPoint = prefix == IPV4_BITS - 1
		val (first, last, usable) = when {
			prefix == IPV4_BITS -> Triple(address, address, 1L)
			pointToPoint -> Triple(network, broadcast, 2L)
			else -> Triple(network + 1, broadcast - 1, total - 2)
		}
		return listOf(
			"Address: ${dotted(address)}",
			"Network: ${dotted(network)}/$prefix",
			"Netmask: ${dotted(mask)}",
			"Wildcard: ${dotted(mask.inv() and IPV4_MAX)}",
			"Broadcast: ${if (prefix == IPV4_BITS || pointToPoint) "none" else dotted(broadcast)}",
			"First host: ${dotted(first)}",
			"Last host: ${dotted(last)}",
			"Usable hosts: $usable",
			"Total addresses: $total",
			"Type: ${type(address)}",
			"Integer: $address",
			"Hex: 0x%08X".format(address),
			"Binary: " + octets(address).joinToString(".") { it.toString(2).padStart(Byte.SIZE_BITS, '0') },
		).joinToString("\n")
	}

	private fun octets(value: Long) =
		(IPV4_OCTETS - 1 downTo 0).map { (value shr (Byte.SIZE_BITS * it)).toInt() and OCTET_MAX }

	fun dotted(value: Long) = octets(value).joinToString(".")

	private fun type(a: Long): String {
		fun within(vararg cidrs: String) = cidrs.any { cidr ->
			val (base, bits) = cidr.split("/")
			(a xor parse(base)) shr (IPV4_BITS - bits.toInt()) == 0L
		}
		return when {
			a == 0L -> "Unspecified"
			a == IPV4_MAX -> "Limited broadcast"
			within("192.0.2.0/24", "198.51.100.0/24", "203.0.113.0/24") -> "Documentation"
			within("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16") -> "Private"
			within("127.0.0.0/8") -> "Loopback"
			within("169.254.0.0/16") -> "Link-local"
			within("100.64.0.0/10") -> "Shared address space"
			within("224.0.0.0/4") -> "Multicast"
			within("240.0.0.0/4", "0.0.0.0/8") -> "Reserved"
			else -> "Public"
		}
	}
}

/** IPv6 as a BigInteger: the description lines and RFC 5952 text. */
private object Ipv6 {
	fun describe(text: String, prefix: Int): String {
		require(prefix in 0..IPV6_BITS) { "Prefix must be 0–128" }
		require('%' !in text) { NOT_AN_ADDRESS }
		val literal = runCatching { Inet6Address.ofLiteral(text) }.getOrNull()
			?: throw IllegalArgumentException(NOT_AN_ADDRESS)
		val address = BigInteger(1, literal.address).let {
			// ofLiteral turns ::ffff:a.b.c.d into an IPv4 address; keep it IPv6.
			if (literal is Inet4Address) BigInteger.valueOf(IPV4_MAPPED).shiftLeft(IPV4_BITS).or(it) else it
		}
		val hostBits = BigInteger.ONE.shiftLeft(IPV6_BITS - prefix) - BigInteger.ONE
		val network = address.andNot(hostBits)
		val last = network.or(hostBits)
		return listOf(
			"Address: ${compressed(address)}",
			"Expanded: ${groups(address).joinToString(":") { "%04x".format(it) }}",
			"Network: ${compressed(network)}/$prefix",
			"Prefix length: $prefix",
			"First address: ${compressed(network)}",
			"Last address: ${compressed(last)}",
			"Total addresses: ${hostBits + BigInteger.ONE}",
			"Type: ${type(address)}",
		).joinToString("\n")
	}

	private fun groups(address: BigInteger) =
		(IPV6_GROUPS - 1 downTo 0).map { address.shiftRight(Short.SIZE_BITS * it).toInt() and GROUP_MAX }

	private fun mapped(address: BigInteger) = address.shiftRight(IPV4_BITS) == BigInteger.valueOf(IPV4_MAPPED)

	/** RFC 5952: lowercase, no leading zeros, the first longest run of two or more zero groups as `::`. */
	fun compressed(address: BigInteger): String {
		if (mapped(address)) return "::ffff:" + Ipv4.dotted(address.toLong() and IPV4_MAX)
		val groups = groups(address)
		val hex = groups.map { Integer.toHexString(it) }
		val run = longestZeroRun(groups)
		return if (run == null) {
			hex.joinToString(":")
		} else {
			hex.take(run.first).joinToString(":") + "::" + hex.drop(run.last + 1).joinToString(":")
		}
	}

	/** The first longest run of two or more zero groups, or null when there is none. */
	private fun longestZeroRun(groups: List<Int>): IntRange? {
		var bestStart = -1
		var bestLength = 1
		var i = 0
		while (i < groups.size) {
			val start = i
			while (i < groups.size && groups[i] == 0) i++
			if (i - start > bestLength) {
				bestStart = start
				bestLength = i - start
			}
			if (i == start) i++
		}
		return if (bestStart < 0) null else bestStart until bestStart + bestLength
	}

	private fun type(a: BigInteger): String {
		fun within(cidr: String): Boolean {
			val (base, bits) = cidr.split("/")
			val network = BigInteger(1, Inet6Address.ofLiteral(base).address)
			return a.xor(network).shiftRight(IPV6_BITS - bits.toInt()) == BigInteger.ZERO
		}
		return when {
			a == BigInteger.ZERO -> "Unspecified"
			a == BigInteger.ONE -> "Loopback"
			mapped(a) -> "IPv4-mapped"
			within("fe80::/10") -> "Link-local"
			within("fc00::/7") -> "Unique local"
			within("ff00::/8") -> "Multicast"
			within("2001:db8::/32") -> "Documentation"
			within("2000::/3") -> "Global unicast"
			else -> "Reserved"
		}
	}
}
