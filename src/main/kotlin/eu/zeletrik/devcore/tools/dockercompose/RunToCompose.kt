package eu.zeletrik.devcore.tools.dockercompose

import eu.zeletrik.devcore.tools.dataformat.DataFormats
import eu.zeletrik.devcore.tools.dataformat.Format
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

private val nodes = JsonNodeFactory.instance

/** Docker's rule for container and volume names; any other `-v` source is a path on the host. */
private val dockerName = Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]*")
private val notInName = Regex("[^a-zA-Z0-9_.-]")
private val decimal = Regex("-?[0-9]+(\\.[0-9]+)?")
private val networkModes = setOf("bridge", "default", "host", "none")
private val hints = mapOf(
	"--detach" to "docker compose up -d runs it in the background",
	"--rm" to "docker compose down removes the container",
)

/** Options that take no value; one written as `--rm=false` still gets one. */
private val switches = byFlag.values.filter { it.shape == Shape.SWITCH }.map { it.flag }.toSet() + setOf(
	"--detach",
	"--disable-content-trust",
	"--no-healthcheck",
	"--publish-all",
	"--quiet",
	"--rm",
	"--sig-proxy",
	"--use-api-socket",
)

/** Options of docker run that take a value and have no Compose key. */
private val unconverted = setOf(
	"--attach",
	"--blkio-weight",
	"--blkio-weight-device",
	"--cidfile",
	"--cpuset-mems",
	"--detach-keys",
	"--device-read-bps",
	"--device-read-iops",
	"--device-write-bps",
	"--device-write-iops",
	"--gpus",
	"--io-maxbandwidth",
	"--io-maxiops",
	"--kernel-memory",
	"--label-file",
	"--link-local-ip",
	"--volume-driver",
)

/** Options that fill a nested key, or more than one, by their long name. */
private val optionHandlers: Map<String, Service.(Given) -> Unit> = mapOf(
	"--name" to {
		containerName = it.value
		node.put("container_name", it.value)
	},
	"--entrypoint" to { node.putArray("entrypoint").apply { if (it.value.isNotEmpty()) add(it.value) } },
	"--mount" to { mount(it) },
	"--network" to { network(it) },
	"--network-alias" to { onNetwork(it) { withArrayProperty("aliases").add(it.value) } },
	"--ip" to { onNetwork(it) { put("ipv4_address", it.value) } },
	"--ip6" to { onNetwork(it) { put("ipv6_address", it.value) } },
	"--ulimit" to { ulimit(it) },
	"--stop-timeout" to {
		val seconds = it.value.toIntOrNull()
		if (seconds == null) note(it) else node.put("stop_grace_period", "${seconds}s")
	},
	"--health-cmd" to { nested("healthcheck").putArray("test").add("CMD-SHELL").add(it.value) },
	"--health-interval" to { nested("healthcheck").put("interval", it.value) },
	"--health-timeout" to { nested("healthcheck").put("timeout", it.value) },
	"--health-start-period" to { nested("healthcheck").put("start_period", it.value) },
	"--health-start-interval" to { nested("healthcheck").put("start_interval", it.value) },
	"--health-retries" to { putNumber(nested("healthcheck"), "retries", it.value) },
	"--no-healthcheck" to { if (it.value in TRUE) nested("healthcheck").put("disable", true) },
	"--log-driver" to { nested("logging").put("driver", it.value) },
	"--log-opt" to { keyed(it) { nested("logging").withObjectProperty("options") } },
	"--storage-opt" to { keyed(it) { nested("storage_opt") } },
)

private val known = byFlag.keys + optionHandlers.keys + switches + unconverted

/** The short spellings as shared strings, so that a long run of switches (`-dddd…`) doesn't make a string for each. */
private val shortSpellings = aliases.keys.filter { it.length == 2 }.associateBy { it[1] }

/** docker run commands as one Compose file, a service for each. */
internal object RunToCompose {
	fun convert(text: String): Conversion {
		val file = ComposeFile()
		ShellReader(text).commands().forEachIndexed { i, words -> file.add(RunParser(words, i + 1).parse()) }
		return file.result()
	}
}

/** An option as given: [spelling] as written (`-d`, `--net`), [flag] its long name, [value] `true` if bare. */
private class Given(val spelling: String, val flag: String, val value: String) {
	override fun toString() = when {
		flag !in switches -> "$spelling $value"
		value == "true" -> spelling
		else -> "$spelling=$value"
	}
}

