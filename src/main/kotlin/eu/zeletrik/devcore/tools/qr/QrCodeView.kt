package eu.zeletrik.devcore.tools.qr

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.radiobutton.RadioButtonGroup
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.CodeImageView
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.textArea
import eu.zeletrik.devcore.shell.textLine
import eu.zeletrik.devcore.tools.FieldError
import eu.zeletrik.devcore.tools.codes.CodeImages

/** Text or a Wi-Fi payload; in Wi-Fi mode the SSID is the main input and QR errors land on it. */
@Route("qr-code-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class QrCodeView : TextToolView<TextArea>(Tool.QR, textArea("Text or URL")) {
	internal val wifiMode = RadioButtonGroup("Content", false, true).apply {
		setItemLabelGenerator { if (it) "Wi-Fi network" else "Text or URL" }
		value = false
		addValueChangeListener { showFields(); recompute() }
	}
	internal val level = choice("Error correction", ErrorCorrectionLevel.entries, ErrorCorrectionLevel.M, { it.name }) {
		recompute()
	}
	internal val ssid = field(textLine("SSID"), "ssid")
	internal val password = field(textLine("Password"), "password")
	internal val security =
		choice("Security", WifiSecurity.entries, WifiSecurity.WPA, WifiSecurity::label) { recompute() }
	internal val hidden = Checkbox("Hidden network").apply { addValueChangeListener { recompute() } }
	private val wifi = HorizontalLayout(ssid, password, security, hidden)
		.apply {
			defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE; isWrap = true; isVisible = false
		}
	internal val code = CodeImageView("qr-code")

	init {
		add(HorizontalLayout(wifiMode, level), input, wifi, code)
	}

	private fun showFields() {
		input.isVisible = !wifiMode.value
		wifi.isVisible = wifiMode.value
	}

	override fun isEmpty() = if (wifiMode.value) ssid.value.isEmpty() else input.value.isEmpty()

	override fun render(text: String) {
		val content =
			if (wifiMode.value) QrCodes.wifi(ssid.value, password.value, security.value, hidden.value) else text
		val matrix = try {
			QrCodes.encode(content, level.value)
		} catch (e: IllegalArgumentException) {
			throw if (wifiMode.value) FieldError("ssid", e.message!!) else e
		}
		code.show(CodeImages.qr(matrix))
	}

	override fun clearOutputs() = code.show(null)
}
