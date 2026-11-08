package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.html.AttachmentType
import com.vaadin.flow.component.html.Image
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.server.streams.DownloadHandler
import com.vaadin.flow.server.streams.DownloadResponse
import eu.zeletrik.devcore.tools.codes.CodeImage
import java.io.ByteArrayInputStream

/** Preview plus PNG and SVG downloads, served from DevCore's own origin as streams, never inlined as data: URLs. */
class CodeImageView(private val name: String) : VerticalLayout() {
	internal val image = Image().apply { setAlt(name) }
	internal val png = Anchor().apply { text = "Download PNG" }
	internal val svg = Anchor().apply { text = "Download SVG" }
	/** What is shown right now; null while hidden. */
	internal var current: CodeImage? = null
		private set

	init {
		isPadding = false
		add(image, HorizontalLayout(png, svg))
		show(null)
	}

	/** Replaces the preview and both downloads, or hides them when [code] is null (empty or invalid input). */
	fun show(code: CodeImage?) {
		current = code
		image.isVisible = code != null
		listOf(png, svg).forEach { it.isEnabled = code != null }
		if (code == null) return
		image.setSrc(handler(code.png, "$name.png", "image/png"))
		png.setHref(handler(code.png, "$name.png", "image/png"), AttachmentType.DOWNLOAD)
		svg.setHref(handler(code.svg.toByteArray(), "$name.svg", "image/svg+xml"), AttachmentType.DOWNLOAD)
	}

	private fun handler(bytes: ByteArray, file: String, type: String) =
		DownloadHandler.fromInputStream { DownloadResponse(ByteArrayInputStream(bytes), file, type, bytes.size.toLong()) }
}
