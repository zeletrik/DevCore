package eu.zeletrik.devcore.tools.dockercompose

import eu.zeletrik.devcore.shell.BoundedWriter
import tools.jackson.databind.JsonNode

/** Which way the converter runs. */
enum class Direction(val label: String) {
	TO_COMPOSE("To Compose"), TO_RUN("To docker run");

	/** The deep-link `mode`: `to-compose` or `to-run`. */
	val param = name.lowercase().replace('_', '-')

	companion object {
		fun fromParam(value: String?) = entries.firstOrNull { it.param == value } ?: TO_COMPOSE
	}
}

/** The converted text, and one line for each option or key that has no equivalent on the other side. */
data class Conversion(val text: String, val notConverted: String)

/**
 * Converts `docker run` commands to a Compose file and back, offline: no image is pulled or inspected and no file is
 * read, so an `--env-file` stays a path. What has no equivalent on the other side is listed, never dropped.
 */
object DockerCompose {
	fun convert(text: String, direction: Direction): Conversion = when (direction) {
		Direction.TO_COMPOSE -> RunToCompose.convert(text)
		Direction.TO_RUN -> ComposeToRun.convert(text)
	}
}

/** Lines written through the output cap, so that a long service name on each of many lines fails early. */
internal class Lines {
	private val out = BoundedWriter()
	private var empty = true

	fun add(line: String) {
		if (!empty) out.write("\n")
		out.write(line)
		empty = false
	}

	override fun toString() = out.toString()
}

/** How a Compose key holds an option's value: as text, as a number when it reads as one, as a list, or as true. */
internal enum class Shape { TEXT, NUMBER, LIST, SWITCH }

/** A docker run option that maps one to one onto a Compose service key. */
internal class Mapped(val flag: String, val key: String, val shape: Shape)

internal val byFlag = listOf(
	Mapped("--add-host", "extra_hosts", Shape.LIST),
	Mapped("--annotation", "annotations", Shape.LIST),
	Mapped("--cap-add", "cap_add", Shape.LIST),
	Mapped("--cap-drop", "cap_drop", Shape.LIST),
	Mapped("--cgroup-parent", "cgroup_parent", Shape.TEXT),
	Mapped("--cgroupns", "cgroup", Shape.TEXT),
	Mapped("--cpu-count", "cpu_count", Shape.NUMBER),
	Mapped("--cpu-percent", "cpu_percent", Shape.NUMBER),
	Mapped("--cpu-period", "cpu_period", Shape.NUMBER),
	Mapped("--cpu-quota", "cpu_quota", Shape.NUMBER),
	Mapped("--cpu-rt-period", "cpu_rt_period", Shape.NUMBER),
	Mapped("--cpu-rt-runtime", "cpu_rt_runtime", Shape.NUMBER),
	Mapped("--cpu-shares", "cpu_shares", Shape.NUMBER),
	Mapped("--cpus", "cpus", Shape.NUMBER),
	Mapped("--cpuset-cpus", "cpuset", Shape.TEXT),
	Mapped("--device", "devices", Shape.LIST),
	Mapped("--device-cgroup-rule", "device_cgroup_rules", Shape.LIST),
	Mapped("--dns", "dns", Shape.LIST),
	Mapped("--dns-option", "dns_opt", Shape.LIST),
	Mapped("--dns-search", "dns_search", Shape.LIST),
	Mapped("--domainname", "domainname", Shape.TEXT),
	Mapped("--env", "environment", Shape.LIST),
	Mapped("--env-file", "env_file", Shape.LIST),
	Mapped("--expose", "expose", Shape.LIST),
	Mapped("--group-add", "group_add", Shape.LIST),
	Mapped("--hostname", "hostname", Shape.TEXT),
	Mapped("--init", "init", Shape.SWITCH),
	Mapped("--interactive", "stdin_open", Shape.SWITCH),
	Mapped("--ipc", "ipc", Shape.TEXT),
	Mapped("--isolation", "isolation", Shape.TEXT),
	Mapped("--label", "labels", Shape.LIST),
	Mapped("--link", "links", Shape.LIST),
	Mapped("--mac-address", "mac_address", Shape.TEXT),
	Mapped("--memory", "mem_limit", Shape.TEXT),
	Mapped("--memory-reservation", "mem_reservation", Shape.TEXT),
	Mapped("--memory-swap", "memswap_limit", Shape.TEXT),
	Mapped("--memory-swappiness", "mem_swappiness", Shape.NUMBER),
	Mapped("--oom-kill-disable", "oom_kill_disable", Shape.SWITCH),
	Mapped("--oom-score-adj", "oom_score_adj", Shape.NUMBER),
	Mapped("--pid", "pid", Shape.TEXT),
	Mapped("--pids-limit", "pids_limit", Shape.NUMBER),
	Mapped("--platform", "platform", Shape.TEXT),
	Mapped("--privileged", "privileged", Shape.SWITCH),
	Mapped("--publish", "ports", Shape.LIST),
	Mapped("--pull", "pull_policy", Shape.TEXT),
	Mapped("--read-only", "read_only", Shape.SWITCH),
	Mapped("--restart", "restart", Shape.TEXT),
	Mapped("--runtime", "runtime", Shape.TEXT),
	Mapped("--security-opt", "security_opt", Shape.LIST),
	Mapped("--shm-size", "shm_size", Shape.TEXT),
	Mapped("--stop-signal", "stop_signal", Shape.TEXT),
	Mapped("--sysctl", "sysctls", Shape.LIST),
	Mapped("--tmpfs", "tmpfs", Shape.LIST),
	Mapped("--tty", "tty", Shape.SWITCH),
	Mapped("--user", "user", Shape.TEXT),
	Mapped("--userns", "userns_mode", Shape.TEXT),
	Mapped("--uts", "uts", Shape.TEXT),
	Mapped("--volume", "volumes", Shape.LIST),
	Mapped("--volumes-from", "volumes_from", Shape.LIST),
	Mapped("--workdir", "working_dir", Shape.TEXT),
).associateBy { it.flag }

