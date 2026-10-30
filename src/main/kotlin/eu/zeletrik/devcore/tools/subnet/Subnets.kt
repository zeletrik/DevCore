package eu.zeletrik.devcore.tools.subnet

import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address

/** IPv4 and IPv6 ranges from literals only: a host name is never looked up (that would be an outbound call). */
object Subnets {
	private const val NOT_AN_ADDRESS = "Not an IPv4 or IPv6 address"
	private val dottedQuad = Regex("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})")

	fun describe(text: String): String {
		val input = text.trim()
		val parts = input.split(Regex("\\s+"))
		return when {
			"/" in input -> {
				val (address, prefix) = input.split("/", limit = 2)
				val bits = prefix.toIntOrNull()
				if (":" in address) ipv6(address, bits ?: throw IllegalArgumentException("Prefix must be 0–128"))
				else ipv4(parseIpv4(address), bits ?: throw IllegalArgumentException("Prefix must be 0–32"))
			}
			parts.size == 2 -> ipv4(parseIpv4(parts[0]), prefixOf(parseIpv4(parts[1])))
			":" in input -> ipv6(input, 128)
			else -> ipv4(parseIpv4(input), 32)
		}
	}

	private fun parseIpv4(text: String): Long {
		val octets = dottedQuad.matchEntire(text)?.groupValues?.drop(1)?.map(String::toInt) ?: throw IllegalArgumentException(NOT_AN_ADDRESS)
		require(octets.all { it in 0..255 }) { NOT_AN_ADDRESS }
		return octets.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
	}

	private fun prefixOf(netmask: Long): Int {
		val bits = java.lang.Long.bitCount(netmask)
		val expected = if (bits == 0) 0L else (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
		require(netmask == expected) { "Netmask must be contiguous" }
		return bits
	}

	private fun ipv4(address: Long, prefix: Int): String {
		require(prefix in 0..32) { "Prefix must be 0–32" }
		val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
		val network = address and mask
		val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
		val total = 1L shl (32 - prefix)
		val (first, last, usable) = when (prefix) {
			32 -> Triple(address, address, 1L)
			31 -> Triple(network, broadcast, 2L)
			else -> Triple(network + 1, broadcast - 1, total - 2)
		}
		return listOf(
			"Address: ${dotted(address)}",
			"Network: ${dotted(network)}/$prefix",
			"Netmask: ${dotted(mask)}",
			"Wildcard: ${dotted(mask.inv() and 0xFFFFFFFFL)}",
			"Broadcast: ${if (prefix >= 31) "none" else dotted(broadcast)}",
			"First host: ${dotted(first)}",
			"Last host: ${dotted(last)}",
			"Usable hosts: $usable",
			"Total addresses: $total",
			"Type: ${ipv4Type(address)}",
			"Integer: $address",
			"Hex: 0x%08X".format(address),
			"Binary: " + (3 downTo 0).joinToString(".") { ((address shr (8 * it)) and 0xFF).toString(2).padStart(8, '0') },
		).joinToString("\n")
	}

	private fun dotted(value: Long) = (3 downTo 0).joinToString(".") { ((value shr (8 * it)) and 0xFF).toString() }

	private fun ipv4Type(a: Long): String {
		fun inRange(base: String, bits: Int) = (a xor parseIpv4(base)) shr (32 - bits) == 0L
		return when {
			a == 0L -> "Unspecified"
			inRange("10.0.0.0", 8) || inRange("172.16.0.0", 12) || inRange("192.168.0.0", 16) -> "Private"
			inRange("127.0.0.0", 8) -> "Loopback"
			inRange("169.254.0.0", 16) -> "Link-local"
			inRange("100.64.0.0", 10) -> "Shared address space"
			inRange("224.0.0.0", 4) -> "Multicast"
			inRange("240.0.0.0", 4) || inRange("0.0.0.0", 8) -> "Reserved"
			else -> "Public"
		}
	}

	private fun ipv6(text: String, prefix: Int): String {
		require(prefix in 0..128) { "Prefix must be 0–128" }
		require('%' !in text) { NOT_AN_ADDRESS }
		val literal = runCatching { Inet6Address.ofLiteral(text) }.getOrNull() ?: throw IllegalArgumentException(NOT_AN_ADDRESS)
		// ofLiteral turns ::ffff:a.b.c.d into an IPv4 address; keep it IPv6.
		val bytes = if (literal is Inet4Address) ByteArray(10) + byteArrayOf(-1, -1) + literal.address else literal.address
		val address = BigInteger(1, bytes)
		val all = BigInteger.ONE.shiftLeft(128) - BigInteger.ONE
		val mask = all.xor(BigInteger.ONE.shiftLeft(128 - prefix) - BigInteger.ONE)
		val network = address.and(mask)
		val last = network.or(all.xor(mask))
		return listOf(
			"Address: ${compressed(address)}",
			"Expanded: ${groups(address).joinToString(":") { "%04x".format(it) }}",
			"Network: ${compressed(network)}/$prefix",
			"Prefix length: $prefix",
			"First address: ${compressed(network)}",
			"Last address: ${compressed(last)}",
			"Total addresses: ${BigInteger.ONE.shiftLeft(128 - prefix)}",
			"Type: ${ipv6Type(address)}",
		).joinToString("\n")
	}

	private fun groups(address: BigInteger) = (7 downTo 0).map { address.shiftRight(16 * it).toInt() and 0xFFFF }

	private fun mapped(address: BigInteger) = address.shiftRight(32) == BigInteger.valueOf(0xFFFF)

	/** RFC 5952: lowercase, no leading zeros, the first longest run of two or more zero groups as `::`. */
	private fun compressed(address: BigInteger): String {
		if (mapped(address)) return "::ffff:" + dotted(address.toLong() and 0xFFFFFFFFL)
		val g = groups(address)
		var bestStart = -1
		var bestLength = 1
		var i = 0
		while (i < 8) {
			if (g[i] != 0) { i++; continue }
			val start = i
			while (i < 8 && g[i] == 0) i++
			if (i - start > bestLength) { bestStart = start; bestLength = i - start }
		}
		val hex = g.map { Integer.toHexString(it) }
		if (bestStart < 0) return hex.joinToString(":")
		return hex.take(bestStart).joinToString(":") + "::" + hex.drop(bestStart + bestLength).joinToString(":")
	}

	private fun ipv6Type(a: BigInteger): String {
		fun inRange(base: String, bits: Int) = a.xor(BigInteger(1, Inet6Address.ofLiteral(base).address)).shiftRight(128 - bits) == BigInteger.ZERO
		return when {
			a == BigInteger.ZERO -> "Unspecified"
			a == BigInteger.ONE -> "Loopback"
			mapped(a) -> "IPv4-mapped"
			inRange("fe80::", 10) -> "Link-local"
			inRange("fc00::", 7) -> "Unique local"
			inRange("ff00::", 8) -> "Multicast"
			inRange("2001:db8::", 32) -> "Documentation"
			inRange("2000::", 3) -> "Global unicast"
			else -> "Reserved"
		}
	}
}
