package eu.zeletrik.devcore.tools.dockercompose

import eu.zeletrik.devcore.shell.OutputTooLarge
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DockerComposeTest {
	private fun compose(text: String) = DockerCompose.convert(text, Direction.TO_COMPOSE)
	private fun run(text: String) = DockerCompose.convert(text, Direction.TO_RUN)
	private fun lines(vararg lines: String) = lines.joinToString("\n")

	private fun error(text: String, direction: Direction = Direction.TO_COMPOSE) =
		assertFailsWith<IllegalArgumentException> { DockerCompose.convert(text, direction) }.message

	@Test
	fun `the README's docker run line gives the repository's compose file`() {
		assertEquals(
			Conversion(
				lines(
					"services:",
					"  devcore:",
					"    image: ghcr.io/zeletrik/devcore:latest",
					"    ports:",
					"      - \"8080:8080\"",
					"    restart: unless-stopped",
				),
				"",
			),
			compose("docker run -p 8080:8080 --restart unless-stopped ghcr.io/zeletrik/devcore:latest"),
		)
	}

	@Test
	fun `options are read as Docker's CLI reads them`() {
		assertEquals(
			lines(
				"services:",
				"  web:",
				"    image: nginx",
				"    container_name: web",
				"    ports:",
				"      - \"80:80\"",
				"      - \"443:443\"",
				"      - \"5432:5432\"",
				"    stdin_open: true",
				"    tty: true",
			),
			compose("sudo docker container run --name=web -p80:80 -p=443:443 -itp 5432:5432 -- nginx").text,
		)
	}

	@Test
	fun `numbers are written as numbers, and sizes as text`() {
		assertEquals(
			lines(
				"services:",
				"  x:",
				"    image: x",
				"    cpus: 0.5",
				"    cpu_shares: 512",
				"    oom_score_adj: -500",
				"    mem_limit: 512m",
				"    privileged: true",
				"    read_only: false",
			),
			compose("docker run --cpus 0.5 -c 512 --oom-score-adj -500 -m 512m --privileged --read-only=false x").text,
		)
	}

	@Test
	fun `each command is a service, named after its container or else its image`() {
		val text = compose(
			lines(
				"docker run ghcr.io/zeletrik/devcore:1.0@sha256:abc",
				"docker run --name 'not a name' ghcr.io/zeletrik/devcore",
				"docker run --name devcore-2 nginx",
				"docker run '\${IMAGE}'",
			),
		).text
		assertEquals(
			listOf("  devcore:", "  devcore-2:", "  devcore-2-2:", "  IMAGE-:"),
			text.lines().filter { it.matches(Regex(" {2}\\S.*")) },
		)
	}

	@Test
	fun `named volumes and networks are declared, so Compose uses the ones docker run made`() {
		assertEquals(
			lines(
				"services:",
				"  x:",
				"    image: x",
				"    volumes:",
				"      - data:/data",
				"      - /srv:/srv",
				"      - ./site:/site",
				"    networks:",
				"      backend:",
				"        aliases:",
				"          - db",
				"        ipv4_address: 10.0.0.2",
				"volumes:",
				"  data:",
				"    name: data",
				"networks:",
				"  backend:",
				"    external: true",
			),
			compose(
				"docker run -v data:/data -v /srv:/srv -v ./site:/site --network backend --network-alias db --ip 10.0.0.2 x",
			).text,
		)
	}

	@Test
	fun `a network mode is not a network, and options with no network to apply to are listed`() {
		assertEquals(
			Conversion(
				lines("services:", "  x:", "    image: x", "    network_mode: host"),
				lines("x: --network-alias db", "x: --ip6 ::1", "x: --network name=a,alias=b"),
			),
			compose("docker run --network host --network-alias db --ip6 ::1 --network name=a,alias=b x"),
		)
	}

	@Test
	fun `--mount becomes Compose's long volume syntax`() {
		assertEquals(
			Conversion(
				lines(
					"services:",
					"  x:",
					"    image: x",
					"    volumes:",
					"      - type: bind",
					"        source: /etc/tz",
					"        target: /etc/timezone",
					"        read_only: true",
					"      - type: volume",
					"        source: cache",
					"        target: /cache",
					"        volume:",
					"          nocopy: true",
					"      - type: tmpfs",
					"        target: /tmp",
					"        tmpfs:",
					"          size: 64m",
					"volumes:",
					"  cache:",
					"    name: cache",
				),
				"x: --mount tmpfs-mode=1770",
			),
			compose(
				"docker run --mount type=bind,src=/etc/tz,dst=/etc/timezone,readonly " +
					"--mount source=cache,target=/cache,volume-nocopy " +
					"--mount type=tmpfs,destination=/tmp,tmpfs-size=64m,tmpfs-mode=1770 x",
			),
		)
	}

	@Test
	fun `health checks, logging, limits and the stop timeout fill their nested keys`() {
		assertEquals(
			Conversion(
				lines(
					"services:",
					"  x:",
					"    image: x",
					"    healthcheck:",
					"      test:",
					"        - CMD-SHELL",
					"        - pg_isready -U postgres",
					"      interval: 10s",
					"      retries: 5",
					"    logging:",
					"      driver: json-file",
					"      options:",
					"        max-size: 10m",
					"    ulimits:",
					"      nofile:",
					"        soft: 1024",
					"        hard: 2048",
					"      nproc: 512",
					"    stop_grace_period: 30s",
					"    storage_opt:",
					"      size: 10G",
				),
				lines("x: --log-opt compress", "x: --ulimit core", "x: --stop-timeout soon"),
			),
			compose(
				"docker run --health-cmd 'pg_isready -U postgres' --health-interval 10s --health-retries 5 " +
					"--log-driver json-file --log-opt max-size=10m --log-opt compress " +
					"--ulimit nofile=1024:2048 --ulimit nproc=512 --ulimit core " +
					"--stop-timeout 30 --stop-timeout soon --storage-opt size=10G x",
			),
		)
		assertEquals(
			lines("services:", "  x:", "    image: x", "    healthcheck:", "      disable: true"),
			compose("docker run --no-healthcheck x").text,
		)
	}

	@Test
	fun `the entrypoint is a list, and the words after the image are the command`() {
		assertEquals(
			lines(
				"services:",
				"  x:",
				"    image: x",
				"    entrypoint:",
				"      - /bin/sh",
				"    command:",
				"      - -c",
				"      - echo hi",
				"      - --",
				"      - --flag",
			),
			compose("docker run --entrypoint /bin/sh x -c 'echo hi' -- --flag").text,
		)
		assertEquals(
			lines("services:", "  x:", "    image: x", "    entrypoint: []"),
			compose("docker run --entrypoint '' x").text,
		)
	}

	@Test
	fun `quotes, escapes, continuations and comments are read as a shell reads them`() {
		val text = lines(
			"# the web server",
			"docker run \\",
			"  -e 'A=single \"quoted\"' \\",
			"  -e \"B=double 'quoted' \\\"escaped\\\"\" \\\r",
			"  -e C=back\\ slash -e D=\"\" \\",
			"  x # trailing comment",
		)
		assertEquals(
			lines(
				"services:",
				"  x:",
				"    image: x",
				"    environment:",
				"      - A=single \"quoted\"",
				"      - B=double 'quoted' \"escaped\"",
				"      - C=back slash",
				"      - D=",
			),
			compose(text).text,
		)
	}

	@Test
	fun `variables stay for Compose, and a dollar the shell keeps as text becomes Compose's $$`() {
		assertEquals(
			lines(
				"services:",
				"  x:",
				"    image: x",
				"    environment:",
				"      - P=pa\$\$\$\$word",
				"      - A=\$\$HOME",
				"      - B=\$HOME",
				"      - \"C=\${X:-\${Y}}\"",
				"      - D=5\$\$",
				"      - E=\$\$1",
				"    volumes:",
				"      - \"\${PWD}/data:/data\"",
			),
			compose(
				"docker run -e 'P=pa\$\$word' -e A=\\\$HOME -e B=\$HOME -e \"C=\${X:-\${Y}}\" -e D=5\$ -e E=\$1 " +
					"-v \"\$(pwd)\"/data:/data x",
			).text,
		)
	}

	@Test
	fun `commands end at a newline, a semicolon, && and ||`() {
		val text = compose("docker run a; docker run b && docker run c || docker run d\n\ndocker run e").text
		assertEquals(listOf("  a:", "  b:", "  c:", "  d:", "  e:"), text.lines().filter { it.matches(Regex(" {2}\\S.*")) })
	}

	@Test
	fun `text in any script passes through unchanged`() {
		assertEquals(
			lines("services:", "  x:", "    image: x", "    labels:", "      - İ=é😀"),
			compose("docker run -l 'İ=é😀' x").text,
		)
	}

	@Test
	fun `options with no Compose key are listed once each, with a hint for -d and --rm`() {
		assertEquals(
			lines(
				"nginx: -d (docker compose up -d runs it in the background)",
				"nginx: --rm (docker compose down removes the container)",
				"nginx: --gpus all",
				"nginx: --sig-proxy=false",
				"nginx: --rm=false",
			),
			compose("docker run -dd --rm --gpus all --sig-proxy=false --rm=false -d nginx").notConverted,
		)
	}

	@Test
	fun `blank input and comments give nothing`() {
		assertEquals(Conversion("", ""), compose("  \n# just a comment\n"))
		assertEquals(Conversion("", ""), run("# just a comment"))
	}

	@Test
	fun `a command that can't be read is refused with the reason`() {
		assertEquals("Command 2 isn't a docker run command", error("docker run x\ndocker ps"))
		assertEquals("Command 1 has no image", error("docker run -d"))
		assertEquals("Command 1: unknown option --frobnicate", error("docker run --frobnicate x"))
		assertEquals("Command 1: unknown option -z", error("docker run -itz x"))
		assertEquals("Command 1: -p needs a value", error("docker run -p"))
		assertEquals("Command 1: --rm=maybe isn't true or false", error("docker run --rm=maybe x"))
		assertEquals("Unclosed ' quote", error("docker run 'x"))
		assertEquals("Unclosed \" quote", error("docker run \"x"))
		listOf("docker run \$(cat image)", "docker run `cat image`").forEach {
			assertEquals("Command substitution isn't supported, except \$(pwd)", error(it))
		}
		listOf("docker run x | tee", "docker run x > log", "docker run x &").forEach {
			assertEquals("Pipes, redirects and background jobs aren't supported", error(it))
		}
	}

	@Test
	fun `a Compose service becomes a docker run command, an option per line`() {
		val file = lines(
			"services:",
			"  devcore:",
			"    image: ghcr.io/zeletrik/devcore:latest",
			"    ports:",
			"      - \"8080:8080\"",
			"      # a comment",
			"    restart: unless-stopped",
		)
		assertEquals(
			Conversion(
				lines(
					"docker run \\",
					"  --name devcore \\",
					"  -p 8080:8080 \\",
					"  --restart unless-stopped \\",
					"  ghcr.io/zeletrik/devcore:latest",
				),
				"",
			),
			run(file),
		)
	}

	@Test
	fun `a command written from Compose converts back to the same Compose file`() {
		val command = lines(
			"docker run \\",
			"  --name web \\",
			"  -p 8080:80 \\",
			"  -e A=1 \\",
			"  -v data:/data \\",
			"  --restart unless-stopped \\",
			"  nginx:1.27 nginx -g 'daemon off;'",
		)
		val file = compose(command)
		assertEquals(Conversion(command, "volumes (top level)"), run(file.text))
	}

	@Test
	fun `values are quoted for the shell, and variables are left to it`() {
		val file = lines(
			"services:",
			"  x:",
			"    image: \"\${IMAGE:-nginx}\"",
			"    environment:",
			"      PLAIN: a.b/c:d@e%f+g=h,i~j-k",
			"      SPACE: a b",
			"      QUOTE: it's",
			"      PASS:",
			"      VAR: \${HOME}/x",
			"      LITERAL: \$\$5",
			"      MIXED: \"\$\$\${X} \\\"q\\\"\"",
			"      TEXT: é😀İ",
		)
		assertEquals(
			lines(
				"docker run \\",
				"  --name x \\",
				"  -e PLAIN=a.b/c:d@e%f+g=h,i~j-k \\",
				"  -e 'SPACE=a b' \\",
				"  -e 'QUOTE=it'\\''s' \\",
				"  -e PASS \\",
				"  -e \"VAR=\${HOME}/x\" \\",
				"  -e 'LITERAL=\$5' \\",
				"  -e \"MIXED=\\\$\${X} \\\"q\\\"\" \\",
				"  -e 'TEXT=é😀İ' \\",
				"  \"\${IMAGE:-nginx}\"",
			),
			run(file).text,
		)
	}

	@Test
	fun `the long syntax of ports and volumes becomes -p and --mount`() {
		val file = lines(
			"services:",
			"  x:",
			"    image: x",
			"    ports:",
			"      - 80",
			"      - {target: 443, published: 8443, protocol: tcp}",
			"      - {target: 53, host_ip: 127.0.0.1, protocol: udp}",
			"      - {target: 81, mode: host}",
			"    volumes:",
			"      - ./site:/site:ro",
			"      - {type: bind, source: /a, target: /b, read_only: true, bind: {propagation: rshared}}",
			"      - {type: volume, source: v, target: /v, read_only: false, volume: {nocopy: true}}",
			"      - {type: bind, source: /c, target: /d, bind: {create_host_path: true}}",
		)
		assertEquals(
			Conversion(
				lines(
					"docker run \\",
					"  --name x \\",
					"  -p 80 \\",
					"  -p 8443:443/tcp \\",
					"  -p 127.0.0.1::53/udp \\",
					"  -v ./site:/site:ro \\",
					"  --mount type=bind,source=/a,target=/b,readonly,bind-propagation=rshared \\",
					"  --mount type=volume,source=v,target=/v,volume-nocopy \\",
					"  x",
				),
				lines("x: ports[3]", "x: volumes[3]"),
			),
			run(file),
		)
	}

	@Test
	fun `the entrypoint's first word is --entrypoint, the rest come before the command`() {
		val command = { entrypoint: String, cmd: String ->
			run(lines("services:", "  x:", "    image: x", "    entrypoint: $entrypoint", "    command: $cmd"))
				.text.lines().takeLast(2)
		}
		assertEquals(listOf("  --entrypoint sh \\", "  x -c 'echo hi'"), command("[sh, -c]", "[echo hi]"))
		assertEquals(listOf("  --entrypoint /init \\", "  x --verbose run"), command("/init --verbose", "run"))
		assertEquals(listOf("  --entrypoint '' \\", "  x 'a b' 'c'\\''d' '\$HOME'"), command("[]", "[a b, c'd, \$\$HOME]"))
	}

	@Test
	fun `health checks, networks, limits, logging and the stop timeout become their options`() {
		val file = lines(
			"services:",
			"  x:",
			"    image: x",
			"    healthcheck: {test: [CMD, curl, -f, 'http://a b'], interval: 30s, start_period: 5s, retries: 3}",
			"    networks: {backend: {aliases: [db, sql], ipv4_address: 10.0.0.2}}",
			"    ulimits: {nproc: 512, nofile: {soft: 1024, hard: 2048}}",
			"    logging: {driver: local, options: {max-size: 10m}}",
			"    stop_grace_period: 1m",
			"    storage_opt: {size: 10G}",
			"    stdin_open: true",
			"    tty: true",
			"    privileged: false",
		)
		assertEquals(
			lines(
				"docker run \\",
				"  --name x \\",
				"  --health-cmd 'curl -f '\\''http://a b'\\''' \\",
				"  --health-interval 30s \\",
				"  --health-start-period 5s \\",
				"  --health-retries 3 \\",
				"  --network backend \\",
				"  --network-alias db \\",
				"  --network-alias sql \\",
				"  --ip 10.0.0.2 \\",
				"  --ulimit nproc=512 \\",
				"  --ulimit nofile=1024:2048 \\",
				"  --log-driver local \\",
				"  --log-opt max-size=10m \\",
				"  --stop-timeout 60 \\",
				"  --storage-opt size=10G \\",
				"  -i \\",
				"  -t \\",
				"  x",
			),
			run(file).text,
		)
		assertEquals(
			lines("docker run \\", "  --name x \\", "  --no-healthcheck \\", "  x"),
			run("services: {x: {image: x, healthcheck: {test: [NONE]}}}").text,
		)
	}

	@Test
	fun `keys docker run can't take are listed, and a service without an image is skipped`() {
		val file = lines(
			"name: demo",
			"services:",
			"  web:",
			"    image: nginx",
			"    container_name: site",
			"    depends_on: [db]",
			"    network_mode: service:db",
			"    networks: {a: {aliases: [x]}, b: {}}",
			"    ulimits: {core: {soft: 1}}",
			"    stop_grace_period: 1m30s",
			"  db:",
			"    build: .",
			"  cache: {}",
			"volumes: {data: {}}",
		)
		assertEquals(
			Conversion(
				lines("docker run \\", "  --name site \\", "  --network a \\", "  --network b \\", "  nginx"),
				lines(
					"web: depends_on",
					"web: network_mode",
					"web: networks.a.aliases",
					"web: ulimits.core",
					"web: stop_grace_period",
					"db: build (build and tag the image, then run it)",
					"cache: no image",
					"name (top level)",
					"volumes (top level)",
				),
			),
			run(file),
		)
	}

	@Test
	fun `a file that isn't Compose is refused with the reason`() {
		val toRun = Direction.TO_RUN
		listOf("services: {}", "[1, 2]", "name: demo").forEach {
			assertEquals("The Compose file has no services", error(it, toRun))
		}
		assertEquals("Service 'web' isn't a map", error("services: {web: nginx}", toRun))
		assertEquals("Duplicate key 'web'", error("services:\n  web: {image: a}\n  web: {image: b}", toRun))
	}

	@Test
	fun `YAML aliases are refused at the first one, so an alias bomb costs nothing`() {
		val bomb = StringBuilder("a: &a [x, x, x, x, x, x, x, x, x, x]\n")
		('b'..'z').forEach { bomb.append("$it: &$it [${List(10) { _ -> "*${it - 1}" }.joinToString()}]\n") }
		bomb.append("services: {x: {image: x, labels: *z}}")
		var message: String? = null
		val nanos = measureNanoTime { message = error(bomb.toString(), Direction.TO_RUN) }
		assertEquals("YAML aliases aren't supported (line 2, column 8)", message)
		assertTrue(nanos < 100_000_000, "took ${nanos / 1_000_000} ms")
	}

	@Test
	fun `outputs past the cap are refused while they are written`() {
		val many = "services: {x: {image: x, cap_add: [" + "a,".repeat(499_975) + "a]}}"
		assertEquals("Output is too large (max 5,000,000 characters)", assertFailsWith<OutputTooLarge> { run(many) }.message)
		val name = "n".repeat(1_000)
		val keys = (1..90_000).joinToString("") { "    k$it: 1\n" }.take(990_000)
		assertFailsWith<OutputTooLarge> { run("services:\n  $name:\n    image: x\n${keys.substringBeforeLast('\n')}") }
	}

	@Test
	fun `the slowest input at the input cap takes well under a second`() {
		val mounts = "docker run" + " --mount ro".repeat(90_000) + " x"
		assertTrue(mounts.length < 1_000_000)
		val nanos = measureNanoTime { compose(mounts) }
		assertTrue(nanos < 1_000_000_000, "took ${nanos / 1_000_000} ms")
	}
}
