package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.select.Select
import eu.zeletrik.devcore.shell.Mode
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.tools.chmod.ChmodView
import eu.zeletrik.devcore.tools.json.JsonFormatterView
import eu.zeletrik.devcore.tools.jwt.JwtView
import eu.zeletrik.devcore.tools.keys.KeyPairView
import eu.zeletrik.devcore.tools.numberbase.NumberBaseView
import eu.zeletrik.devcore.tools.password.PasswordView
import eu.zeletrik.devcore.tools.qr.QrCodeView
import eu.zeletrik.devcore.tools.random.RandomNumberView
import eu.zeletrik.devcore.tools.sql.SqlDialect
import eu.zeletrik.devcore.tools.sql.SqlFormat
import eu.zeletrik.devcore.tools.sql.SqlFormatterView
import eu.zeletrik.devcore.tools.unicode.UnicodeEscapeView
import eu.zeletrik.devcore.tools.uuid.IdVersion
import eu.zeletrik.devcore.tools.uuid.UuidGeneratorView
import eu.zeletrik.devcore.tools.xml.XmlFormatterView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class ControlsBrowserlessTest : SpringBrowserlessTest() {
	private fun Component.descendants(): Sequence<Component> =
		sequenceOf(this) + children.toList().asSequence().flatMap { it.descendants() }

	@Test
	fun `radio groups hold 2 to 4 choices and selects 5 or more, in every tool`() {
		Tool.entries.forEach { tool ->
			UI.getCurrent().navigate(tool.view)
			val view = currentView as Component
			view.descendants().forEach { component ->
				when (component) {
					is RadioButtonGroup<*> -> assertTrue(component.listDataView.itemCount in 2..4, "${tool.id}: ${component.label}")
					is Select<*> -> assertTrue(component.listDataView.itemCount >= 5, "${tool.id}: ${component.label}")
				}
			}
		}
	}

	@Test
	fun `former selects and radios swapped to the rule`() {
		assertTrue(navigate(NumberBaseView::class.java).base is Select<*>)
		assertTrue(navigate(JwtView::class.java).algorithm is RadioButtonGroup<*>)
		assertTrue(navigate(KeyPairView::class.java).algorithm is RadioButtonGroup<*>)
		val qr = navigate(QrCodeView::class.java)
		assertTrue(qr.level is RadioButtonGroup<*> && qr.security is RadioButtonGroup<*>)
		assertTrue(navigate(UnicodeEscapeView::class.java).format is RadioButtonGroup<*>)
	}

	@Test
	fun `chmod permissions are one labelled group`() {
		val view = navigate(ChmodView::class.java)
		val group = view.descendants().single { it.element.getAttribute("role") == "group" }
		val label = view.descendants().single { it.id.orElse("") == group.element.getAttribute("aria-labelledby") }
		assertEquals("Permissions", label.element.text)
		assertEquals(12, group.descendants().count { it is Checkbox })
	}

	@Test
	fun `sql labels and the uppercase keywords checkbox`() {
		val view = navigate(SqlFormatterView::class.java)
		assertEquals("Indentation", view.indent.label)
		assertTrue(view.uppercase is Checkbox)
		assertEquals("Uppercase keywords", view.uppercase.label)
		assertTrue(view.uppercase.value)
		val asWritten = SqlFormat.format("SeLeCt a FrOm t", SqlDialect.STANDARD, 2, false)
		assertTrue("SeLeCt" in asWritten && "FrOm" in asWritten, asWritten)
	}

	@Test
	fun `modes - Unicode escape and unescape, JWT decode first`() {
		val unicode = navigate(UnicodeEscapeView::class.java)
		assertEquals(
			listOf("Escape", "Unescape"),
			unicode.mode.listDataView.items.toList().map { unicode.mode.itemLabelGenerator.apply(it) },
		)
		assertEquals(Mode.DECODE, navigate("unicode-escape?mode=decode", UnicodeEscapeView::class.java).mode.value)
		assertEquals(listOf(Mode.DECODE, Mode.ENCODE), navigate(JwtView::class.java).mode.listDataView.items.toList())
	}

	@Test
	fun `output labels follow the mode`() {
		val password = navigate(PasswordView::class.java)
		password.passphrase.value = true
		assertEquals("Passphrases", password.output.label)
		val uuid = navigate(UuidGeneratorView::class.java)
		uuid.version.value = IdVersion.ULID
		assertEquals("ULIDs", uuid.output.label)
		listOf(
			navigate(JsonFormatterView::class.java).output,
			navigate(XmlFormatterView::class.java).output,
			navigate(SqlFormatterView::class.java).output,
		)
			.forEach { assertEquals("Formatted", it.label) }
	}

	@Test
	fun `random number asks for a whole number`() {
		val view = navigate(RandomNumberView::class.java)
		val before = view.output.value
		test(view.min).setValue("abc")
		assertEquals("Enter a whole number", view.min.errorMessage)
		assertEquals(before, view.output.value)
	}
}
