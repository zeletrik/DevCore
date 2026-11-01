package eu.zeletrik.devcore.tools.json

import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonFormatterTest {
	private val mapper = JsonMapper()

	/** [text] as a JSON string literal, the way a producer stringifies a payload. */
	private fun quoted(text: String) = mapper.writeValueAsString(text)

	/** [text] as it reads inside a JSON string: escaped, without the surrounding quotes. */
	private fun escaped(text: String) = quoted(text).removeSurrounding("\"")

	private fun expanded(text: String) = JsonFormatter.format(text, Indent.MINIFIED, expand = true)

	private fun invalid(text: String) = assertFailsWith<IllegalArgumentException> { JsonFormatter.format(text) }.message!!

	@Test
	fun `number literals pass through unchanged`() {
		val text = "[12345678901234567890, 0.1000000000000000055511151231257827, 1e3, -0, 1.50]"
		assertEquals(
			"[12345678901234567890,0.1000000000000000055511151231257827,1e3,-0,1.50]",
			JsonFormatter.format(text, Indent.MINIFIED),
		)
	}

	@Test
	fun `member order and duplicate names are preserved`() {
		assertEquals("""{"b":1,"a":2,"b":3}""", JsonFormatter.format("""{"b":1, "a":2, "b":3}""", Indent.MINIFIED))
	}

	@Test
	fun `default layout matches JSON stringify with 2 spaces`() {
		assertEquals(
			"{\n  \"a\": [\n    1,\n    {}\n  ],\n  \"b\": \"x\"\n}",
			JsonFormatter.format("""{"a":[1,{}],"b":"x"}"""),
		)
	}

	@Test
	fun `4-space layout`() {
		assertEquals(
			"{\n    \"a\": [\n        1,\n        {}\n    ],\n    \"b\": \"x\"\n}",
			JsonFormatter.format("""{"a":[1,{}],"b":"x"}""", Indent.FOUR),
		)
	}

	@Test
	fun `minified output`() {
		assertEquals(
			"""{"a":[1,{}],"b":"x"}""",
			JsonFormatter.format("{\n  \"a\": [\n    1,\n    {}\n  ],\n  \"b\": \"x\"\n}", Indent.MINIFIED),
		)
	}

	@Test
	fun `string values are preserved`() {
		assertEquals("[\"é\\n\",\"\\\"q\\\"\",\"\\\\\"]", JsonFormatter.format("""["é\n", "\"q\"", "\\"]""", Indent.MINIFIED))
	}

	@Test
	fun `top-level scalars are valid`() {
		listOf("42", "\"x\"", "true", "null").forEach { assertEquals(it, JsonFormatter.format(it)) }
	}

	@Test
	fun `whitespace only is empty output`() {
		assertEquals("", JsonFormatter.format("  \n\t "))
	}

	@Test
	fun `syntax errors report line and column`() {
		assertTrue(invalid("{\n  \"a\": tru\n}").startsWith("Invalid JSON at line 2, column"))
	}

	@Test
	fun `non-standard JSON is rejected`() {
		listOf("{/* c */}", "[1,]", "{'a':1}", "{a:1}", "[NaN]").forEach {
			assertTrue(invalid(it).startsWith("Invalid JSON at line 1, column"), it)
		}
	}

	@Test
	fun `content after the value is rejected`() {
		listOf("{} {}", "{}x").forEach { assertTrue(invalid(it).startsWith("Invalid JSON at line 1, column"), it) }
	}

	@Test
	fun `expand - an SNS message inside an SQS body inside a Lambda event, numbers untouched`() {
		val s3 = """{"Records":[{"s3":{"object":{"key":"a.csv","size":12345678901234567890,"ratio":1.50}}}]}"""
		val topic = "arn:aws:sns:eu-west-1:123456789012:uploads"
		val sns = """{"Type":"Notification","TopicArn":"$topic","Message":${quoted(s3)}}"""
		val event = """{"Records":[{"messageId":"m1","body":${quoted(sns)}}]}"""
		assertEquals(
			"""{"Records":[{"messageId":"m1","body":{"Type":"Notification","TopicArn":"$topic",""" +
				""""Message":{"Records":[{"s3":{"object":{"key":"a.csv","size":12345678901234567890,"ratio":1.50}}}]}}}]}""",
			expanded(event),
		)
	}

	@Test
	fun `expand is off by default`() {
		val event = """{"body":${quoted("""{"a":1}""")}}"""
		assertEquals(event, JsonFormatter.format(event, Indent.MINIFIED))
	}

	@Test
	fun `expand - nested structures are indented like the rest`() {
		val text = """{"log":${quoted("""{"level":"info"}""" + "\n")},"stream":"stdout"}"""
		assertEquals(
			"""
			{
			  "log": {
			    "level": "info"
			  },
			  "stream": "stdout"
			}
			""".trimIndent(),
			JsonFormatter.format(text, Indent.TWO, expand = true),
		)
	}

	@Test
	fun `expand - strings that are not exactly one object or array stay strings`() {
		val text = """{"id":"123","flag":"true","none":"null","note":"[INFO] started","broken":"{not json}",""" +
			""""two":"{} {}","literal":${quoted(quoted("{}"))},"empty":""}"""
		assertEquals(text, expanded(text))
	}

	@Test
	fun `expand - the whole input as a string literal`() {
		assertEquals("""{"a":1}""", expanded(quoted("""{"a":1}""")))
	}

	@Test
	fun `expand - escaped text pasted without its quotes, once or twice`() {
		assertEquals("""{"a":[1,"x"]}""", expanded(escaped("""{"a":[1,"x"]}""")))
		assertEquals("""{"a":[1,"x"]}""", expanded(escaped(escaped("""{"a":[1,"x"]}"""))))
		assertTrue(invalid(escaped("""{"a":1}""")).startsWith("Invalid JSON at line 1, column"))
	}

	@Test
	fun `expand - invalid JSON still reports the first problem`() {
		val message = assertFailsWith<IllegalArgumentException> { expanded("[1,]") }.message!!
		assertEquals(invalid("[1,]"), message)
		assertTrue(
			assertFailsWith<IllegalArgumentException> { expanded("C:\\temp\\new") }.message!!.startsWith("Invalid JSON")
		)
	}
}
