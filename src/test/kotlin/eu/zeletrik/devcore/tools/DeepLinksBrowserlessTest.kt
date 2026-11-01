package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.textfield.PasswordField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.tools.basicauth.BasicAuthView
import eu.zeletrik.devcore.tools.jsonpath.JsonPathView
import eu.zeletrik.devcore.tools.jwt.JwtView
import eu.zeletrik.devcore.tools.regex.RegexView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The public deep-link contract across all 40 tools: which accept `input`, which modes exist, nothing else is read. */
@SpringBootTest
class DeepLinksBrowserlessTest : SpringBrowserlessTest() {
	private val linked = setOf(
		"barcode-generator", "base64", "case-converter", "certificate-decoder", "chmod-calculator", "color-converter",
		"cron-explainer", "data-format-converter", "data-size-converter", "hash-generator", "html-entities", "json-formatter",
		"jsonpath-tester", "jwt", "line-tools", "markdown-to-html", "morse-code", "nato-alphabet", "number-base-converter",
		"qr-code-generator", "regex-tester", "sql-formatter", "string-escape", "subnet-calculator", "text-statistics",
		"time-zone-converter", "timestamp-converter", "unicode-escape", "url-encoder", "url-parser", "xml-formatter",
	)

	/** Mode names per tool, the default first. */
	private val modes = mapOf(
		"base64" to listOf("encode", "decode"),
		"html-entities" to listOf("encode", "decode"),
		"morse-code" to listOf("encode", "decode"),
		"string-escape" to listOf("encode", "decode"),
		"unicode-escape" to listOf("encode", "decode"),
		"url-encoder" to listOf("encode", "decode"),
		"basic-auth" to listOf("encode", "decode"),
		"jwt" to listOf("decode", "encode"),
		"password-hashing" to listOf("hash", "verify"),
	)

	private fun open(location: String, tool: Tool): Component {
		UI.getCurrent().navigate(location)
		return (currentView as Component).also { assertTrue(tool.view.isInstance(it), location) }
	}

	private fun Component.textFields(): List<Component> = children.toList().flatMap { child ->
		val editable = (child is TextField || child is TextArea || child is PasswordField) &&
			!(child as com.vaadin.flow.component.HasValue<*, *>).isReadOnly
		(if (editable) listOf(child) else emptyList()) + child.textFields()
	}

	private fun Component.fieldValues() =
		textFields().map { (it as com.vaadin.flow.component.HasValue<*, *>).value as String }

	private fun Component.modeName(): String {
		val group = this::class.memberProperties.first { it.name == "mode" }.getter.call(this) as RadioButtonGroup<*>
		return (group.value as Enum<*>).name.lowercase()
	}

	@Test
	fun `input fills the primary input of exactly the linked tools`() {
		Tool.entries.forEach { tool ->
			val view = open("${tool.id}?input=probe-42", tool)
			val primary = (view as? TextToolView<*>)?.input?.value
			if (tool.id in linked) assertEquals("probe-42", primary, tool.id)
			else assertTrue("probe-42" !in view.fieldValues(), tool.id)
		}
	}

	@Test
	fun `input goes to the right field where a tool has several`() {
		val regex = navigate("regex-tester?input=a1b22", RegexView::class.java)
		assertEquals("a1b22" to "", regex.input.value to regex.pattern.value)
		assertEquals("$", navigate("jsonpath-tester?input=%7B%22a%22%3A1%7D", JsonPathView::class.java).path.value)
		assertEquals(
			"""{"sub":"1"}""",
			navigate("jwt?mode=encode&input=%7B%22sub%22%3A%221%22%7D", JwtView::class.java).input.value,
		)
		val decode = navigate(
			"basic-auth?mode=decode&input=Basic%20QWxhZGRpbjpvcGVuIHNlc2FtZQ%3D%3D",
			BasicAuthView::class.java,
		)
		assertEquals(
			listOf("", "", ""),
			listOf(decode.input.value, decode.decodedUsername.value, decode.decodedPassword.value),
		)
		val encode = navigate("basic-auth?input=alice", BasicAuthView::class.java)
		assertEquals(listOf("", ""), listOf(encode.username.value, encode.password.value))
	}

	@Test
	fun `no secret or other field is ever read from the URL`() {
		val params = listOf(
			"password",
			"secret",
			"key",
			"hmac",
			"ssid",
			"pattern",
			"path",
			"replacement",
			"comment",
			"hash",
			"changed",
		)
			.joinToString("&") { "$it=from-url" }
		Tool.entries.forEach { tool ->
			val view = open("${tool.id}?$params", tool)
			assertTrue(view.fieldValues().none { "from-url" in it }, tool.id)
		}
	}

	@Test
	fun `modes by name, the default for anything else, and no mode on tools without one`() {
		modes.forEach { (id, names) ->
			val tool = Tool.entries.single { it.id == id }
			assertEquals(names[1], open("$id?mode=${names[1]}", tool).modeName(), id)
			assertEquals(names[0], open("$id?mode=foo", tool).modeName(), id)
		}
		val jwt = navigate("jwt?mode=foo&input=eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0.", JwtView::class.java)
		assertEquals("No expiry", jwt.status.value)
		val json = open("json-formatter?mode=decode&input=[1]", Tool.JSON) as TextToolView<*>
		assertEquals("[1]", json.input.value)
	}
}
