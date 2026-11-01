package eu.zeletrik.devcore.shell

import java.io.Writer

const val MAX_OUTPUT_CHARS = 5_000_000

/** Thrown as soon as an output would pass [MAX_OUTPUT_CHARS]; the page shows it like an invalid input. */
class OutputTooLarge : IllegalArgumentException("Output is too large (max 5,000,000 characters)")

/** Collects text and refuses to grow past [MAX_OUTPUT_CHARS], so an exploding output fails early, not out of memory. */
class BoundedWriter : Writer() {
	private val buffer = StringBuilder()

	override fun write(cbuf: CharArray, off: Int, len: Int) {
		ensureRoom(len)
		buffer.appendRange(cbuf, off, off + len)
	}

	override fun write(str: String, off: Int, len: Int) {
		ensureRoom(len)
		buffer.append(str, off, off + len)
	}

	override fun flush() = Unit

	override fun close() = Unit

	override fun toString() = buffer.toString()

	private fun ensureRoom(extra: Int) {
		if (buffer.length.toLong() + extra > MAX_OUTPUT_CHARS) throw OutputTooLarge()
	}
}