/** A docker run command: its options in the order written, the image, and the command after the image. */
private class Run(val options: List<Given>, val image: String, val args: List<String>)

/** Reads a command's options as Docker's CLI does: `--name=value` or `--name value`, `-it`, and `-p80:80`. */
private class RunParser(private val words: List<String>, private val number: Int) {
	private val options = mutableListOf<Given>()
	private var i = 0

	fun parse(): Run {
		i = start()
		while (i < words.size && words[i].length > 1 && words[i].startsWith("-")) {
			val word = words[i++]
			if (word == "--") break
			if (word.startsWith("--")) long(word) else short(word)
		}
		require(i < words.size) { "Command $number has no image" }
		return Run(options, words[i], words.subList(i + 1, words.size))
	}

	/** The index after `docker run` or `docker container run`, past a leading `sudo`. */
	private fun start(): Int {
		val docker = if (words.first() == "sudo") 1 else 0
		val run = docker + if (words.getOrNull(docker + 1) == "container") 2 else 1
		require(words.getOrNull(docker) == "docker" && words.getOrNull(run) == "run") {
			"Command $number isn't a docker run command"
		}
		return run + 1
	}

	private fun long(word: String) = add(word.substringBefore('='), if ('=' in word) word.substringAfter('=') else null)

	/** `-it` is two switches; in `-p80:80` and `-p=80:80`, the rest of the word is the value. */
	private fun short(word: String) {
		for (j in 1 until word.length) {
			val spelling = shortSpellings[word[j]] ?: "-${word[j]}"
			if ((aliases[spelling] ?: spelling) !in switches) {
				add(spelling, word.substring(j + 1).removePrefix("=").ifEmpty { null })
				return
			}
			add(spelling, null)
		}
	}

	private fun add(spelling: String, inline: String?) {
		val flag = aliases[spelling] ?: spelling
		require(flag in known) { "Command $number: unknown option $spelling" }
		val value = when {
			flag !in switches -> inline ?: next(spelling)
			inline == null -> "true"
			else -> inline
		}
		require(flag !in switches || value in TRUE || value in FALSE) {
			"Command $number: $spelling=$value isn't true or false"
		}
		options += Given(spelling, flag, value)
	}

	private fun next(spelling: String): String {
		require(i < words.size) { "Command $number: $spelling needs a value" }
		return words[i++]
	}
}

/** The Compose file being built: a service per command, and the volumes and networks they name. */
private class ComposeFile {
	private val services = nodes.objectNode()
	private val volumes = nodes.objectNode()
	private val networks = nodes.objectNode()
	private val notes = Lines()
	private val taken = HashSet<String>()
	private val suffixes = HashMap<String, Int>()

	fun add(run: Run) {
		val service = Service(this, run.image)
		run.options.forEach(service::apply)
		if (run.args.isNotEmpty()) service.node.putArray("command").apply { run.args.forEach { add(it) } }
		val name = unique(service.containerName?.takeIf(dockerName::matches) ?: nameOf(run.image))
		services.set(name, service.node)
		service.notes.forEach { notes.add("$name: $it") }
	}

	/** A named volume keeps its own name, so Compose uses the volume docker run made instead of a new, prefixed one. */
	fun volume(name: String) {
		volumes.withObjectProperty(name).put("name", name)
	}

	/** docker run only joins networks that already exist, which Compose calls external. */
	fun network(name: String) {
		networks.withObjectProperty(name).put("external", true)
	}

	fun result(): Conversion {
		if (services.isEmpty) return Conversion("", "")
		val root = nodes.objectNode()
		root.set("services", services)
		if (!volumes.isEmpty) root.set("volumes", volumes)
		if (!networks.isEmpty) root.set("networks", networks)
		return Conversion(DataFormats.write(root, Format.YAML), notes.toString())
	}

	/** [base], or `base-2`, `base-3` and on while that is taken; each base remembers its last suffix. */
	private fun unique(base: String): String {
		var name = base
		var suffix = suffixes[base] ?: 1
		while (!taken.add(name)) name = "$base-${++suffix}"
		suffixes[base] = suffix
		return name
	}

	/** A service name from an image: `ghcr.io/zeletrik/devcore:latest` gives `devcore`. */
	private fun nameOf(image: String) = image.substringBefore('@').substringAfterLast('/').substringBefore(':')
		.replace(notInName, "-")
		.trimStart('-', '_', '.')
		.ifEmpty { "service" }
}

