import com.github.jk1.license.render.JsonReportRenderer
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.plugin.getSupportedKotlinVersion
import org.gradle.kotlin.dsl.withType

plugins {
	kotlin("jvm") version "2.4.21"
	kotlin("plugin.spring") version "2.4.21"
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.vaadin") version "25.3.4"
	id("dev.detekt") version "2.0.0-alpha.3"
	id("com.github.jk1.dependency-license-report") version "3.1.4"
}
repositories {
	mavenCentral()
}

extra["vaadinVersion"] = "25.3.4"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	developmentOnly("com.vaadin:vaadin-dev")
	implementation("com.vaadin:vaadin-spring-boot-starter")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
	implementation("tools.jackson.dataformat:jackson-dataformat-csv")
	implementation("tools.jackson.dataformat:jackson-dataformat-properties")
	// NOTE: pinned because 3.1.x truncates 19-digit TOML integers; drop the pin once Spring Boot manages Jackson 3.2.4+.
	implementation("tools.jackson.dataformat:jackson-dataformat-toml:3.2.4")
	implementation("io.github.java-diff-utils:java-diff-utils:4.18")
	implementation("com.github.vertical-blank:sql-formatter:2.0.5")
	implementation("com.jayway.jsonpath:json-path:3.0.0")
	implementation("org.commonmark:commonmark:0.31.0")
	implementation("org.commonmark:commonmark-ext-gfm-tables:0.31.0")
	implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.31.0")
	implementation("org.commonmark:commonmark-ext-autolink:0.31.0")
	implementation("org.jsoup:jsoup:1.23.2")
	implementation("org.bouncycastle:bcprov-jdk18on:1.86")
	implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
	implementation("com.google.zxing:core:3.5.4")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("com.vaadin:browserless-test-spring")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("com.vaadin:vaadin-bom:${property("vaadinVersion")}")
	}

	configurations.matching { it.name == "detekt" }.all {
		resolutionStrategy.eachDependency {
			if (requested.group == "org.jetbrains.kotlin") {
				useVersion(getSupportedKotlinVersion())
			}
		}
	}
}

kotlin {
	jvmToolchain(25)
}

springBoot {
	buildInfo()
}


tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.bootJar {
	archiveFileName = "devcore.jar"
}

tasks.jar {
	enabled = false
}

detekt {
	config.setFrom(file("detekt.yml"))
	buildUponDefaultConfig = true
	autoCorrect = true
}

tasks.withType<Detekt>().configureEach {
	reports {
		checkstyle.required.set(true)
		html.required.set(true)
		sarif.required.set(true)
		markdown.required.set(false)
	}
}

// The libraries the jar ships (development-only ones excluded), for the About page and the bundled notices.
licenseReport {
	configurations = arrayOf("productionRuntimeClasspath")
	renderers = arrayOf(JsonReportRenderer("dependencies.json", false))
}

val licenseReportDir = layout.buildDirectory.dir("reports/dependency-license")

/** The curated NOTICE, then each bundled library's own NOTICE text once, naming the libraries that carry it. */
val assembleNotice by tasks.registering {
	dependsOn("generateLicenseReport")
	val curated = layout.projectDirectory.file("NOTICE")
	val reports = licenseReportDir
	val output = layout.buildDirectory.file("generated/notice/NOTICE")
	inputs.file(curated)
	inputs.dir(reports)
	outputs.file(output)
	doLast {
		val root = reports.get().asFile
		val byText = root.walkTopDown()
			.filter { it.isFile && it.name.lowercase().startsWith("notice") }
			.groupBy({ it.readText().trim() }, { it.relativeTo(root).path.substringBefore('/').removeSuffix(".jar") })
			.toSortedMap()
		val upstream = byText.entries.joinToString("\n\n") { (text, modules) -> "=== ${modules.sorted().joinToString(", ")} ===\n$text" }
		output.get().asFile.apply { parentFile.mkdirs() }.writeText(curated.asFile.readText().trimEnd() + "\n\n" + upstream + "\n")
	}
}

tasks.processResources {
	dependsOn(assembleNotice)
	into("META-INF/devcore") {
		from(assembleNotice)
		from(licenseReportDir.map { it.file("dependencies.json") })
		from("LICENSE", "licenses/Apache-2.0.txt")
		// Present only in a release checkout: semantic-release writes it before the release build.
		from("CHANGELOG.md")
	}
}
