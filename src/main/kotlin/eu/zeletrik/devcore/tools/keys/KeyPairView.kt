package eu.zeletrik.devcore.tools.keys

import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.GeneratorView
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.markInvalid
import eu.zeletrik.devcore.shell.optionsRow
import eu.zeletrik.devcore.shell.textLine
import java.security.KeyPair

/** OpenSSH comments are free text; the cap only keeps the output line bounded. */
private const val MAX_COMMENT = 1_000

/** Keys are generated on the DevCore server and live only in this UI session. */
@Route("key-pair-generator", layout = MainLayout::class)
@SpringComponent
@UIScope
class KeyPairView : GeneratorView(Tool.KEY_PAIR) {
	internal val algorithm =
		choice("Algorithm", KeyAlgorithm.entries, KeyAlgorithm.ED25519, KeyAlgorithm::label) { generate() }
	internal val comment = textLine("Comment").apply { addValueChangeListener { showOpenSsh() } }
	internal val privateKey = OutputField("Private key (PKCS#8 PEM)")
	internal val publicKey = OutputField("Public key (SPKI PEM)")
	internal val openSsh = OutputField("OpenSSH public key")
	private var pair: KeyPair? = null

	init {
		add(optionsRow(algorithm, comment, generateButton, generateError), privateKey, publicKey, openSsh)
	}

	override fun produce() {
		if (!commentFits()) return
		val generated = KeyPairs.generate(algorithm.value)
		pair = generated
		privateKey.value = KeyPairs.privatePem(generated)
		publicKey.value = KeyPairs.publicPem(generated)
		showOpenSsh()
	}

	private fun showOpenSsh() {
		if (commentFits()) pair?.let { openSsh.value = KeyPairs.openSsh(it, comment.value) }
	}

	/** A comment longer than [MAX_COMMENT] is marked, and the keys and OpenSSH line stay as they are. */
	private fun commentFits(): Boolean {
		comment.isInvalid = false
		val fits = comment.value.length <= MAX_COMMENT
		if (!fits) comment.markInvalid("At most 1,000 characters")
		return fits
	}
}
