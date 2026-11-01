package eu.zeletrik.devcore.tools.jwt

import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.Mode
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.modeGroup
import eu.zeletrik.devcore.shell.optionsRow
import eu.zeletrik.devcore.shell.textArea
import eu.zeletrik.devcore.shell.textLine
import java.time.Instant

/** Decode (default) shows the token and checks HS* signatures with a secret; Encode signs a payload. */
@Route("jwt", layout = MainLayout::class)
@SpringComponent
@UIScope
class JwtView : TextToolView<TextArea>(Tool.JWT, textArea("JWT")) {
	private val texts = mutableMapOf<Mode, String>()
	internal val mode = modeGroup(decodeFirst = true).apply {
		value = Mode.DECODE
		addValueChangeListener {
			// Each mode keeps its own text, so a token never ends up parsed as a payload.
			texts[it.oldValue] = input.value
			showFields()
			input.value = texts[it.value].orEmpty()
			recompute()
		}
	}
	internal val algorithm =
		choice("Algorithm", JwtAlgorithm.entries, JwtAlgorithm.HS256, JwtAlgorithm::name) { recompute() }
	internal val secret = field(textLine("Secret"), "secret")
	internal val notice = Span().apply { isVisible = false }
	internal val status = OutputField("Status")
	internal val times = OutputField("Time claims")
	internal val header = OutputField("Header")
	internal val payload = OutputField("Payload")
	internal val signature = OutputField("Signature")
	internal val token = OutputField("Token")

	init {
		add(optionsRow(mode, algorithm), input, secret, notice, status, times, header, payload, signature, token)
		showFields()
	}

	private fun showFields() {
		val decoding = mode.value == Mode.DECODE
		input.label = if (decoding) "JWT" else "Payload"
		secret.helperText = if (decoding) "Optional: checks HS256, HS384 and HS512 signatures" else "Used to sign the token"
		algorithm.isVisible = !decoding
		token.isVisible = !decoding
		listOf(status, times, header, payload, signature).forEach { it.isVisible = decoding }
	}

	override fun applyMode(mode: String?) {
		this.mode.value = Mode.fromParam(mode, Mode.DECODE)
	}

	override fun render(text: String) {
		if (mode.value == Mode.ENCODE) {
			token.value = JwtSigner.sign(text, algorithm.value, secret.value)
			return
		}
		val jwt = JwtDecoder.decode(text, Instant.now())
		notice.text = if (secret.value.isEmpty()) {
			"Signature not verified — enter the secret to check it"
		} else {
			JwtSigner.check(text, secret.value)
		}
		notice.isVisible = true
		status.value = jwt.status
		times.value = jwt.times
		header.value = jwt.header
		payload.value = jwt.payload
		signature.value = jwt.signature
	}

	override fun clearOutputs() {
		notice.isVisible = false
		listOf(status, times, header, payload, signature, token).forEach { it.clear() }
	}
}
