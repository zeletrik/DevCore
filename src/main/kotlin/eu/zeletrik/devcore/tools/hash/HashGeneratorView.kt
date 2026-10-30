package eu.zeletrik.devcore.tools.hash

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textArea
import eu.zeletrik.devcore.shell.textLine

@Route("hash-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class HashGeneratorView : TextToolView<TextArea>(Tool.HASH, textArea("Text")) {
	internal val uppercase = Checkbox("Uppercase").apply { addValueChangeListener { recompute() } }
	internal val key = field(textLine("HMAC key"), "key").apply { helperText = "Leave empty for plain digests" }
	internal val outputs = Hashes.ALGORITHMS.associateWith { OutputField(it) }

	init {
		add(uppercase, key, input)
		outputs.values.forEach(::add)
	}

	override fun render(text: String) {
		val hmac = key.value.isNotEmpty()
		outputs.forEach { (algorithm, field) ->
			field.relabel(if (hmac) "HMAC-$algorithm" else algorithm)
			field.value = if (hmac) Hashes.hmac(text, key.value, algorithm, uppercase.value) else Hashes.digest(text, algorithm, uppercase.value)
		}
	}

	override fun clearOutputs() = outputs.values.forEach { it.clear() }
}