internal val byKey = byFlag.values.associateBy { it.key }

/** Short and old spellings, by the long option they stand for. Commands written from Compose use the short ones. */
internal val aliases = mapOf(
	"-a" to "--attach",
	"-c" to "--cpu-shares",
	"-d" to "--detach",
	"-e" to "--env",
	"-h" to "--hostname",
	"-i" to "--interactive",
	"-l" to "--label",
	"-m" to "--memory",
	"-P" to "--publish-all",
	"-p" to "--publish",
	"-q" to "--quiet",
	"-t" to "--tty",
	"-u" to "--user",
	"-v" to "--volume",
	"-w" to "--workdir",
	"--net" to "--network",
	"--net-alias" to "--network-alias",
)

internal val shortSpelling = aliases.entries.filter { it.key.length == 2 }.associate { (short, long) -> long to short }

/** How Docker's CLI spells true and false. */
internal val TRUE = setOf("1", "t", "T", "true", "TRUE", "True")
internal val FALSE = setOf("0", "f", "F", "false", "FALSE", "False")

/** `--mount` fields by the path they fill in Compose's long volume syntax; the first spelling is written back. */
internal val mountFields = mapOf(
	"type" to "type",
	"source" to "source",
	"src" to "source",
	"target" to "target",
	"destination" to "target",
	"dst" to "target",
	"readonly" to "read_only",
	"ro" to "read_only",
	"bind-propagation" to "bind.propagation",
	"volume-nocopy" to "volume.nocopy",
	"volume-subpath" to "volume.subpath",
	"tmpfs-size" to "tmpfs.size",
)

/** The `--mount` fields that are true when written without a value. */
internal val mountSwitches = setOf("read_only", "volume.nocopy")

/** A string, number or boolean as text; null for anything else. */
internal fun text(node: JsonNode?): String? = node?.takeIf { it.isValueNode && !it.isNull }?.asString()
