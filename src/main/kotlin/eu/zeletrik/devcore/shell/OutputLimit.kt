package eu.zeletrik.devcore.shell

import java.io.Writer

const val MAX_OUTPUT_CHARS = 5_000_000

/** Thrown as soon as an output would pass [MAX_OUTPUT_CHARS]; the page shows it like an invalid input. */
class OutputTooLarge : IllegalArgumentException("Output is too large (max 5,000,000 characters)")

/** Collects text and refuses to grow past [limit], so an exploding output fails early instead of filling memory. */
class BoundedWriter(private val limit: Int = MAX_OUTPUT_CHARS) : Writer() {
	private val buffer = StringBuilder()

	val length get() = buffer.length

	override fun write(cbuf: CharArray, off: Int, len: Int) {
		ensureRoom(len)
		buffer.appendRange(cbuf, off, off + len)
	}

	override fun write(str: String, off: Int, len: Int) {
		ensureRoom(len)
		buffer.append(str, off, off + len)
	}

	override fun write(c: Int) {
		ensureRoom(1)
		buffer.append(c.toChar())
	}

	override fun append(csq: CharSequence?, start: Int, end: Int): BoundedWriter {
		val text = csq ?: "null"
		ensureRoom(end - start)
		buffer.append(text, start, end)
		return this
	}

	override fun flush() = Unit

	override fun close() = Unit

	override fun toString() = buffer.toString()

	private fun ensureRoom(extra: Int) {
		if (buffer.length.toLong() + extra > limit) throw OutputTooLarge()
	}
}
