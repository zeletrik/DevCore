package eu.zeletrik.devcore.tools.dockercompose

import eu.zeletrik.devcore.shell.BoundedWriter
import eu.zeletrik.devcore.tools.dataformat.DataFormats
import eu.zeletrik.devcore.tools.dataformat.Format
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode

private const val SECONDS_PER_MINUTE = 60
private val whitespace = Regex("\\s+")
private val duration = Regex("([0-9]+)([sm]?)")
private val upFront = setOf("container_name", "image", "command")
private val portFields = setOf("target", "published", "host_ip", "protocol")
private val healthTimings = setOf("interval", "timeout", "start_period", "start_interval", "retries")
private val networkOptions = mapOf("aliases" to "--network-alias", "ipv4_address" to "--ip", "ipv6_address" to "--ip6")
private val mountSpelling = buildMap { mountFields.forEach { (field, path) -> putIfAbsent(path, field) } }

/** Compose keys that fill several options, or nested ones. */
private val keyHandlers: Map<String, RunCommand.(JsonNode) -> Unit> = mapOf(
	"entrypoint" to { entrypoint(it) },
	"network_mode" to { mode ->
		val value = text(mode)?.takeUnless { it.startsWith("service:") }
		if (value == null) note("network_mode") else option("--network", value)
	},
	"networks" to { networks(it) },
	"ulimits" to { ulimits(it) },
	"healthcheck" to { healthcheck(it) },
	"logging" to { logging(it) },
	"stop_grace_period" to { stopTimeout(it) },
	"storage_opt" to { pairs("--storage-opt", it, "storage_opt") },
)

/** A Compose file as docker run commands, one for each service. */
internal object ComposeToRun {
	fun convert(text: String): Conversion {
		val root = read(text) ?: return Conversion("", "")
		val services = root.get("services")?.takeIf { it.isObject && !it.isEmpty }
		requireNotNull(services) { "The Compose file has no services" }
		val out = BoundedWriter()
		val notes = Lines()
		var first = true
		services.properties().forEach { (name, service) ->
			require(service.isObject) { "Service '$name' isn't a map" }
			val image = text(service.get("image"))
			if (image == null) {
				notes.add("$name: " + if (service.has("build")) "build (build and tag the image, then run it)" else "no image")
			} else {
				if (!first) out.write("\n\n")
				first = false
				RunCommand(name, service, out).finish(image).forEach { notes.add("$name: $it") }
			}
		}
		root.properties().forEach { (key, _) -> if (key != "services") notes.add("$key (top level)") }
		return Conversion(out.toString(), notes.toString())
	}

	private fun read(text: String): JsonNode? = try {
		DataFormats.read(text, Format.YAML)
	} catch (e: JacksonException) {
		throw DataFormats.invalid(e)
	}
}

/**
 * One service as a docker run command, written to [out] as it is read: `--name` first (the container name, or else
 * the service's), then an option for each key in the order written, then the image and the command. What docker run
 * can't take goes to the notes.
 */
private class RunCommand(name: String, private val service: JsonNode, private val out: BoundedWriter) {
	private val notes = mutableListOf<String>()

	/** Entrypoint words after the first, which docker run takes before the command. */
	var entrypointArgs = emptyList<String>()

	init {
		out.write("docker run")
		option("--name", text(service.get("container_name")) ?: name)
		service.properties().forEach { (key, value) ->
			val mapped = byKey[key]
			val handler = keyHandlers[key]
			when {
				key in upFront -> Unit
				mapped != null -> simple(mapped, value)
				handler != null -> handler(value)
				else -> note(key)
			}
		}
	}

	/** Ends the command with the image and the command's words, and returns what wasn't converted. */
	fun finish(image: String): List<String> {
		val command = service.get("command")
		val words = mutableListOf<String>()
		if (command != null && command.isArray) {
			command.forEachIndexed { i, word -> text(word)?.let { words += shellWord(it) } ?: note("command[$i]") }
		} else {
			// NOTE: a command written as one string goes out as written; Compose splits it without the shell's globs
			// and expansions, so splitting it here would take a shell-words parser.
			text(command)?.let { words += it }
		}
		out.write(" \\\n  ${shellWord(image)}")
		(entrypointArgs + words).forEach { out.write(" $it") }
		return notes
	}

