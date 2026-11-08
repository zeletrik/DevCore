import com.github.jk1.license.render.JsonReportRenderer
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.plugin.getSupportedKotlinVersion

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.spring)
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependency.management)
	alias(libs.plugins.vaadin)
	alias(libs.plugins.detekt)
	alias(libs.plugins.license.report)
}
repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.spring.boot.starter.webmvc)
	implementation(libs.spring.boot.starter.actuator)
	implementation(libs.vaadin.spring)
	implementation(libs.kotlin.reflect)
	implementation(libs.jackson.kotlin)
	implementation(libs.jackson.yaml)
	implementation(libs.jackson.csv)
	implementation(libs.jackson.properties)
	implementation(libs.jackson.toml)
	implementation(libs.java.diff.utils)
	implementation(libs.sql.formatter)
	implementation(libs.json.path)
	implementation(libs.bundles.commonmark)
	implementation(libs.jsoup)
	implementation(libs.bundles.bouncycastle)
	implementation(libs.zxing)
	developmentOnly(libs.vaadin.dev)
	developmentOnly(libs.spring.boot.devtools)
	testImplementation(libs.spring.boot.test)
	testImplementation(libs.kotlin.test)
	testImplementation(libs.vaadin.browserless.test)
	testRuntimeOnly(libs.junit.launcher)
}

dependencyManagement {
	imports {
		mavenBom("com.vaadin:vaadin-bom:${libs.versions.vaadin.get()}")
	}
}

// Detekt is compiled against its own Kotlin; the project's newer Kotlin stays off its classpath.
configurations.matching { it.name == "detekt" }.all {
	resolutionStrategy.eachDependency {
		@Suppress("UnstableApiUsage")
		if (requested.group == "org.jetbrains.kotlin") useVersion(getSupportedKotlinVersion())
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

val assembleNotice = tasks.register("assembleNotice") {
	group = "build"
	description = "Writes the curated NOTICE plus each bundled library's NOTICE text, naming the libraries that carry it."
	dependsOn("generateLicenseReport")
	val curated = layout.projectDirectory.file("NOTICE")
	val reports = licenseReportDir
	val output = layout.buildDirectory.file("generated/notice/NOTICE")
	inputs.file(curated)
	inputs.dir(reports)
	outputs.file(output)
	doLast {
		val root = reports.get().asFile
		val notices = root.walkTopDown().filter { it.isFile && it.name.lowercase().startsWith("notice") }
		// Jars that carry the same NOTICE text are listed together above a single copy of it.
		val byText = notices
			.groupBy({ it.readText().trim() }, { it.relativeTo(root).path.substringBefore('/').removeSuffix(".jar") })
			.toSortedMap()
		val upstream = byText.entries.joinToString("\n\n") { (text, modules) ->
			"=== ${modules.sorted().joinToString(", ")} ===\n$text"
		}
		val file = output.get().asFile
		file.parentFile.mkdirs()
		file.writeText(curated.asFile.readText().trimEnd() + "\n\n" + upstream + "\n")
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
