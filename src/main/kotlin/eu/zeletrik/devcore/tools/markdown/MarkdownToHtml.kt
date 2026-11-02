package eu.zeletrik.devcore.tools.markdown

import eu.zeletrik.devcore.shell.BoundedWriter
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** CommonMark plus GitHub tables, strikethrough and autolinks, as HTML source text; raw HTML passes through as text. */
object MarkdownToHtml {
	/**
	 * CommonMark case-folds every bracketed text to look it up as a link label, and the JDK's case folding is quadratic
	 * on text dense in ß or İ; at this size one bracket costs well under a second.
	 */
	private const val MAX_CHARS = 100_000
	private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create())
	private val parser = Parser.builder().extensions(extensions).build()
	private val renderer = HtmlRenderer.builder().extensions(extensions).build()

	/**
	 * Rendered into a [BoundedWriter]: every use of a reference link repeats its destination, so the HTML can far
	 * outgrow the input and must stop at the output cap while it is written.
	 */
	fun convert(markdown: String): String {
		require(markdown.length <= MAX_CHARS) { "Input is too large for Markdown to HTML (max 100,000 characters)" }
		val html = BoundedWriter()
		renderer.render(parser.parse(markdown), html)
		return html.toString().trimEnd('\n')
	}
}
