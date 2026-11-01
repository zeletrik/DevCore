package eu.zeletrik.devcore.tools.color

import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CHANNEL_MAX = 255.0
private const val PERCENT = 100.0
private const val FULL_TURN = 360.0

/** The hue circle's six sectors (red, yellow, green, cyan, blue, magenta), 60° each. */
private const val SECTORS = 6
private const val SECTOR_DEGREES = FULL_TURN / SECTORS

/** Green is the largest channel in sectors 1–2 and blue in 3–4, so their hues start two and four sectors round. */
private const val GREEN_SECTOR = 2.0
private const val BLUE_SECTOR = 4.0

/** `rgb`, `hsl` and `hsv` take three values; `rgb` and `hsl` may add alpha as a fourth. `cmyk` takes four. */
private const val COMPONENTS = 3
private const val INKS = 4

/** `#rgb` and `#rgba`: one hex digit per channel. */
private const val SHORT_HEX_DIGITS = 4
private const val HEX = 16
private const val ALPHA_DECIMALS = 3

/** An sRGB color with 0–255 channels and alpha 0–1. */
data class Rgba(val r: Int, val g: Int, val b: Int, val a: Double = 1.0)

data class ColorNotations(
	val hex: String,
	val rgb: String,
	val hsl: String,
	val hsv: String,
	val cmyk: String,
	val name: String,
)

object Colors {
	const val INVALID = "Invalid color"

	/** The 148 CSS named colors. */
	private val named = mapOf(
		"aliceblue" to "#f0f8ff", "antiquewhite" to "#faebd7", "aqua" to "#00ffff", "aquamarine" to "#7fffd4",
		"azure" to "#f0ffff", "beige" to "#f5f5dc", "bisque" to "#ffe4c4", "black" to "#000000",
		"blanchedalmond" to "#ffebcd", "blue" to "#0000ff", "blueviolet" to "#8a2be2", "brown" to "#a52a2a",
		"burlywood" to "#deb887", "cadetblue" to "#5f9ea0", "chartreuse" to "#7fff00", "chocolate" to "#d2691e",
		"coral" to "#ff7f50", "cornflowerblue" to "#6495ed", "cornsilk" to "#fff8dc", "crimson" to "#dc143c",
		"cyan" to "#00ffff", "darkblue" to "#00008b", "darkcyan" to "#008b8b", "darkgoldenrod" to "#b8860b",
		"darkgray" to "#a9a9a9", "darkgreen" to "#006400", "darkgrey" to "#a9a9a9", "darkkhaki" to "#bdb76b",
		"darkmagenta" to "#8b008b", "darkolivegreen" to "#556b2f", "darkorange" to "#ff8c00", "darkorchid" to "#9932cc",
		"darkred" to "#8b0000", "darksalmon" to "#e9967a", "darkseagreen" to "#8fbc8f", "darkslateblue" to "#483d8b",
		"darkslategray" to "#2f4f4f", "darkslategrey" to "#2f4f4f", "darkturquoise" to "#00ced1", "darkviolet" to "#9400d3",
		"deeppink" to "#ff1493", "deepskyblue" to "#00bfff", "dimgray" to "#696969", "dimgrey" to "#696969",
		"dodgerblue" to "#1e90ff", "firebrick" to "#b22222", "floralwhite" to "#fffaf0", "forestgreen" to "#228b22",
		"fuchsia" to "#ff00ff", "gainsboro" to "#dcdcdc", "ghostwhite" to "#f8f8ff", "gold" to "#ffd700",
		"goldenrod" to "#daa520", "gray" to "#808080", "green" to "#008000", "greenyellow" to "#adff2f",
		"grey" to "#808080", "honeydew" to "#f0fff0", "hotpink" to "#ff69b4", "indianred" to "#cd5c5c",
		"indigo" to "#4b0082", "ivory" to "#fffff0", "khaki" to "#f0e68c", "lavender" to "#e6e6fa",
		"lavenderblush" to "#fff0f5", "lawngreen" to "#7cfc00", "lemonchiffon" to "#fffacd", "lightblue" to "#add8e6",
		"lightcoral" to "#f08080", "lightcyan" to "#e0ffff", "lightgoldenrodyellow" to "#fafad2", "lightgray" to "#d3d3d3",
		"lightgreen" to "#90ee90", "lightgrey" to "#d3d3d3", "lightpink" to "#ffb6c1", "lightsalmon" to "#ffa07a",
		"lightseagreen" to "#20b2aa",
		"lightskyblue" to "#87cefa",
		"lightslategray" to "#778899",
		"lightslategrey" to "#778899",
		"lightsteelblue" to "#b0c4de", "lightyellow" to "#ffffe0", "lime" to "#00ff00", "limegreen" to "#32cd32",
		"linen" to "#faf0e6", "magenta" to "#ff00ff", "maroon" to "#800000", "mediumaquamarine" to "#66cdaa",
		"mediumblue" to "#0000cd", "mediumorchid" to "#ba55d3", "mediumpurple" to "#9370db", "mediumseagreen" to "#3cb371",
		"mediumslateblue" to "#7b68ee",
		"mediumspringgreen" to "#00fa9a",
		"mediumturquoise" to "#48d1cc",
		"mediumvioletred" to "#c71585",
		"midnightblue" to "#191970", "mintcream" to "#f5fffa", "mistyrose" to "#ffe4e1", "moccasin" to "#ffe4b5",
		"navajowhite" to "#ffdead", "navy" to "#000080", "oldlace" to "#fdf5e6", "olive" to "#808000",
		"olivedrab" to "#6b8e23", "orange" to "#ffa500", "orangered" to "#ff4500", "orchid" to "#da70d6",
		"palegoldenrod" to "#eee8aa", "palegreen" to "#98fb98", "paleturquoise" to "#afeeee", "palevioletred" to "#db7093",
		"papayawhip" to "#ffefd5", "peachpuff" to "#ffdab9", "peru" to "#cd853f", "pink" to "#ffc0cb",
		"plum" to "#dda0dd", "powderblue" to "#b0e0e6", "purple" to "#800080", "rebeccapurple" to "#663399",
		"red" to "#ff0000", "rosybrown" to "#bc8f8f", "royalblue" to "#4169e1", "saddlebrown" to "#8b4513",
		"salmon" to "#fa8072", "sandybrown" to "#f4a460", "seagreen" to "#2e8b57", "seashell" to "#fff5ee",
		"sienna" to "#a0522d", "silver" to "#c0c0c0", "skyblue" to "#87ceeb", "slateblue" to "#6a5acd",
		"slategray" to "#708090", "slategrey" to "#708090", "snow" to "#fffafa", "springgreen" to "#00ff7f",
		"steelblue" to "#4682b4", "tan" to "#d2b48c", "teal" to "#008080", "thistle" to "#d8bfd8",
		"tomato" to "#ff6347", "turquoise" to "#40e0d0", "violet" to "#ee82ee", "wheat" to "#f5deb3",
		"white" to "#ffffff", "whitesmoke" to "#f5f5f5", "yellow" to "#ffff00", "yellowgreen" to "#9acd32",
	)
	private val hexForm = Regex("#?([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")
	private val function = Regex("(rgba?|hsla?|hsv|cmyk)\\((.*)\\)", RegexOption.IGNORE_CASE)

