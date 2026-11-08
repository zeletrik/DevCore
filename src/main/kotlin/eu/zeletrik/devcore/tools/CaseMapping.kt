package eu.zeletrik.devcore.tools

/** The most characters case-mapped at once; the JDK's slowdown below stays within one piece (1M İ: about 0.1 s). */
private const val PIECE_CHARS = 256

/**
 * [String.lowercase] in linear time. The JDK regrows its result array for every character whose lower case is longer
 * than itself (İ → i̇), so text dense in them takes quadratic time: a million İ takes minutes. Mapping in pieces
 * keeps that cost per piece. Pieces end after whitespace, so context-dependent mappings (Greek final sigma) see whole
 * words.
 */
fun String.lowercaseLinear(): String = mapInPieces(String::lowercase)

/** [String.uppercase] in linear time, for the same reason as [lowercaseLinear] (ß → SS). */
fun String.uppercaseLinear(): String = mapInPieces(String::uppercase)

private fun String.mapInPieces(map: (String) -> String): String {
	if (length <= PIECE_CHARS) return map(this)
	val out = StringBuilder(length)
	var start = 0
	while (start < length) {
		val end = pieceEnd(start)
		out.append(map(substring(start, end)))
		start = end
	}
	return out.toString()
}

/** After the last whitespace within reach, else at the reach (never between the two halves of a surrogate pair). */
private fun String.pieceEnd(start: Int): Int {
	val reach = minOf(start + PIECE_CHARS, length)
	if (reach == length) return reach
	val space = (reach - 1 downTo start + 1).firstOrNull { this[it].isWhitespace() }
	return when {
		space != null -> space + 1
		this[reach - 1].isHighSurrogate() -> reach - 1
		else -> reach
	}
}
