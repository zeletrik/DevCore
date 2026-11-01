package eu.zeletrik.devcore.tools.certificate

import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.DeepWork
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea
import java.time.Instant

/** Decoding runs on [DeepWork]: Bouncy Castle's DER parser recurses once per nesting level. */
@Route("certificate-decoder", layout = MainLayout::class)
@SpringComponent
@UIScope
class CertificateView : TextToolView<TextArea>(Tool.CERTIFICATE, textArea("PEM certificates or request")) {
	internal val output = OutputField("Decoded")

	init {
		add(input, output)
	}

	override fun render(text: String) {
		val now = Instant.now()
		output.value = DeepWork.run { Certificates.decode(text, now) }
	}

	override fun clearOutputs() = output.clear()
}