	/** One option on a line of its own. */
	fun option(flag: String, value: String? = null) {
		out.write(" \\\n  ")
		out.write(shortSpelling[flag] ?: flag)
		if (value != null) out.write(" ${shellWord(value)}")
	}

	fun note(what: String) {
		notes += what
	}

	/** Each value of a map as `--flag key=value`, such as `--storage-opt size=10G`. */
	fun pairs(flag: String, map: JsonNode, path: String) {
		map.properties().forEach { (key, value) ->
			val text = text(value)
			if (text == null) note("$path.$key") else option(flag, "$key=$text")
		}
	}

	private fun simple(mapped: Mapped, value: JsonNode) {
		val text = text(value)
		when {
			mapped.shape == Shape.LIST -> list(mapped, value)
			mapped.shape == Shape.SWITCH -> if (text in TRUE) option(mapped.flag)
			text != null -> option(mapped.flag, text)
			else -> note(mapped.key)
		}
	}

	/** A list, a single value, or a map such as `environment: {KEY: value}`; a key without a value passes through. */
	private fun list(mapped: Mapped, value: JsonNode) {
		val separator = if (mapped.key == "extra_hosts") ":" else "="
		when {
			value.isArray -> value.forEachIndexed { i, item -> item(mapped, item, i) }
			value.isObject -> value.properties().forEach { (key, entry) ->
				val text = text(entry)
				when {
					entry.isNull -> option(mapped.flag, key)
					text != null -> option(mapped.flag, key + separator + text)
					else -> note("${mapped.key}.$key")
				}
			}

			else -> text(value)?.let { option(mapped.flag, it) } ?: note(mapped.key)
		}
	}

	/** A plain item as it is, and the long syntax of ports and volumes as `-p` and `--mount`. */
	private fun item(mapped: Mapped, item: JsonNode, index: Int) {
		val mount = item.isObject && mapped.key == "volumes"
		val written = when {
			mount -> mount(item)
			item.isObject && mapped.key == "ports" -> port(item)
			else -> text(item)
		}
		when {
			written == null -> note("${mapped.key}[$index]")
			mount -> option("--mount", written)
			else -> option(mapped.flag, written)
		}
	}
}

/** The first word as `--entrypoint`; the others go before the command, as docker run passes them on. */
private fun RunCommand.entrypoint(value: JsonNode) {
	// NOTE: an entrypoint written as one string is split at whitespace only, which holds unless it has quotes.
	val words = when {
		value.isArray -> value.values().map(::text)
		else -> text(value)?.split(whitespace)?.filter { it.isNotEmpty() }
	}
	val plain = words?.filterNotNull()?.takeIf { it.size == words.size }
	when {
		plain == null -> note("entrypoint")
		plain.isEmpty() -> option("--entrypoint", "")
		else -> {
			option("--entrypoint", plain.first())
			entrypointArgs = plain.drop(1).map(::shellWord)
		}
	}
}

/** A list or map of networks as `--network`; aliases and addresses only when there is one network to give them to. */
private fun RunCommand.networks(value: JsonNode) {
	if (value.isArray) {
		value.forEachIndexed { i, item -> text(item)?.let { option("--network", it) } ?: note("networks[$i]") }
		return
	}
	value.properties().forEach { (name, config) ->
		option("--network", name)
		config.properties().forEach { (key, field) ->
			val flag = networkOptions[key]?.takeIf { value.size() == 1 }
			val values = if (field.isArray) field.values().map(::text) else listOf(text(field))
			if (flag == null || null in values) note("networks.$name.$key") else values.forEach { option(flag, it) }
		}
	}
}