	fun parse(text: String): Rgba {
		val value = text.trim()
		val hex = hexForm.matchEntire(named[value.lowercase()] ?: value)
		return if (hex != null) fromHex(hex.groupValues[1]) else fromFunction(value)
	}

	private fun fromHex(written: String): Rgba {
		val digits = if (written.length <= SHORT_HEX_DIGITS) written.map { c -> "$c$c" }.joinToString("") else written
		val channels = digits.chunked(2).map { it.toInt(HEX) }
		val alpha = channels.getOrNull(COMPONENTS)?.let { it / CHANNEL_MAX } ?: 1.0
		return Rgba(channels[0], channels[1], channels[2], alpha)
	}

	private fun fromFunction(value: String): Rgba {
		val match = function.matchEntire(value) ?: throw IllegalArgumentException(INVALID)
		val name = match.groupValues[1].lowercase()
		val parts = match.groupValues[2].split(Regex("\\s*[,/]\\s*|\\s+")).filter { it.isNotBlank() }
		val takesAlpha = name in setOf("rgb", "rgba", "hsl", "hsla")
		val alpha = if (parts.size == COMPONENTS + 1 && takesAlpha) Components.alpha(parts.last()) else 1.0
		return with(Components) {
			when (name) {
				"rgb", "rgba" -> {
					require(parts.size in COMPONENTS..COMPONENTS + 1) { INVALID }
					Rgba(channel(parts[0]), channel(parts[1]), channel(parts[2]), alpha)
				}
				"hsl", "hsla" -> {
					require(parts.size in COMPONENTS..COMPONENTS + 1) { INVALID }
					ColorSpaces.fromHsl(hue(parts[0]), percent(parts[1]), percent(parts[2]), alpha)
				}
				"hsv" -> {
					require(parts.size == COMPONENTS) { INVALID }
					ColorSpaces.fromHsv(hue(parts[0]), percent(parts[1]), percent(parts[2]))
				}
				else -> {
					require(parts.size == INKS) { INVALID }
					val inks = parts.map(::percent)
					val k = inks.last()
					val (r, g, b) = inks.take(COMPONENTS).map { ((1 - it) * (1 - k) * CHANNEL_MAX).roundToInt() }
					Rgba(r, g, b)
				}
			}
		}
	}

	fun notations(color: Rgba): ColorNotations {
		val (r, g, b) = color
		val a = color.a
		val opaque = a >= 1.0
		val hex = cssHex(color) + if (opaque) "" else "%02x".format((a * CHANNEL_MAX).roundToInt())
		val (h, s, l) = ColorSpaces.toHsl(color)
		val (hv, sv, v) = ColorSpaces.toHsv(color)
		val k = 1 - max(r, max(g, b)) / CHANNEL_MAX
		fun ink(channel: Int) = if (k >= 1.0) 0 else ((1 - channel / CHANNEL_MAX - k) / (1 - k) * PERCENT).roundToInt()
		return ColorNotations(
			hex = hex,
			rgb = if (opaque) "rgb($r, $g, $b)" else "rgba($r, $g, $b, ${trim(a)})",
			hsl = "hsl($h, $s%, $l%)",
			hsv = "hsv($hv, $sv%, $v%)",
			cmyk = "cmyk(${ink(r)}%, ${ink(g)}%, ${ink(b)}%, ${(k * PERCENT).roundToInt()}%)",
			name = if (opaque) named.entries.firstOrNull { it.value == hex }?.key.orEmpty() else "",
		)
	}

