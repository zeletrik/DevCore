package eu.zeletrik.devcore.tools.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** CommonMark plus GitHub tables, strikethrough and autolinks, as HTML source text; raw HTML passes through as text. */
object MarkdownToHtml {
	private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create())
	private val parser = Parser.builder().extensions(extensions).build()
	private val renderer = HtmlRenderer.builder().extensions(extensions).build()

	fun convert(markdown: String): String = renderer.render(parser.parse(markdown)).trimEnd('\n')
}
