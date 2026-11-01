package eu.zeletrik.devcore.tools

import eu.zeletrik.devcore.tools.datasize.DataSizes
import eu.zeletrik.devcore.tools.datasize.DataUnit
import eu.zeletrik.devcore.tools.numberbase.InputBase
import eu.zeletrik.devcore.tools.numberbase.NumberBases
import eu.zeletrik.devcore.tools.regex.RegexTester
import eu.zeletrik.devcore.tools.sql.SqlDialect
import eu.zeletrik.devcore.tools.sql.SqlFormat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Cost caps checked before the expensive work, each at, below and above its bound. */
class CostCapsTest {
	private fun error(block: () -> Any) = assertFailsWith<IllegalArgumentException> { block() }.message

	private fun listed(length: Int, pattern: String = "a+") =
		RegexTester.list(RegexTester.compile(pattern, emptySet()), "a".repeat(length), RegexTester.deadline()).lines()

	@ParameterizedTest
	@ValueSource(ints = [199, 200])
	fun `regex values up to 200 characters are shown in full`(length: Int) {
		assertEquals("#1 [0, $length) \"${"a".repeat(length)}\"", listed(length)[1])
	}

	@Test
	fun `regex values over 200 characters are cut`() {
		assertEquals("#1 [0, 201) \"${"a".repeat(200)}…\"", listed(201)[1])
		assertEquals("  1: [0, 300) \"${"a".repeat(200)}…\"", listed(300, "(a+)")[2])
	}

	private fun sql(text: String) = SqlFormat.format(text, SqlDialect.STANDARD, 2, true)

	@ParameterizedTest
	@ValueSource(ints = [99_999, 100_000])
	fun `sql up to 100,000 characters is formatted`(length: Int) {
		assertTrue(sql("select " + "a".repeat(length - 7)).startsWith("SELECT"))
	}

	@Test
	fun `sql over 100,000 characters is refused`() {
		assertEquals(
			"Input is too large for the SQL Formatter (max 100,000 characters)",
			error { sql("select " + "a".repeat(99_994)) },
		)
	}

	@ParameterizedTest
	@ValueSource(ints = [99, 100])
	fun `sql nested up to 100 levels is formatted`(depth: Int) {
		assertTrue(sql("select " + "(".repeat(depth) + "1" + ")".repeat(depth)).startsWith("SELECT"))
	}

	@Test
	fun `sql nested 101 levels is refused, parentheses in literals and comments don't count`() {
		assertEquals(
			"Parentheses nested too deeply (max 100)",
			error { sql("select " + "(".repeat(101) + "1" + ")".repeat(101)) },
		)
		sql("select '" + "(".repeat(150) + "' -- " + "(".repeat(150) + "\n/* " + "(".repeat(150) + " */ x")
	}

	@ParameterizedTest
	@ValueSource(ints = [9_999, 10_000])
	fun `number base up to 10,000 digits converts`(digits: Int) {
		assertEquals(digits, NumberBases.convert("-0x_" + "1".repeat(digits), InputBase.AUTO, false).hex.length - 1)
	}

	@Test
	fun `number base over 10,000 digits is refused`() {
		assertEquals(
			"Number too long (max 10,000 digits)",
			error { NumberBases.convert("1".repeat(10_001), InputBase.DECIMAL, false) },
		)
	}

	@ParameterizedTest
	@ValueSource(strings = ["1e29", "999999999999999999999999999999", "0.123456789012345678901234567890"])
	fun `data sizes within range convert`(value: String) {
		assertEquals(DataUnit.entries.size, DataSizes.convert(value, DataUnit.MB).lines().size)
	}

	@ParameterizedTest
	@ValueSource(strings = ["1e30", "0.1234567890123456789012345678901", "1e999999999", "1e-30000000"])
	fun `data sizes out of range are refused quickly`(value: String) {
		val started = System.nanoTime()
		assertEquals("Value out of range", error { DataSizes.convert(value, DataUnit.MB) })
		assertTrue(System.nanoTime() - started < 1_000_000_000)
	}
}
