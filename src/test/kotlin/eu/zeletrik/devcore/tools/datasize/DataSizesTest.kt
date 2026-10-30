package eu.zeletrik.devcore.tools.datasize

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataSizesTest {
	private fun lines(value: String, unit: DataUnit) = DataSizes.convert(value, unit).lines()

	@Test
	fun `one megabyte in every unit`() {
		assertEquals(
			listOf(
				"8000000 bit", "8000 kbit", "8 Mbit", "0.008 Gbit", "0.000008 Tbit", "1000000 B", "1000 kB", "1 MB", "0.001 GB",
				"0.000001 TB", "0 PB", "976.5625 KiB", "0.953674 MiB", "0.000931 GiB", "0.000001 TiB", "0 PiB",
			),
			lines("1", DataUnit.MB),
		)
	}

	@Test
	fun `binary source, plain notation, fractions and zero`() {
		val gib = lines("1", DataUnit.GIB)
		listOf("8589934592 bit", "1073741824 B", "1073.741824 MB", "1.073742 GB", "1048576 KiB", "1024 MiB").forEach { assertTrue(it in gib, it) }
		val pb = lines("1", DataUnit.PB)
		assertTrue("8000000000000000 bit" in pb && "976562500000 KiB" in pb)
		assertTrue("1500 kB" in lines("1.5", DataUnit.MB))
		assertTrue(lines("0", DataUnit.MB).all { it.startsWith("0 ") })
	}

	@Test
	fun `negative or non-numbers are rejected`() {
		listOf("-1", "abc").forEach { assertFailsWith<IllegalArgumentException> { DataSizes.convert(it, DataUnit.MB) } }
	}
}
