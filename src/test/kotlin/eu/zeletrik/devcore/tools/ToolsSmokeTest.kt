package eu.zeletrik.devcore.tools

import com.vaadin.browserless.SpringBrowserlessTest
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasValue
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.PasswordField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import eu.zeletrik.devcore.shell.Mode
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.tools.basicauth.BasicAuthView
import eu.zeletrik.devcore.tools.chmod.ChmodView
import eu.zeletrik.devcore.tools.color.ColorView
import eu.zeletrik.devcore.tools.hash.HashGeneratorView
import eu.zeletrik.devcore.tools.hashing.PasswordHashes
import eu.zeletrik.devcore.tools.hashing.PasswordHashingView
import eu.zeletrik.devcore.tools.htmlentities.HtmlEntitiesView
import eu.zeletrik.devcore.tools.jsonpath.JsonPathView
import eu.zeletrik.devcore.tools.jwt.JwtView
import eu.zeletrik.devcore.tools.keys.KeyAlgorithm
import eu.zeletrik.devcore.tools.keys.KeyPairView
import eu.zeletrik.devcore.tools.password.PasswordView
import eu.zeletrik.devcore.tools.qr.QrCodeView
import eu.zeletrik.devcore.tools.random.RandomNumberView
import eu.zeletrik.devcore.tools.regex.RegexTester
import eu.zeletrik.devcore.tools.regex.RegexView
import eu.zeletrik.devcore.tools.timestamp.TimestampView
import eu.zeletrik.devcore.tools.timezone.TimeZoneView
import eu.zeletrik.devcore.tools.unicode.EscapeFormat
import eu.zeletrik.devcore.tools.unicode.UnicodeEscapeView
import eu.zeletrik.devcore.tools.uuid.IdVersion
import eu.zeletrik.devcore.tools.uuid.UuidGeneratorView
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every tool once: the deep-link contract across all of them, then the happy path of each view that does more than
 * pass its input to its logic (mode switches, field visibility, options that change the call).
 */
