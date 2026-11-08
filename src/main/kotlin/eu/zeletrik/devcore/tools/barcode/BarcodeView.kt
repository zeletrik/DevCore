package eu.zeletrik.devcore.tools.barcode

import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.CodeImageView
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.enumSelect
import eu.zeletrik.devcore.shell.textLine
import eu.zeletrik.devcore.tools.codes.CodeImages

/** Code 128, Code 39 or a GS1 symbology with its check digit, as an image with PNG and SVG downloads. */
@Route("barcode-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class BarcodeView : TextToolView<TextField>(Tool.BARCODE, textLine("Value")) {
	internal val symbology =
		enumSelect("Format", Symbology.entries, Symbology.CODE_128, Symbology::label) { recompute() }
	internal val encoded = OutputField("Encoded value")
	internal val code = CodeImageView("barcode")

	init {
		add(symbology, input, encoded, code)
	}

	override fun render(text: String) {
		val (value, matrix) = Barcodes.encode(text, symbology.value)
		encoded.value = value
		code.show(CodeImages.barcode(matrix))
	}

	override fun clearOutputs() {
		encoded.clear()
		code.show(null)
	}
}