/** `nofile: {soft: 1024, hard: 2048}` as `--ulimit nofile=1024:2048`, and `nproc: 512` as `--ulimit nproc=512`. */
private fun RunCommand.ulimits(value: JsonNode) {
	value.properties().forEach { (name, limit) ->
		val single = text(limit)
		val soft = text(limit.get("soft"))
		val hard = text(limit.get("hard"))
		when {
			single != null -> option("--ulimit", "$name=$single")
			soft != null && hard != null && limit.size() == 2 -> option("--ulimit", "$name=$soft:$hard")
			else -> note("ulimits.$name")
		}
	}
}

/** The test and its timings as `--health-*` options; a test of `NONE`, or `disable`, turns the check off. */
private fun RunCommand.healthcheck(value: JsonNode) {
	value.properties().forEach { (key, field) ->
		val text = text(field)
		val test = if (key == "test") healthTest(field) else null
		when {
			key == "test" && text(field.get(0)) == "NONE" -> option("--no-healthcheck")
			test != null -> option("--health-cmd", test)
			key == "disable" -> if (text in TRUE) option("--no-healthcheck")
			key in healthTimings && text != null -> option("--health-${key.replace('_', '-')}", text)
			else -> note("healthcheck.$key")
		}
	}
}

/** A health test as the shell command `--health-cmd` runs: `CMD` words quoted and joined, `CMD-SHELL` as it is. */
private fun healthTest(test: JsonNode): String? {
	val all = if (test.isArray) test.values().map(::text) else listOf("CMD-SHELL", text(test))
	val words = all.filterNotNull().takeIf { it.size == all.size && it.size > 1 } ?: return null
	return when (words.first()) {
		"CMD-SHELL" -> words.drop(1).singleOrNull()
		"CMD" -> words.drop(1).joinToString(" ", transform = ::shellWord)
		else -> null
	}
}

private fun RunCommand.logging(value: JsonNode) {
	value.properties().forEach { (key, field) ->
		val text = text(field)
		when {
			key == "driver" && text != null -> option("--log-driver", text)
			key == "options" && field.isObject -> pairs("--log-opt", field, "logging.options")
			else -> note("logging.$key")
		}
	}
}

/** `stop_grace_period` (`30s`, `1m`, `45`) as `--stop-timeout`, which counts whole seconds. */
private fun RunCommand.stopTimeout(value: JsonNode) {
	val seconds = text(value)?.let(duration::matchEntire)?.destructured?.let { (amount, unit) ->
		amount.toLongOrNull()?.times(if (unit == "m") SECONDS_PER_MINUTE else 1)
	}
	if (seconds == null) note("stop_grace_period") else option("--stop-timeout", seconds.toString())
}

/** A port in Compose's long syntax as `-p [host_ip:][published:]target[/protocol]`; null if it has other fields. */
private fun port(item: JsonNode): String? {
	val fields = item.properties().associate { it.key to text(it.value) }
	val target = fields["target"]
	val host = fields["host_ip"]
	val published = fields["published"]
	val prefix = when {
		host != null -> "$host:${published.orEmpty()}:"
		published != null -> "$published:"
		else -> ""
	}
	val complete = target != null && fields.all { (key, value) -> key in portFields && value != null }
	return if (complete) prefix + target + fields["protocol"]?.let { "/$it" }.orEmpty() else null
}

/** A volume in Compose's long syntax as a `--mount` value; null if it has a field `--mount` has no name for. */
private fun mount(item: JsonNode): String? {
	val fields = item.properties().flatMap { (key, value) ->
		if (value.isObject) value.properties().map { "$key.${it.key}" to text(it.value) } else listOf(key to text(value))
	}
	val complete = fields.all { (path, value) -> path in mountSpelling && value != null }
	return fields
		.filter { (path, value) -> path !in mountSwitches || value in TRUE }
		.joinToString(",") { (path, value) ->
			val field = mountSpelling[path]
			if (path in mountSwitches) "$field" else "$field=$value"
		}
		.takeIf { complete }
}
