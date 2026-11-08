package eu.zeletrik.devcore.tools.xml

import eu.zeletrik.devcore.shell.BoundedWriter
import eu.zeletrik.devcore.tools.json.BYTE_ORDER_MARK
import eu.zeletrik.devcore.tools.json.Indent
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.CDATA
import javax.xml.stream.XMLStreamConstants.CHARACTERS
import javax.xml.stream.XMLStreamConstants.COMMENT
import javax.xml.stream.XMLStreamConstants.DTD
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION
import javax.xml.stream.XMLStreamConstants.SPACE
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/** Streams the document with DTDs off, so no entity is ever resolved and nothing outside the input is read. */
object XmlFormatter {
	private val factory = XMLInputFactory.newFactory().apply {
		setProperty(XMLInputFactory.SUPPORT_DTD, false)
		setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
		setProperty(XMLInputFactory.IS_COALESCING, false)
		// The JDK's parser reports CDATA as plain characters unless asked otherwise.
		runCatching { setProperty("http://java.sun.com/xml/stream/properties/report-cdata-event", true) }
	}

	private sealed interface Node

	/** An element with its tag and its attribute markup already rendered and escaped (` xmlns="…" a="b"`, or empty). */
	private class Element(
		val tag: String,
		val attributes: String,
		val children: MutableList<Node> = mutableListOf(),
	) : Node

	/** Already-escaped markup; [inline] for text and CDATA, which may share a line with their element. */
	private class Leaf(val markup: String, val inline: Boolean, val blank: Boolean = false) : Node

	fun format(text: String, indent: Indent = Indent.TWO): String {
		val nodes = try {
			TreeBuilder(factory.createXMLStreamReader(StringReader(text.removePrefix(BYTE_ORDER_MARK)))).build()
		} catch (e: XMLStreamException) {
			val reason = e.message?.substringAfter("Message: ")?.trim() ?: "not well-formed"
			throw IllegalArgumentException(
				e.location?.let {
					"Invalid XML at line ${it.lineNumber}, column ${it.columnNumber}: $reason"
				} ?: "Invalid XML: $reason",
				e,
			)
		}
		val pad = if (indent == Indent.MINIFIED) null else " ".repeat(indent.spaces)
		val out = BoundedWriter()
		nodes.forEachIndexed { i, node ->
			if (i > 0 && pad != null) out.append("\n")
			render(node, pad, 0, out)
		}
		return out.toString()
	}

	/** Builds the node tree from the reader's events. */
	private class TreeBuilder(private val reader: XMLStreamReader) {
		private val top = mutableListOf<Node>()
		private val open = ArrayDeque<Element>()

		// The parser splits text at every entity and character reference; the pieces are joined before deciding anything.
		private val text = StringBuilder()

		fun build(): List<Node> {
			declaration()?.let(top::add)
			while (reader.hasNext()) {
				val event = reader.next()
				if (event == CHARACTERS || event == SPACE) {
					text.append(reader.text)
				} else {
					flushText()
					handle(event)
				}
			}
			top.removeAll { it is Leaf && it.blank }
			return top
		}

		private fun declaration(): Leaf? = reader.version?.let { version ->
			val encoding = reader.characterEncodingScheme?.let { " encoding=\"$it\"" }.orEmpty()
			val standalone =
				if (reader.standaloneSet()) " standalone=\"${if (reader.isStandalone) "yes" else "no"}\"" else ""
			Leaf("<?xml version=\"$version\"$encoding$standalone?>", inline = false)
		}

		private fun add(node: Node) = (open.lastOrNull()?.children ?: top).add(node)

		private fun flushText() {
			if (text.isEmpty()) return
			add(Leaf(escape(text.toString(), attribute = false), inline = true, blank = text.isBlank()))
			text.clear()
		}

		private fun handle(event: Int) {
			when (event) {
				DTD -> throw IllegalArgumentException("DTDs aren't supported")
				START_ELEMENT -> Element(
					name(reader.prefix, reader.localName),
					attributes()
				).also { add(it); open.addLast(it) }
				// Whitespace-only text is layout only between child elements; on its own (`<sep> </sep>`) it is the value.
				END_ELEMENT -> open.removeLast().children.let { children ->
					if (children.any { it is Element }) children.removeAll { it is Leaf && it.blank }
				}

				CDATA -> add(Leaf("<![CDATA[${reader.text}]]>", inline = true))
				COMMENT -> add(Leaf("<!--${reader.text}-->", inline = false))
				PROCESSING_INSTRUCTION -> {
					// The JDK reports a PI without data as empty data, not as none.
					val data = reader.piData?.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
					add(Leaf("<?${reader.piTarget}$data?>", inline = false))
				}
			}
		}

		/** Namespace declarations first, then attributes in document order. */
		private fun attributes() = buildString {
			for (i in 0 until reader.namespaceCount) {
				val prefix = reader.getNamespacePrefix(i)
				append(" xmlns").append(if (prefix.isNullOrEmpty()) "" else ":$prefix")
				append("=\"").append(escape(reader.getNamespaceURI(i), attribute = true)).append('"')
			}
			for (i in 0 until reader.attributeCount) {
				append(' ').append(name(reader.getAttributePrefix(i), reader.getAttributeLocalName(i)))
				append("=\"").append(escape(reader.getAttributeValue(i), attribute = true)).append('"')
			}
		}
	}

	private fun name(prefix: String?, local: String) = if (prefix.isNullOrEmpty()) local else "$prefix:$local"

	/** Writes [node] into [out], which enforces the output cap while the text grows. */
	private fun render(node: Node, pad: String?, depth: Int, out: Appendable) {
		val indent = pad?.repeat(depth).orEmpty()
		when (node) {
			is Leaf -> out.append(indent).append(node.markup)
			is Element -> when {
				node.children.isEmpty() -> out.append(indent).append('<').append(node.tag).append(node.attributes)
					.append("/>")

				node.children.all { it is Leaf && it.inline } -> {
					out.append(indent).append('<').append(node.tag).append(node.attributes).append('>')
					node.children.forEach { out.append((it as Leaf).markup) }
					out.append("</").append(node.tag).append('>')
				}

				else -> {
					val newline = if (pad == null) "" else "\n"
					out.append(indent).append('<').append(node.tag).append(node.attributes).append('>').append(newline)
					node.children.forEachIndexed { i, child ->
						if (i > 0) out.append(newline)
						render(child, pad, depth + 1, out)
					}
					out.append(newline).append(indent).append("</").append(node.tag).append('>')
				}
			}
		}
	}

	private fun escape(text: String, attribute: Boolean) = buildString {
		text.forEach { c ->
			when {
				c == '&' -> append("&amp;")
				c == '<' -> append("&lt;")
				c == '>' -> append("&gt;")
				c == '"' && attribute -> append("&quot;")
				// Parsers normalize raw whitespace inside attribute values, so these must stay character references.
				c == '\n' && attribute -> append("&#10;")
				c == '\r' && attribute -> append("&#13;")
				c == '\t' && attribute -> append("&#9;")
				else -> append(c)
			}
		}
	}
}
