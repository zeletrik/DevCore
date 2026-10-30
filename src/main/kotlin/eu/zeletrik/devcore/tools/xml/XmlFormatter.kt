package eu.zeletrik.devcore.tools.xml

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

	private class Element(val tag: String, val attributes: String, val children: MutableList<Node> = mutableListOf()) : Node

	/** Already-escaped markup; [inline] for text and CDATA, which may share a line with their element. */
	private class Leaf(val markup: String, val inline: Boolean) : Node

	fun format(text: String, indent: Indent = Indent.TWO): String {
		val nodes = try {
			parse(factory.createXMLStreamReader(StringReader(text)))
		} catch (e: XMLStreamException) {
			val reason = e.message?.substringAfter("Message: ")?.trim() ?: "not well-formed"
			throw IllegalArgumentException(e.location?.let { "Invalid XML at line ${it.lineNumber}, column ${it.columnNumber}: $reason" } ?: "Invalid XML: $reason")
		}
		val pad = if (indent == Indent.MINIFIED) null else " ".repeat(indent.spaces)
		return nodes.joinToString(if (pad == null) "" else "\n") { render(it, pad, 0) }
	}

	private fun parse(reader: XMLStreamReader): List<Node> {
		val top = mutableListOf<Node>()
		reader.version?.let { version ->
			val encoding = reader.characterEncodingScheme?.let { " encoding=\"$it\"" }.orEmpty()
			val standalone = if (reader.standaloneSet()) " standalone=\"${if (reader.isStandalone) "yes" else "no"}\"" else ""
			top += Leaf("<?xml version=\"$version\"$encoding$standalone?>", inline = false)
		}
		val open = ArrayDeque<Element>()
		fun add(node: Node) = (open.lastOrNull()?.children ?: top).add(node)
		while (reader.hasNext()) {
			when (reader.next()) {
				DTD -> throw IllegalArgumentException("DTDs are not supported")
				START_ELEMENT -> Element(name(reader.prefix, reader.localName), attributes(reader)).also { add(it); open.addLast(it) }
				END_ELEMENT -> open.removeLast()
				CHARACTERS, SPACE -> if (!reader.isWhiteSpace) add(Leaf(escape(reader.text, attribute = false), inline = true))
				CDATA -> add(Leaf("<![CDATA[${reader.text}]]>", inline = true))
				COMMENT -> add(Leaf("<!--${reader.text}-->", inline = false))
				PROCESSING_INSTRUCTION -> add(Leaf("<?${reader.piTarget}${reader.piData?.let { " $it" }.orEmpty()}?>", inline = false))
			}
		}
		return top
	}

	/** Namespace declarations first, then attributes in document order. */
	private fun attributes(reader: XMLStreamReader) = buildString {
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

	private fun name(prefix: String?, local: String) = if (prefix.isNullOrEmpty()) local else "$prefix:$local"

	private fun render(node: Node, pad: String?, depth: Int): String {
		val indent = pad?.repeat(depth).orEmpty()
		return when (node) {
			is Leaf -> indent + node.markup
			is Element -> when {
				node.children.isEmpty() -> "$indent<${node.tag}${node.attributes}/>"
				node.children.all { it is Leaf && it.inline } ->
					"$indent<${node.tag}${node.attributes}>${node.children.joinToString("") { (it as Leaf).markup }}</${node.tag}>"
				else -> {
					val inner = node.children.joinToString(if (pad == null) "" else "\n") { render(it, pad, depth + 1) }
					val newline = if (pad == null) "" else "\n"
					"$indent<${node.tag}${node.attributes}>$newline$inner$newline$indent</${node.tag}>"
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
				else -> append(c)
			}
		}
	}
}
