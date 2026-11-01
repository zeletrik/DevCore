package eu.zeletrik.devcore.tools.hashing

import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.textfield.PasswordField
import com.vaadin.flow.router.Route
import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import eu.zeletrik.devcore.shell.MainLayout
import eu.zeletrik.devcore.shell.OutputField
import eu.zeletrik.devcore.shell.TextToolView
import eu.zeletrik.devcore.shell.Tool
import eu.zeletrik.devcore.shell.boundedInt
import eu.zeletrik.devcore.shell.choice
import eu.zeletrik.devcore.shell.numberField
import eu.zeletrik.devcore.shell.optionsRow
import eu.zeletrik.devcore.shell.secretField
import eu.zeletrik.devcore.shell.textLine

/** Hash a password, or verify one against a hash. */
enum class HashMode(val label: String) {
	HASH("Hash"), VERIFY("Verify");

	companion object {
		fun fromParam(value: String?) = entries.firstOrNull { it.name.lowercase() == value } ?: HASH
	}
}

/** The password never comes from the URL: no deep-link input, a masked field. */
@Route("password-hashing", layout = MainLayout::class)
@SpringComponent
@UIScope
class PasswordHashingView : TextToolView<PasswordField>(Tool.PASSWORD_HASHING, secretField("Password")) {
	internal val mode = choice("Mode", HashMode.entries, HashMode.HASH, HashMode::label) { showFields(); recompute() }
	internal val algorithm =
		choice("Algorithm", HashAlgorithm.entries, HashAlgorithm.BCRYPT, HashAlgorithm::label) { showFields(); recompute() }
	internal val cost = numberField(
		"Cost",
		PasswordHashes.bcryptCosts,
		10,
	).apply { addValueChangeListener { recompute() } }
	internal val memory = numberField(
		"Memory (KiB)",
		8192..PasswordHashes.MAX_ARGON2_MEMORY_KIB,
		19_456,
	).apply { addValueChangeListener { recompute() } }
	internal val iterations = numberField(
		"Iterations",
		1..PasswordHashes.MAX_ARGON2_ITERATIONS,
		2,
	).apply { addValueChangeListener { recompute() } }
	internal val hash = field(textLine("Hash"), "hash")
	internal val output = OutputField("Hash")
	internal val result = Span()
	private val options = optionsRow(algorithm, cost, memory, iterations)

	init {
		add(mode, options, input, hash, output, result)
		showFields()
	}

	private fun showFields() {
		val hashing = mode.value == HashMode.HASH
		options.isVisible = hashing
		cost.isVisible = algorithm.value == HashAlgorithm.BCRYPT
		memory.isVisible = algorithm.value == HashAlgorithm.ARGON2ID
		iterations.isVisible = algorithm.value == HashAlgorithm.ARGON2ID
		hash.isVisible = !hashing
		output.isVisible = hashing
		result.isVisible = !hashing
	}

	override fun acceptsInputParam() = false

	override fun applyMode(mode: String?) {
		this.mode.value = HashMode.fromParam(mode)
	}

	override fun isEmpty() = input.value.isEmpty() || (mode.value == HashMode.VERIFY && hash.value.isBlank())

	override fun render(text: String) {
		if (mode.value == HashMode.VERIFY) {
			result.text = if (PasswordHashes.verify(text, hash.value)) "Match" else "No match"
		} else {
			val hashed = hash(text)
			if (hashed == null) keepOutputUnlessInputsChanged() else output.value = hashed
		}
	}

	/** A new hash of [text], or null while a cost field is invalid (every invalid one is marked). */
	private fun hash(text: String): String? = when (algorithm.value) {
		HashAlgorithm.BCRYPT -> boundedInt(cost)?.let { PasswordHashes.bcrypt(text, it) }
		else -> {
			val m = boundedInt(memory)
			val t = boundedInt(iterations)
			if (m != null && t != null) PasswordHashes.argon2id(text, m, t) else null
		}
	}

	override fun clearOutputs() {
		output.clear()
		result.text = ""
	}
}