@SpringBootTest
class ToolsSmokeTest : SpringBrowserlessTest() {
	private val linked = setOf(
		"barcode-generator",
		"base64",
		"case-converter",
		"certificate-decoder",
		"chmod-calculator",
		"color-converter",
		"cron-explainer",
		"data-format-converter",
		"data-size-converter",
		"hash-generator",
		"html-entities",
		"json-formatter",
		"jsonpath-tester",
		"jwt",
		"line-tools",
		"markdown-to-html",
		"morse-code",
		"nato-alphabet",
		"number-base-converter",
		"qr-code-generator",
		"regex-tester",
		"sql-formatter",
		"string-escape",
		"subnet-calculator",
		"text-statistics",
		"time-zone-converter",
		"timestamp-converter",
		"unicode-escape",
		"url-encoder",
		"url-parser",
		"xml-formatter",
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

	private val jwtIo = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
			"eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ." +
			"SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

	private fun open(location: String, tool: Tool): Component {
		UI.getCurrent().navigate(location)
		return (currentView as Component).also { assertTrue(tool.view.isInstance(it), location) }
	}

	private fun Component.descendants(): Sequence<Component> =
		sequenceOf(this) + children.toList().asSequence().flatMap { it.descendants() }

	private fun Component.fieldValues() = descendants()
		.filter { (it is TextField || it is TextArea || it is PasswordField) && !(it as HasValue<*, *>).isReadOnly }
		.map { (it as HasValue<*, *>).value as String }
		.toList()

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
		val names = listOf(
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
			"changed"
		)
		val params = names.joinToString("&") { "$it=from-url" }
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

	@Test
	fun `radio groups hold 2 to 4 choices and selects 5 or more, in every tool`() {
		Tool.entries.forEach { tool ->
			UI.getCurrent().navigate(tool.view)
			(currentView as Component).descendants().forEach { component ->
				when (component) {
					is RadioButtonGroup<*> -> assertTrue(
						component.listDataView.itemCount in 2..4,
						"${tool.id}: ${component.label}"
					)

					is Select<*> -> assertTrue(component.listDataView.itemCount >= 5, "${tool.id}: ${component.label}")
				}
			}
		}
	}

	@Test
	fun `chmod - checkboxes follow the input and the input follows the checkboxes`() {
		val view = navigate("chmod-calculator?input=640", ChmodView::class.java)
		assertEquals("chmod 640 <file>", view.command.value)
		val ticked = view.boxes.filterValues { it.value }.values.map { it.label }
		assertEquals(listOf("Owner Read", "Owner Write", "Group Read"), ticked)
		test(view.input).setValue("rw-r--r--")
		test(view.boxes.getValue(1 shl 1)).click()
		assertEquals("646", view.input.value)
		assertEquals("rw-r--rw-", view.symbolic.value)
	}

	@Test
	fun `color - swatch from the parsed color, picker writes the input`() {
		val view = navigate("color-converter?input=Red", ColorView::class.java)
		assertTrue(view.swatch.isVisible)
		assertEquals("#ff0000", view.swatch.style.get("background-color"))
		assertEquals("#ff0000", view.picker.value)
		view.pick("#00ff00")
		assertEquals("#00ff00", view.input.value)
		assertEquals("lime", view.name.value)
	}

	@Test
	fun `jsonpath - an empty path or whitespace-only JSON is nothing to do`() {
		val view = navigate("jsonpath-tester?input=%7B%22a%22%3A1%7D", JsonPathView::class.java)
		assertTrue(view.output.value.startsWith("["))
		test(view.path).setValue("")
		assertEquals("", view.output.value)
		assertTrue(!view.path.isInvalid && !view.input.isInvalid)
		test(view.path).setValue("$")
		view.input.value = "  \n\t "
		assertFalse(view.input.isInvalid)
		assertEquals("", view.output.value)
	}

	@Test
	fun `timestamp and time zone - the zone reaches the output, Now fills the input, a bad format marks its field`() {
		val timestamp = navigate("timestamp-converter?input=2026-10-28T10:00:00", TimestampView::class.java)
		test(timestamp.zone).selectItem("Europe/Budapest")
		assertEquals("2026-10-28T09:00:00Z", timestamp.utc.value)
		test(timestamp.input).setValue("1700000000")
		test(timestamp.format).setValue("HH:mm {")
		assertTrue(timestamp.format.isInvalid)
		assertEquals("", timestamp.custom.value)
		assertEquals("1700000000", timestamp.seconds.value)
		assertFalse(timestamp.input.isInvalid)
		test(timestamp.now).click()
		assertTrue(abs(timestamp.input.value.toLong() - Instant.now().epochSecond) < 5)

		val zones = navigate(TimeZoneView::class.java)
		test(zones.now).click()
		assertTrue(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(zones.input.value))
		assertTrue(zones.output.value.startsWith("UTC  "))
	}

	@Test
	fun `html entities - deep link in decode mode, and the encode option only shows when encoding`() {
		val view = navigate("html-entities?mode=decode&input=%26lt%3Bb%26gt%3B", HtmlEntitiesView::class.java)
		assertEquals("<b>", view.output.value)
		assertFalse(view.nonAscii.isVisible)
		test(view.mode).selectItem("Encode")
		assertTrue(view.nonAscii.isVisible)
		assertEquals("&amp;lt;b&amp;gt;", view.output.value)
	}

	@Test
	fun `unicode escape - follows the chosen format, which only applies when escaping`() {
		val view = navigate("unicode-escape?input=%C3%A9", UnicodeEscapeView::class.java)
		assertEquals("\\u00E9", view.output.value)
		test(view.format).selectItem(EscapeFormat.CODE_POINTS.label)
		assertEquals("U+00E9", view.output.value)
		assertFalse(view.escapeAscii.isVisible)
		test(view.mode).selectItem("Unescape")
		assertFalse(view.format.isVisible)
	}

	@Test
	fun `QR - downloads only while a code is shown, Wi-Fi mode swaps the inputs`() {
		val view = navigate(QrCodeView::class.java)
		assertNull(view.code.current)
		assertFalse(view.code.png.isEnabled || view.code.svg.isEnabled)
		test(view.input).setValue("hello")
		assertNotNull(view.code.current)
		assertTrue(view.code.png.isEnabled && view.code.svg.isEnabled && view.code.image.isVisible)
		test(view.wifiMode).selectItem("Wi-Fi network")
		assertFalse(view.input.isVisible)
		assertNull(view.code.current)
		test(view.ssid).setValue("home")
		assertNotNull(view.code.current)
	}

	@Test
	fun `password - passphrase mode relabels and swaps its options, invalid options keep the output`() {
		val view = navigate(PasswordView::class.java)
		assertEquals(20, view.output.value.length)
		assertEquals("≈ 129 bits each", view.entropy.text)
		val before = view.output.value
		test(view.length).setValue(129)
		assertTrue(view.length.isInvalid)
		assertEquals(before, view.output.value)
		test(view.length).setValue(20)
		test(view.sets).deselectAll()
		assertTrue(view.sets.isInvalid)
		test(view.passphrase).selectItem("Passphrase")
		assertEquals("Passphrases", view.output.label)
		assertTrue(view.words.parent.get().isVisible)
		assertFalse(view.length.parent.get().isVisible)
		test(view.separator).setValue("0123456789")
		assertTrue(view.output.value.contains("0123456789"))
		val passphrase = view.output.value
		test(view.separator).setValue("0123456789A")
		assertEquals("At most 10 characters", view.separator.errorMessage)
		assertEquals(passphrase, view.output.value)
	}

	@Test
	fun `random number - a range error lands on the Max field and keeps the output`() {
		val view = navigate(RandomNumberView::class.java)
		val before = view.output.value
		test(view.max).setValue("0")
		assertEquals("Must be at least the minimum", view.max.errorMessage)
		assertEquals(before, view.output.value)
	}

	@Test
	fun `password hashing - verify shows Match or No match and hides the hash options`() {
		val view = navigate("password-hashing?mode=verify", PasswordHashingView::class.java)
		assertFalse(view.output.isVisible)
		assertTrue(view.hash.isVisible)
		test(view.input).setValue("hunter2")
		test(view.hash).setValue(PasswordHashes.bcrypt("hunter2", 4))
		assertEquals("Match", view.result.text)
		test(view.input).setValue("hunter3")
		assertEquals("No match", view.result.text)
	}

	@Test
	fun `key pairs - a comment edit keeps the keys, a comment over 1,000 characters is refused`() {
		val view = navigate(KeyPairView::class.java)
		val first = view.privateKey.value
		assertTrue(view.openSsh.value.startsWith("ssh-ed25519 "))
		test(view.comment).setValue("me@host")
		assertEquals(first, view.privateKey.value)
		assertTrue(view.openSsh.value.endsWith(" me@host"))
		test(view.algorithm).selectItem(KeyAlgorithm.RSA_2048.label)
		assertTrue(view.openSsh.value.startsWith("ssh-rsa ") && view.openSsh.value.endsWith(" me@host"))
		val (key, line) = view.privateKey.value to view.openSsh.value
		test(view.comment).setValue("c".repeat(1_001))
		assertEquals("At most 1,000 characters", view.comment.errorMessage)
		assertEquals(key, view.privateKey.value)
		assertEquals(line, view.openSsh.value)
	}

	@Test
	fun `jwt - the notice follows the secret, each mode keeps its own text and labels`() {
		val view = navigate("jwt?input=$jwtIo", JwtView::class.java)
		assertEquals(Mode.DECODE, view.mode.value)
		assertTrue(view.notice.isVisible)
		assertEquals("Signature not verified — enter the secret to check it", view.notice.text)
		test(view.secret).setValue("your-256-bit-secret")
		assertEquals("Signature valid", view.notice.text)
		test(view.secret).setValue("wrong")
		assertEquals("Signature invalid", view.notice.text)
		test(view.mode).selectItem("Encode")
		assertEquals("", view.input.value)
		assertEquals("Payload", view.input.label)
		test(view.secret).setValue("")
		test(view.input).setValue("""{"sub":"1234567890","name":"John Doe","iat":1516239022}""")
		assertEquals("Enter a secret to sign the token", view.secret.errorMessage)
		test(view.secret).setValue("your-256-bit-secret")
		assertEquals(jwtIo, view.token.value)
		test(view.mode).selectItem("Decode")
		assertEquals(jwtIo, view.input.value)
	}

	@Test
	fun `hash - a key turns the digests into HMACs and says so`() {
		val view = navigate("hash-generator?input=abc", HashGeneratorView::class.java)
		assertEquals("MD5", view.outputs.getValue("MD5").label)
		test(view.key).setValue("k")
		assertEquals("HMAC-SHA-256", view.outputs.getValue("SHA-256").label)
	}

	@Test
	fun `uuid - version and count reach the output, ULID disables the UUID-only options and relabels`() {
		val view = navigate(UuidGeneratorView::class.java)
		assertEquals(4, UUID.fromString(view.output.value).version())
		test(view.version).selectItem("v7")
		assertEquals(7, UUID.fromString(view.output.value).version())
		test(view.count).setValue(3)
		assertEquals(3, view.output.value.lines().size)
		test(view.version).selectItem(IdVersion.ULID.label)
		assertFalse(view.uppercase.isEnabled || view.hyphens.isEnabled)
		assertEquals("ULIDs", view.output.label)
		assertTrue(view.output.value.lines().all { it.length == 26 })
	}

	@Test
	fun `regex - empty pattern is nothing to do, Replace shows its fields, bad replacement & timeout mark fields`() {
		val view = navigate("regex-tester?input=a1b2", RegexView::class.java)
		assertEquals("", view.matches.value)
		assertFalse(view.input.isInvalid || view.pattern.isInvalid)
		assertFalse(view.replacement.isVisible || view.replaced.isVisible)
		test(view.pattern).setValue("(\\d)")
		assertTrue(view.matches.value.startsWith("2 matches"))
		test(view.replace).click()
		assertTrue(view.replacement.isVisible && view.replaced.isVisible)
		assertEquals("ab", view.replaced.value)
		test(view.replacement).setValue("$2")
		assertTrue(view.replacement.isInvalid)
		assertEquals("", view.replaced.value)
		assertTrue(view.matches.value.startsWith("2 matches"))
		test(view.input).setValue("a,".repeat(30))
		test(view.pattern).setValue("(.*,){15}x")
		assertEquals(RegexTester.STOPPED, view.pattern.errorMessage)
		assertEquals("", view.matches.value)
	}
}
