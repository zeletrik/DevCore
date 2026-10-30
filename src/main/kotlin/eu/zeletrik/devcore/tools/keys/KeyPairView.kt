package eu.zeletrik.devcore.tools.keys

import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.GeneratorView
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.textLine
import java.security.KeyPair

/** Keys are generated on the DevCore server and live only in this UI session. */
@Route("key-pair-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class KeyPairView : GeneratorView(Tool.KEY_PAIR) {
	internal val algorithm = Select<KeyAlgorithm>().apply {
		label = "Algorithm"
		setItems(*KeyAlgorithm.entries.toTypedArray())
		setItemLabelGenerator { it.label }
		value = KeyAlgorithm.ED25519
		addValueChangeListener { generate() }
	}
	internal val comment = textLine("Comment").apply { addValueChangeListener { showOpenSsh() } }
	internal val privateKey = OutputField("Private key (PKCS#8 PEM)")
	internal val publicKey = OutputField("Public key (SPKI PEM)")
	internal val openSsh = OutputField("OpenSSH public key")
	private var pair: KeyPair? = null

	init {
		add(
			HorizontalLayout(algorithm, comment, generateButton).apply { defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE; isWrap = true },
			privateKey,
			publicKey,
			openSsh,
		)
	}

	override fun produce() {
		val generated = KeyPairs.generate(algorithm.value)
		pair = generated
		privateKey.value = KeyPairs.privatePem(generated)
		publicKey.value = KeyPairs.publicPem(generated)
		showOpenSsh()
	}

	private fun showOpenSsh() {
		pair?.let { openSsh.value = KeyPairs.openSsh(it, comment.value) }
	}
}
