package eu.zeletrik.devcore.tools.dockercompose

import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.DeepWork
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.textArea

/** docker run commands to a Compose file and back; commands often carry secrets, so no input comes from the URL. */
@Route("docker-compose-converter", layout = MainLayout::class)
@SpringComponent
@UIScope
class DockerComposeView : TextToolView<TextArea>(Tool.DOCKER_COMPOSE, textArea("Input")) {
	internal val mode = choice("Mode", Direction.entries, Direction.TO_COMPOSE, Direction::label) { recompute() }
	internal val output = OutputField("Output")
	internal val notConverted = OutputField("Not converted")

	init {
		add(mode, input, output, notConverted)
	}

	override fun acceptsInputParam() = false

	override fun applyMode(mode: String?) {
		this.mode.value = Direction.fromParam(mode)
	}

	override fun render(text: String) {
		val direction = mode.value
		val conversion = DeepWork.run { DockerCompose.convert(text, direction) }
		output.value = conversion.text
		notConverted.value = conversion.notConverted
	}

	override fun clearOutputs() {
		output.clear()
		notConverted.clear()
	}
}
