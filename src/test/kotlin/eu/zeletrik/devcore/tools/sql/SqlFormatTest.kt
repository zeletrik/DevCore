package eu.zeletrik.devcore.tools.sql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlFormatTest {
	private fun format(
		sql: String,
		dialect: SqlDialect = SqlDialect.STANDARD,
		indent: Int = 2,
		uppercase: Boolean = true,
	) = SqlFormat.format(sql, dialect, indent, uppercase)

	private fun error(sql: String) = assertFailsWith<IllegalArgumentException> { format(sql) }.message

	@Test
	fun `the chosen indentation reaches the layout`() {
		assertEquals(listOf("SELECT", "    a,", "    b", "FROM", "    t"), format("select a, b from t", indent = 4).lines())
	}

	@Test
	fun `statements are separated by one blank line`() {
		assertTrue("\n\n" in format("select 1; select 2") && "\n\n\n" !in format("select 1; select 2"))
	}

	@Test
	fun `keyword case`() {
		assertEquals(listOf("SELECT", "  a", "FROM", "  t"), format("select a from t").lines())
		assertEquals(listOf("SeLeCt", "  a", "FrOm", "  t"), format("SeLeCt a FrOm t", uppercase = false).lines())
	}

	@Test
	fun `PostgreSQL casts and the user's own placeholder text survive, and anything is laid out`() {
		assertTrue("a::int" in format("select a::int from t", SqlDialect.POSTGRESQL))
		assertTrue("__devcore_cast__" in format("select __devcore_cast__ from t"))
		assertTrue(format("hello (((").isNotBlank())
	}

	@Test
	fun `input up to 100,000 characters is formatted, more is refused`() {
		listOf(99_999, 100_000).forEach { assertTrue(format("select " + "a".repeat(it - 7)).startsWith("SELECT"), "$it") }
		assertEquals(
			"Input is too large for the SQL Formatter (max 100,000 characters)",
			error("select " + "a".repeat(99_994)),
		)
	}

	@Test
	fun `parentheses nested up to 100 levels are formatted, 101 are refused, quoted and commented ones don't count`() {
		listOf(99, 100).forEach { assertTrue(format("select " + "(".repeat(it) + "1" + ")".repeat(it)).startsWith("SELECT")) }
		assertEquals("Parentheses nested too deeply (max 100)", error("select " + "(".repeat(101) + "1" + ")".repeat(101)))
		val many = "(".repeat(150)
		format("select '$many' -- $many\n/* $many */ x")
		format("select 'it''s $many' || \"a\\\"b $many\" from t /* $many")
	}
}
