package eu.zeletrik.devcore.tools

import eu.zeletrik.devcore.tools.casing.Case
import eu.zeletrik.devcore.tools.casing.CaseConverter
import eu.zeletrik.devcore.tools.diff.TextDiff
import eu.zeletrik.devcore.tools.htmlentities.HtmlEntities
import eu.zeletrik.devcore.tools.lines.LineOptions
import eu.zeletrik.devcore.tools.lines.LineTools
import eu.zeletrik.devcore.tools.lines.Order
import eu.zeletrik.devcore.tools.morse.MorseCode
import eu.zeletrik.devcore.tools.nato.NatoAlphabet
import eu.zeletrik.devcore.tools.numberbase.InputBase
import eu.zeletrik.devcore.tools.numberbase.NumberBases
import eu.zeletrik.devcore.tools.stringescape.Flavor
import eu.zeletrik.devcore.tools.stringescape.StringEscapes
import eu.zeletrik.devcore.tools.subnet.Subnets
import eu.zeletrik.devcore.tools.textstats.TextStatistics
import eu.zeletrik.devcore.tools.unicode.UnicodeEscapes
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextFixesTest {
	private fun cp(vararg codePoints: Int) = codePoints.joinToString("") { String(Character.toChars(it)) }

	private val acute = cp(0x301)

	@Test
	fun `combining marks stay inside words`() {
		assertEquals("cafe${acute}_au_lait", CaseConverter.convert("cafe$acute au lait", Case.SNAKE))
		val hindi = cp(0x928, 0x92E, 0x938, 0x94D, 0x924, 0x947) + " " + cp(0x926, 0x941, 0x928, 0x93F, 0x92F, 0x93E)
		assertEquals(hindi.replace(' ', '-'), CaseConverter.convert(hindi, Case.KEBAB))
	}

	@Test
	fun `natural order treats only ASCII digits as numbers`() {
		val fullWidthOne = cp(0xFF11) + cp(0x756A)
		val two = "2" + cp(0x756A)
		assertEquals("$two\n$fullWidthOne", LineTools.apply("$fullWidthOne\n$two", LineOptions(order = Order.NATURAL)))
	}

	@Test
	fun `html numeric references follow the HTML standard`() {
		assertEquals(
			"a" + cp(0xFFFD) + "b" + cp(0x20AC) + "c" + cp(0x2013) + "d",
			HtmlEntities.decode("a&#0;b&#128;c&#150;d"),
		)
		assertEquals(cp(0xFFFD), HtmlEntities.decode("&#xD800;"))
		assertEquals(cp(0x81), HtmlEntities.decode("&#129;"))
	}

	@Test
	fun `supplementary letters make words`() {
		val bold = cp(0x1D407, 0x1D41E, 0x1D425, 0x1D425, 0x1D428)
		assertTrue("Words: 1" in TextStatistics.describe(bold).lines(), TextStatistics.describe(bold))
	}

	@Test
	fun `morse separators and whole-character errors`() {
		assertEquals("SO", MorseCode.decode("..." + cp(0xA0) + "---"))
		val emoji = cp(0x1F600)
		val error = runCatching { MorseCode.encode("a$emoji") }.exceptionOrNull()?.message
		assertEquals("No Morse code for '$emoji'", error)
	}

	@Test
	fun `nato tokens are grapheme clusters`() {
		val flag = cp(0x1F1ED, 0x1F1FA)
		assertEquals(flag, NatoAlphabet.spell(flag))
		assertEquals("e$acute", NatoAlphabet.spell("e$acute"))
	}

	@Test
	fun `a long U+ run decodes`() {
		assertEquals("A".repeat(4_000), UnicodeEscapes.decode("U+0041 ".repeat(4_000).trim()))
	}

	@Test
	fun `javascript line continuation`() {
		assertEquals("ab", StringEscapes.unescape("a\\\nb", Flavor.JAVASCRIPT))
		assertEquals("ab", StringEscapes.unescape("a\\\r\nb", Flavor.JAVASCRIPT))
		assertTrue(runCatching { StringEscapes.unescape("a\\\nb", Flavor.JSON) }.isFailure)
	}

	@Test
	fun `documentation ranges and limited broadcast`() {
		listOf("192.0.2.1", "198.51.100.7", "203.0.113.9").forEach {
			assertTrue("Type: Documentation" in Subnets.describe(it).lines(), it)
		}
		assertTrue("Type: Limited broadcast" in Subnets.describe("255.255.255.255").lines())
	}

	@Test
	fun `text diff with both ignore options on 10,000 lines`() {
		val original = (1..10_000).joinToString("\n") { "Line  $it of the ORIGINAL text" }
		val changed = (1..10_000).joinToString("\n") { "changed line $it, entirely different" }
		val diff = assertTimeoutPreemptively(Duration.ofSeconds(5)) {
			TextDiff.diff(original, changed, 3, ignoreCase = true, ignoreWhitespace = true)
		}
		assertTrue(diff.unified.contains("-Line  1 of the ORIGINAL text"))
	}

	@Test
	fun `ignore options print the lines as written`() {
		val diff = TextDiff.diff("A  b\nkeep\nold", "a b\nkeep\nnew", 3, ignoreCase = true, ignoreWhitespace = true)
		assertTrue("-old" in diff.unified.lines() && "+new" in diff.unified.lines(), diff.unified)
		assertTrue(" A  b" in diff.unified.lines(), diff.unified)
	}

	@Test
	fun `natural sort of long digit runs and number base at its cap are quick`() {
		val lines = (1..10).joinToString("\n") { it.toString().repeat(99_999).take(99_999) }
		assertTimeoutPreemptively(Duration.ofSeconds(1)) { LineTools.apply(lines, LineOptions(order = Order.NATURAL)) }
		assertTimeoutPreemptively(Duration.ofSeconds(1)) { NumberBases.convert("9".repeat(10_000), InputBase.DECIMAL, false) }
	}
}
