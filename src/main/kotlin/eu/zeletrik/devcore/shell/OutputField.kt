package eu.zeletrik.devcore.shell

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.textfield.TextArea

/** Read-only, labelled output with a copy button that is disabled while empty. */
class OutputField(label: String) : TextArea(label) {
	internal val copy = Button(VaadinIcon.COPY.create()).apply {
		addThemeVariants(ButtonVariant.TERTIARY)
		setAriaLabel("Copy $label")
		isEnabled = false
	}

	init {
		isReadOnly = true
		setWidthFull()
		monospace()
		suffixComponent = copy
		addValueChangeListener { copy.isEnabled = it.value.isNotEmpty() }
		// NOTE: copy runs in the browser — Vaadin's Clipboard API needs a secure context, plain-HTTP LANs don't have one.
		copy.addAttachListener { copy.element.executeJs(COPY_JS, element) }
		copy.element.addEventListener("devcore-copied") { Notification.show("Copied") }
		copy.element.addEventListener("devcore-copy-failed") {
			Notification.show("Copy failed — select the text and copy it manually")
		}
	}

	/** Every tool output passes through here, so no output can pass the output cap unnoticed. */
	override fun setValue(value: String) {
		if (value.length > MAX_OUTPUT_CHARS) throw OutputTooLarge()
		super.setValue(value)
	}

	/** Changes the visible label and the copy button's accessible name together. */
	fun relabel(text: String) {
		label = text
		copy.setAriaLabel("Copy $text")
	}

	private companion object {
		const val COPY_JS = """
			const field = $0;
			if (this.__devcoreCopy) return;
			this.__devcoreCopy = true;
			this.addEventListener('click', async () => {
				const text = field.value || '';
				const done = ok => this.dispatchEvent(new CustomEvent(ok ? 'devcore-copied' : 'devcore-copy-failed'));
				try {
					await navigator.clipboard.writeText(text);
					return done(true);
				} catch (e) {}
				const area = document.createElement('textarea');
				area.value = text;
				area.setAttribute('readonly', '');
				area.style.position = 'fixed';
				area.style.opacity = '0';
				document.body.appendChild(area);
				area.select();
				let ok = false;
				try { ok = document.execCommand('copy'); } catch (e) {}
				area.remove();
				done(ok);
			});
		"""
	}
}
