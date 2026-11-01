package eu.zeletrik.devcore.tools.chmod

private const val OCTAL = 8

/** One `rwx` triad each for owner, group and others: nine permission bits, owner read highest. */
private const val TRIAD = "rwx"
private const val PERMISSION_BITS = 9
private const val ALL_PERMISSIONS = 0x1FF
private const val SETUID = 0x800

/** A Unix mode, 0–7777 octal: special bits (setuid 4, setgid 2, sticky 1), then owner, group and others. */
object Permissions {
	const val INVALID = "Not a permission mode (use octal like 755 or symbolic like rwxr-xr-x)"
	private val octal = Regex("0?([0-7]{3,4})")
	// `ls -l` may add one marker: `.` (SELinux context), `@` (macOS extended attributes) or `+` (ACL).
	private val symbolic = Regex("[-dlcbps]?([r-][w-][xsS-][r-][w-][xsS-][r-][w-][xtT-])[.@+]?")

	fun parse(text: String): Int {
		val value = text.trim()
		octal.matchEntire(value)?.let { return it.groupValues[1].toInt(OCTAL) }
		val chars = symbolic.matchEntire(value)?.groupValues?.get(1) ?: throw IllegalArgumentException(INVALID)
		var mode = 0
		chars.forEachIndexed { i, c ->
			val bit = bit(i)
			when (c) {
				'r', 'w', 'x' -> mode = mode or bit
				's', 't' -> mode = mode or bit or special(i)
				'S', 'T' -> mode = mode or special(i)
			}
		}
		return mode
	}

	private fun bit(position: Int) = 1 shl (PERMISSION_BITS - 1 - position)

	/** The special bit an execute position carries: setuid for the owner's, setgid for the group's, sticky for others'. */
	private fun special(position: Int) = SETUID shr (position / TRIAD.length)

	fun octal(mode: Int): String = if (mode > ALL_PERMISSIONS) "%04o".format(mode) else "%03o".format(mode)

	fun symbolic(mode: Int): String = buildString {
		for (i in 0 until PERMISSION_BITS) {
			val set = mode and bit(i) != 0
			val letter = TRIAD[i % TRIAD.length]
			val specialBit = if (letter == 'x') mode and special(i) != 0 else false
			append(
				when {
					specialBit && i == PERMISSION_BITS - 1 -> if (set) 't' else 'T'
					specialBit -> if (set) 's' else 'S'
					set -> letter
					else -> '-'
				},
			)
		}
	}
}
