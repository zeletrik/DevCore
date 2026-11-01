package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import eu.zeletrik.devcore.tools.certificate.CertificateView
import eu.zeletrik.devcore.tools.jsonpath.JsonPathView
import eu.zeletrik.devcore.tools.regex.RegexView
import eu.zeletrik.devcore.tools.sql.SqlDialect
import eu.zeletrik.devcore.tools.sql.SqlFormatterView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Inputs that recurse past a default thread stack still work, because the deep tools compute on a large stack. */
@SpringBootTest
class DeepWorkBrowserlessTest : SpringBrowserlessTest() {
	private val bs = "\\"

	@Test
	fun `regex - an alternation over 10,000 characters`() {
		val view = navigate(RegexView::class.java)
		test(view.pattern).setValue("(.|${bs}n)*")
		view.input.value = "ab${'\n'}".repeat(3_334).take(10_000)
		assertFalse(view.input.isInvalid)
		val lines = view.matches.value.lines()
		assertEquals("2 matches", lines.first())
		assertTrue(lines.any { it.startsWith("#1 [0, 10000) ") })
		assertTrue(lines.any { it.startsWith("#2 [10000, 10000) ") })
	}

	@Test
	fun `sql - a literal with 2,000 escaped quotes`() {
		val view = navigate(SqlFormatterView::class.java)
		view.dialect.value = SqlDialect.MYSQL
		val literal = "'" + "$bs'".repeat(2_000) + "'"
		view.input.value = "select $literal"
		assertFalse(view.input.isInvalid)
		assertTrue(view.output.value.contains(literal))
	}

	@Test
	fun `jsonpath - a filter nested 20,000 parentheses deep`() {
		val view = navigate(JsonPathView::class.java)
		view.input.value = "[1]"
		test(view.path).setValue("$[?(" + "(".repeat(20_000) + "@ == 1" + ")".repeat(20_000) + ")]")
		assertFalse(view.path.isInvalid)
		assertEquals("[${'\n'}  1${'\n'}]", view.output.value)
	}

	@Test
	fun `certificate - DER nested 20,000 levels is an error, not a crash`() {
		val view = navigate(CertificateView::class.java)
		val der = ByteArray(20_000 * 2) { if (it % 2 == 0) 0x30 else 0x80.toByte() } + ByteArray(20_000 * 2)
		val body = Base64.getMimeEncoder(64, "${'\n'}".toByteArray()).encodeToString(der)
		view.input.value = "-----BEGIN CERTIFICATE-----${'\n'}$body${'\n'}-----END CERTIFICATE-----"
		assertTrue(view.input.isInvalid)
		assertEquals("", view.output.value)
	}
}