	/** `#rrggbb` for the swatch and the picker, built from the parsed color, never from raw input. */
	fun cssHex(color: Rgba) = "#%02x%02x%02x".format(color.r, color.g, color.b)

	/** Alpha with at most 3 decimals (`0.502`), without trailing zeros. */
	private fun trim(value: Double) =
		value.toBigDecimal().setScale(ALPHA_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

/** The values inside `rgb()`, `hsl()`, `hsv()` and `cmyk()`, each checked against its range. */
private object Components {
	/** Plain decimal numbers only: `toDouble` would also take NaN, Infinity and hex floats. */
	private val decimal = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

	fun channel(part: String): Int {
		val value = if (part.endsWith("%")) {
			percent(part) * CHANNEL_MAX
		} else {
			number(part) ?: throw IllegalArgumentException(Colors.INVALID)
		}
		require(value in 0.0..CHANNEL_MAX) { Colors.INVALID }
		return value.roundToInt()
	}

	fun percent(part: String): Double {
		val value = number(part.removeSuffix("%")) ?: throw IllegalArgumentException(Colors.INVALID)
		require(value in 0.0..PERCENT) { Colors.INVALID }
		return value / PERCENT
	}

	fun alpha(part: String): Double {
		val value = if (part.endsWith("%")) percent(part) else number(part) ?: throw IllegalArgumentException(Colors.INVALID)
		require(value in 0.0..1.0) { Colors.INVALID }
		return value
	}

	fun hue(part: String): Double {
		val value = number(part.removeSuffix("deg")) ?: throw IllegalArgumentException(Colors.INVALID)
		return ((value % FULL_TURN) + FULL_TURN) % FULL_TURN
	}

	private fun number(text: String) = text.takeIf { decimal.matches(it) }?.toDouble()
}

/** Conversions between sRGB and the hue-based spaces. */
private object ColorSpaces {
	fun fromHsl(h: Double, s: Double, l: Double, a: Double): Rgba {
		val c = (1 - abs(2 * l - 1)) * s
		return fromChroma(h, c, l - c / 2, a)
	}

	fun fromHsv(h: Double, s: Double, v: Double): Rgba {
		val c = v * s
		return fromChroma(h, c, v - c, 1.0)
	}

	private fun fromChroma(h: Double, c: Double, m: Double, a: Double): Rgba {
		val x = c * (1 - abs((h / SECTOR_DEGREES) % 2 - 1))
		val bySector = listOf(
			Triple(c, x, 0.0), Triple(x, c, 0.0), Triple(0.0, c, x), Triple(0.0, x, c), Triple(x, 0.0, c), Triple(c, 0.0, x),
		)
		val (r, g, b) = bySector[(h / SECTOR_DEGREES).toInt().coerceIn(0, SECTORS - 1)]
		fun scaled(value: Double) = ((value + m) * CHANNEL_MAX).roundToInt()
		return Rgba(scaled(r), scaled(g), scaled(b), a)
	}

	private fun hueOf(r: Double, g: Double, b: Double, max: Double, delta: Double): Int {
		if (delta == 0.0) return 0
		val sector = when (max) {
			r -> ((g - b) / delta) % SECTORS
			g -> (b - r) / delta + GREEN_SECTOR
			else -> (r - g) / delta + BLUE_SECTOR
		}
		val h = sector * SECTOR_DEGREES
		return ((h + FULL_TURN) % FULL_TURN).roundToInt() % FULL_TURN.toInt()
	}

	fun toHsl(c: Rgba): Triple<Int, Int, Int> {
		val (r, g, b) = listOf(c.r, c.g, c.b).map { it / CHANNEL_MAX }
		val hi = max(r, max(g, b))
		val lo = min(r, min(g, b))
		val l = (hi + lo) / 2
		val delta = hi - lo
		val s = if (delta == 0.0) 0.0 else delta / (1 - abs(2 * l - 1))
		return Triple(hueOf(r, g, b, hi, delta), (s * PERCENT).roundToInt(), (l * PERCENT).roundToInt())
	}

	fun toHsv(c: Rgba): Triple<Int, Int, Int> {
		val (r, g, b) = listOf(c.r, c.g, c.b).map { it / CHANNEL_MAX }
		val hi = max(r, max(g, b))
		val delta = hi - min(r, min(g, b))
		val s = if (hi == 0.0) 0.0 else delta / hi
		return Triple(hueOf(r, g, b, hi, delta), (s * PERCENT).roundToInt(), (hi * PERCENT).roundToInt())
	}
}
