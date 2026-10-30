package eu.zeletrik.devcore.tools.sql

import com.github.vertical_blank.sqlformatter.SqlFormatter
import com.github.vertical_blank.sqlformatter.core.FormatConfig
import com.github.vertical_blank.sqlformatter.languages.Dialect

enum class SqlDialect(val label: String, val dialect: Dialect) {
	STANDARD("Standard SQL", Dialect.StandardSql),
	POSTGRESQL("PostgreSQL", Dialect.PostgreSql),
	MYSQL("MySQL", Dialect.MySql),
	MARIADB("MariaDB", Dialect.MariaDb),
	SQL_SERVER("SQL Server", Dialect.TSql),
	ORACLE("Oracle PL/SQL", Dialect.PlSql),
}

/** A formatter, not a validator: any text is laid out, nothing is rejected. */
object SqlFormat {
	// NOTE: the formatter splits PostgreSQL casts (a::int → a :: INT); shielding `::` inside an identifier keeps them intact.
	private const val CAST = "__devcore_cast__"

	fun format(sql: String, dialect: SqlDialect, indent: Int, uppercase: Boolean): String {
		val config = FormatConfig.builder().indent(" ".repeat(indent)).uppercase(uppercase).linesBetweenQueries(2).build()
		val shielded = if (CAST in sql) sql else sql.replace("::", CAST)
		return SqlFormatter.of(dialect.dialect).format(shielded, config).replace(CAST, "::")
	}
}
