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
import eu.zeletrik.devcore.shell.textLine
import eu.zeletrik.devcore.tools.codes.CodeImages

@Route("barcode-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class BarcodeView : TextToolView<TextField>(Tool.BARCODE, textLine("Value")) {
	internal val symbology = Select<Symbology>().apply {
		label = "Format"
		setItems(*Symbology.entries.toTypedArray())
		setItemLabelGenerator { it.label }
		value = Symbology.CODE_128
		addValueChangeListener { recompute() }
	}
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