/**
 * One service, filled option by option in the order written. What Compose can't hold goes to [notes], each note once:
 * the same option given again says nothing new.
 */
private class Service(private val file: ComposeFile, image: String) {
	val node: ObjectNode = nodes.objectNode().put("image", image)
	val notes = LinkedHashSet<String>()
	var containerName: String? = null

	/** The named network that `--network-alias`, `--ip` and `--ip6` apply to: the last one given. */
	private var network: String? = null

	fun apply(given: Given) {
		val mapped = byFlag[given.flag]
		val handler = optionHandlers[given.flag]
		when {
			mapped != null -> put(mapped, given.value)
			handler != null -> handler(given)
			else -> note(given)
		}
	}

	fun note(given: Given) {
		notes += given.toString() + hints[given.flag]?.takeIf { given.value in TRUE }?.let { " ($it)" }.orEmpty()
	}

	fun nested(key: String): ObjectNode = node.withObjectProperty(key)

	/** [value] as a number where it reads as one, so `1024` isn't written as the string `"1024"`. */
	fun putNumber(target: ObjectNode, key: String, value: String) {
		when {
			!decimal.matches(value) -> target.put(key, value)
			'.' in value -> target.put(key, value.toBigDecimal())
			else -> value.toLongOrNull()?.let { target.put(key, it) } ?: target.put(key, value)
		}
	}

	private fun put(mapped: Mapped, value: String) {
		when (mapped.shape) {
			Shape.TEXT -> node.put(mapped.key, value)
			Shape.NUMBER -> putNumber(node, mapped.key, value)
			Shape.SWITCH -> node.put(mapped.key, value in TRUE)
			Shape.LIST -> {
				node.withArrayProperty(mapped.key).add(value)
				val source = value.substringBefore(':', "")
				if (mapped.key == "volumes" && dockerName.matches(source)) file.volume(source)
			}
		}
	}

	/** `--mount type=bind,source=/data,target=/data,readonly` in Compose's long volume syntax. */
	fun mount(given: Given) {
		val spec = nodes.objectNode().put("type", "volume")
		given.value.split(',').forEach { field ->
			val path = mountFields[field.substringBefore('=')]
			val value = field.substringAfter('=', "")
			val parent = path?.substringBefore('.', "").orEmpty()
			val target = if (parent.isEmpty()) spec else spec.withObjectProperty(parent)
			when {
				path == null -> notes += "--mount $field"
				path in mountSwitches -> target.put(path.substringAfter('.'), value.isEmpty() || value in TRUE)
				else -> target.put(path.substringAfter('.'), value)
			}
		}
		node.withArrayProperty("volumes").add(spec)
		val source = text(spec.get("source")).orEmpty()
		if (text(spec.get("type")) == "volume" && dockerName.matches(source)) file.volume(source)
	}

	/** Host, none, bridge and another container's network are a network mode; any other name is a network. */
	fun network(given: Given) {
		val name = given.value
		when {
			name in networkModes || name.startsWith("container:") -> node.put("network_mode", name)
			'=' in name -> note(given)
			else -> {
				nested("networks").withObjectProperty(name)
				network = name
				file.network(name)
			}
		}
	}

	fun onNetwork(given: Given, set: ObjectNode.() -> Unit) {
		val name = network
		if (name == null) note(given) else nested("networks").withObjectProperty(name).set()
	}

	/** `nofile=1024:2048` as `nofile: {soft: 1024, hard: 2048}`, and `nproc=512` as `nproc: 512`. */
	fun ulimit(given: Given) {
		val name = given.value.substringBefore('=')
		val limits = given.value.substringAfter('=', "")
		when {
			limits.isEmpty() -> note(given)
			':' in limits -> nested("ulimits").putObject(name).also {
				putNumber(it, "soft", limits.substringBefore(':'))
				putNumber(it, "hard", limits.substringAfter(':'))
			}

			else -> putNumber(nested("ulimits"), name, limits)
		}
	}

	/** A `key=value` option, such as `--log-opt max-size=10m`, as one member of [target]. */
	fun keyed(given: Given, target: () -> ObjectNode) {
		if ('=' in given.value) {
			target().put(given.value.substringBefore('='), given.value.substringAfter('='))
		} else {
			note(given)
		}
	}
}
