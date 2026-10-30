package eu.zeletrik.devcore.tools.subnet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SubnetsTest {
	private fun fields(input: String) = Subnets.describe(input).lines().associate { it.substringBefore(": ") to it.substringAfter(": ") }

	private fun error(input: String) = assertFailsWith<IllegalArgumentException> { Subnets.describe(input) }.message

	@Test
	fun `IPv4 fields`() {
		assertEquals(
			mapOf(
				"Address" to "192.168.1.130", "Network" to "192.168.1.128/26", "Netmask" to "255.255.255.192",
				"Wildcard" to "0.0.0.63", "Broadcast" to "192.168.1.191", "First host" to "192.168.1.129",
				"Last host" to "192.168.1.190", "Usable hosts" to "62", "Total addresses" to "64", "Type" to "Private",
				"Integer" to "3232235906", "Hex" to "0xC0A80182", "Binary" to "11000000.10101000.00000001.10000010",
			),
			fields("192.168.1.130/26"),
		)
	}

	@Test
	fun `IPv4 types`() {
		mapOf(
			"10.0.0.1" to "Private", "8.8.8.8" to "Public", "127.0.0.1" to "Loopback", "169.254.1.1" to "Link-local",
			"224.0.0.1" to "Multicast", "100.64.0.1" to "Shared address space", "240.0.0.1" to "Reserved", "0.0.0.0" to "Unspecified",
		).forEach { (address, type) -> assertEquals(type, fields(address)["Type"], address) }
	}

	@Test
	fun `IPv6 fields and types`() {
		val v6 = fields("2001:db8:0:0:1::1/64")
		assertEquals("2001:db8::1:0:0:1", v6["Address"])
		assertEquals("2001:0db8:0000:0000:0001:0000:0000:0001", v6["Expanded"])
		assertEquals("2001:db8::/64", v6["Network"])
		assertEquals("64", v6["Prefix length"])
		assertEquals("2001:db8::", v6["First address"])
		assertEquals("2001:db8::ffff:ffff:ffff:ffff", v6["Last address"])
		assertEquals("18446744073709551616", v6["Total addresses"])
		assertEquals("Documentation", v6["Type"])
		mapOf(
			"2606:4700::1" to "Global unicast", "fd00::1" to "Unique local", "fe80::1" to "Link-local", "::1" to "Loopback",
			"ff02::1" to "Multicast", "::" to "Unspecified", "::ffff:192.0.2.1" to "IPv4-mapped",
		).forEach { (address, type) -> assertEquals(type, fields(address)["Type"], address) }
		assertEquals("::ffff:192.0.2.1", fields("::ffff:192.0.2.1")["Address"])
	}

	@Test
	fun `input forms, point-to-point and single hosts`() {
		assertEquals("10.0.0.0/24", fields("10.0.0.5 255.255.255.0")["Network"])
		assertEquals("10.0.0.5/32", fields("10.0.0.5")["Network"])
		assertEquals("2001:db8::1/128", fields("2001:db8::1")["Network"])
		val p2p = fields("10.0.0.5/31")
		assertEquals(listOf("10.0.0.4/31", "2", "none"), listOf(p2p["Network"], p2p["Usable hosts"], p2p["Broadcast"]))
		val host = fields("10.0.0.5/32")
		assertEquals(listOf("1", "1"), listOf(host["Usable hosts"], host["Total addresses"]))
	}

	@Test
	fun `prefix bounds`() {
		assertEquals("10.1.2.0/24", fields("10.1.2.3/24")["Network"])
		assertEquals(listOf("0.0.0.0/0", "4294967296"), fields("10.1.2.3/0").let { listOf(it["Network"], it["Total addresses"]) })
		assertEquals(listOf("10.1.2.3/32", "1"), fields("10.1.2.3/32").let { listOf(it["Network"], it["Total addresses"]) })
		assertEquals("Prefix must be 0–32", error("10.1.2.3/33"))
		assertEquals("2001:db8::/48", fields("2001:db8::1/48")["Network"])
		assertEquals("340282366920938463463374607431768211456", fields("2001:db8::1/0")["Total addresses"])
		assertEquals("1", fields("2001:db8::1/128")["Total addresses"])
		assertEquals("Prefix must be 0–128", error("2001:db8::1/129"))
	}

	@Test
	fun `bad masks and host names are rejected, never resolved`() {
		assertEquals("Netmask must be contiguous", error("192.168.1.0 255.0.255.0"))
		listOf("example.com/24", "localhost", "fe80::1%eth0", "300.1.1.1").forEach { assertEquals("Not an IPv4 or IPv6 address", error(it), it) }
	}
}
