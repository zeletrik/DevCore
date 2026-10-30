package eu.zeletrik.devcore.tools.chmod

/** A Unix mode, 0–7777 octal: special bits (setuid 4, setgid 2, sticky 1), then owner, group and others. */
object Permissions {
	const val INVALID = "Not a permission mode (use octal like 755 or symbolic like rwxr-xr-x)"
	private val octal = Regex("0?([0-7]{3,4})")
	private val symbolic = Regex("[-dlcbps]?([r-][w-][xsS-][r-][w-][xsS-][r-][w-][xtT-])")

	fun parse(text: String): Int {
		val value = text.trim()
		octal.matchEntire(value)?.let { return it.groupValues[1].toInt(8) }
		val chars = symbolic.matchEntire(value)?.groupValues?.get(1) ?: throw IllegalArgumentException(INVALID)
		var mode = 0
		chars.forEachIndexed { i, c ->
			val bit = 1 shl (8 - i)
			when (c) {
				'r', 'w', 'x' -> mode = mode or bit
				's', 't' -> mode = mode or bit or special(i)
				'S', 'T' -> mode = mode or special(i)
			}
		}
		return mode
	}

	private fun special(position: Int) = when (position) {
		2 -> 0x800
		5 -> 0x400
		else -> 0x200
	}

	fun octal(mode: Int): String = if (mode > 0x1FF) "%04o".format(mode) else "%03o".format(mode)

	fun symbolic(mode: Int): String = buildString {
		for (i in 0 until 9) {
			val set = mode and (1 shl (8 - i)) != 0
			val letter = "rwx"[i % 3]
			val specialBit = if (letter == 'x') mode and special(i) != 0 else false
			append(
				when {
					specialBit && i == 8 -> if (set) 't' else 'T'
					specialBit -> if (set) 's' else 'S'
					set -> letter
					else -> '-'
				},
			)
		}
	}
}
