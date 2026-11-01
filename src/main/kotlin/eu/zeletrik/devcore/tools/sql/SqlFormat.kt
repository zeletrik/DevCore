package eu.zeletrik.devcore.tools.sql

import com.github.vertical_blank.sqlformatter.SqlFormatter
import com.github.vertical_blank.sqlformatter.core.FormatConfig
import com.github.vertical_blank.sqlformatter.languages.Dialect

/** The dialects of the sql-formatter library offered. */
enum class SqlDialect(val label: String, val dialect: Dialect) {
	STANDARD("Standard SQL", Dialect.StandardSql),
	POSTGRESQL("PostgreSQL", Dialect.PostgreSql),
	MYSQL("MySQL", Dialect.MySql),
	MARIADB("MariaDB", Dialect.MariaDb),
	SQL_SERVER("SQL Server", Dialect.TSql),
	ORACLE("Oracle PL/SQL", Dialect.PlSql),
}

/** A formatter, not a validator: any text within the size and nesting caps is laid out, nothing else is rejected. */
object SqlFormat {
	// NOTE: the formatter splits PostgreSQL casts (a::int → a :: INT); an identifier-safe stand-in for `::` keeps them.
	private const val CAST = "__devcore_cast__"
	private const val MAX_CHARS = 100_000
	private const val MAX_DEPTH = 100

	fun format(sql: String, dialect: SqlDialect, indent: Int, uppercase: Boolean): String {
		// NOTE: the library's cost grows faster than its input (and with nesting), so both are capped before it runs.
		require(sql.length <= MAX_CHARS) { "Input is too large for the SQL Formatter (max 100,000 characters)" }
		require(nesting(sql) <= MAX_DEPTH) { "Parentheses nested too deeply (max 100)" }
		val config = FormatConfig.builder().indent(" ".repeat(indent)).uppercase(uppercase).linesBetweenQueries(2).build()
		val shield = CAST !in sql
		val formatted = SqlFormatter.of(dialect.dialect).format(if (shield) sql.replace("::", CAST) else sql, config)
		return if (shield) formatted.replace(CAST, "::") else formatted
	}

	/** The deepest parenthesis nesting outside quoted literals and comments. */
	private fun nesting(sql: String): Int {
		var depth = 0
		var deepest = 0
		var i = 0
		while (i < sql.length) {
			when (val c = sql[i]) {
				'\'', '"', '`' -> i = closing(sql, i, c)
				'-', '/' -> i = commentEnd(sql, i)
				'(' -> deepest = maxOf(deepest, ++depth)
				')' -> depth = maxOf(0, depth - 1)
			}
			i++
		}
		return deepest
	}

	/** The last index of a line or block comment starting at [start], or [start] when none starts there. */
	private fun commentEnd(sql: String, start: Int): Int = when {
		sql.startsWith("--", start) -> sql.indexOf('\n', start).let { if (it < 0) sql.length else it }
		sql.startsWith("/*", start) -> sql.indexOf("*/", start + 2).let { if (it < 0) sql.length else it + 1 }
		else -> start
	}

	/** Index of the quote closing the literal opened at [start]; a backslash or a doubled quote escapes it. */
	private fun closing(sql: String, start: Int, quote: Char): Int {
		var i = start + 1
		while (i < sql.length) {
			when {
				sql[i] == '\\' -> i++
				sql[i] == quote && sql.getOrNull(i + 1) == quote -> i++
				sql[i] == quote -> return i
			}
			i++
		}
		return sql.length
	}
}
