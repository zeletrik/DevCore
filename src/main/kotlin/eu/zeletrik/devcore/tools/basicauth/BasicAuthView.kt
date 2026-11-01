package eu.zeletrik.devcore.tools.basicauth

import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.Mode
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.modeGroup
import eu.zeletrik.devcore.shell.secretField
import eu.zeletrik.devcore.shell.textArea
import eu.zeletrik.devcore.shell.textLine

/** The password never comes from the URL: deep-link input only fills the header to decode. */
@Route("basic-auth", layout = MainLayout::class)
@SpringComponent
@UIScope
class BasicAuthView : TextToolView<TextArea>(Tool.BASIC_AUTH, textArea("Header or token")) {
	internal val mode = modeGroup().apply { addValueChangeListener { showFields(); recompute() } }
	internal val username = field(textLine("Username"), "username")
	internal val password = field(secretField("Password"), "password")
	internal val header = OutputField("Header")
	internal val decodedUsername = OutputField("Username")
	internal val decodedPassword = OutputField("Password")
	private val credentials = HorizontalLayout(username, password).apply {
		defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
	}

	init {
		add(mode, credentials, input, header, decodedUsername, decodedPassword)
		showFields()
	}

	private fun showFields() {
		val encoding = mode.value == Mode.ENCODE
		credentials.isVisible = encoding
		header.isVisible = encoding
		input.isVisible = !encoding
		decodedUsername.isVisible = !encoding
		decodedPassword.isVisible = !encoding
	}

	override fun applyMode(mode: String?) {
		this.mode.value = Mode.fromParam(mode)
	}

	/** Never from a link: a Basic header is the password, and links end up in history and proxy logs. */
	override fun acceptsInputParam() = false

	override fun isEmpty() =
		if (mode.value == Mode.ENCODE) username.value.isEmpty() && password.value.isEmpty() else input.value.isEmpty()

	override fun render(text: String) {
		if (mode.value == Mode.ENCODE) {
			header.value = BasicAuth.encode(username.value, password.value)
		} else {
			val (user, pass) = BasicAuth.decode(text)
			decodedUsername.value = user
			decodedPassword.value = pass
		}
	}

	override fun clearOutputs() = listOf(header, decodedUsername, decodedPassword).forEach { it.clear() }
}
