package eu.zeletrik.devcore.tools.sql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlFormatTest {
	private fun format(sql: String, dialect: SqlDialect = SqlDialect.STANDARD, indent: Int = 2, uppercase: Boolean = true) =
		SqlFormat.format(sql, dialect, indent, uppercase)

	@Test
	fun `offers the six dialects, Standard SQL first`() {
		assertEquals(listOf("Standard SQL", "PostgreSQL", "MySQL", "MariaDB", "SQL Server", "Oracle PL/SQL"), SqlDialect.entries.map { it.label })
	}

	@Test
	fun `clauses on their own lines, list items indented`() {
		val lines = format("select a, b from t where x = 1 order by a").lines()
		listOf("SELECT", "FROM", "WHERE", "ORDER BY").forEach { clause -> assertTrue(lines.any { it == clause }, "$clause in $lines") }
		val select = format("select a, b from t").lines()
		assertEquals(listOf("SELECT", "  a,", "  b", "FROM", "  t"), select)
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
	fun `literals, comments and dialect syntax survive, and anything is laid out`() {
		val out = format("select 'from x' from t -- select me")
		assertTrue("'from x'" in out && "-- select me" in out)
		assertTrue("a::int" in format("select a::int from t", SqlDialect.POSTGRESQL))
		assertTrue(format("hello (((").isNotBlank())
	}
}
