import com.almostreliable.almostgradle.dependency.LoadingMode
import java.util.Locale
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
	id("net.neoforged.moddev") version "2.0.138"
	id("com.almostreliable.almostgradle") version "2.2.0"
	id("groovy")
	id("idea")
	jacoco
	// id("me.shedaniel.unified-publishing") version "0.1.+"
}

val runningInCI = System.getenv("CI").toBoolean()

almostgradle.setup {
	javaVersion = 25

	launchArgs {
		loggingLevel = "INFO"
		// mixinDebugOutput = true
	}

	dataGen = false

	splitRunDirs = true
	withAccessTransformerValidation = !runningInCI

	tests {
		testMod = true
		gameTests = true
		testFramework = true
		jUnit = true
	}

	recipeViewers {
		/*emi {
			runConfig = true
			mode = LoadingMode.API
			version = "1.1.22"
			minecraftVersion = "1.21.1"
		}*/

		rei {
			runConfig = true
			mode = LoadingMode.API
			version = "21.11.814"
			minecraftVersion = "1.21.1"
		}

		jei {
			runConfig = true
			mode = LoadingMode.API
			version = "29.6.2.31"
		}
	}
}

val groovyVersion: String by project
val tinyServerVersion: String by project
val gifLibVersion: String by project
val batVersion: String by project

println("Building version: ${project.version}")

val gametestClientRunDir = layout.buildDirectory.dir("tmp/gametestRuns/gametestClient")

neoForge {
	interfaceInjectionData {
		from(file("interfaces.json"))
		publish(file("interfaces.json"))
	}
	runs {
		configureEach {
			jvmArgument("-XX:+IgnoreUnrecognizedVMOptions")
			jvmArgument("-XX:+AllowEnhancedClassRedefinition")
		}

		// Interactive client runner; start the test mod's GameTests from an integrated world with /test run testmod:*.
		create("gametestClient") {
			client()
			ideName = "GameTest Client"
			gameDirectory = gametestClientRunDir.get().asFile
			sourceSet = sourceSets["test"]
			programArguments.addAll("--width", "1920", "--height", "1080")
			systemProperty("neoforge.enableGameTest", "true")
			systemProperty("neoforge.enabledGameTestNamespaces", "testmod")
		}
	}
}

repositories {
	maven {
		setUrl("https://maven.shedaniel.me/")
		content {
			includeGroup("me.shedaniel")
			includeGroup("me.shedaniel.cloth")
			includeGroup("dev.architectury")
		}
	}

	maven {
		setUrl("https://maven.latvian.dev/releases")
		content {
			includeGroup("dev.latvian.mods")
			includeGroup("dev.latvian.apps")
		}
	}

	maven {
		setUrl("https://maven.latvian.dev/mirror")
		content {
			includeGroup("dev.architectury")
			includeGroup("me.shedaniel")
			includeGroup("me.shedaniel.cloth")
			includeGroup("net.darkhax.bookshelf")
			includeGroup("net.darkhax.gamestages")
			includeGroup("com.github.rtyley")
		}
	}
	mavenCentral()
}

val artifactType = Attribute.of("artifactType", String::class.java)
val fixedManifestType = "jar-fixed-manifest"

tasks.named<Copy>("processResources") {
	from(layout.projectDirectory.file("plugin/live-plugin/plugin.kts")) {
		into("META-INF/kubegroovy/live-plugin")
	}
}

// A dedicated resolvable configuration containing ONLY the jar you want to patch
val patchedJarConfig by configurations.creating {
	isCanBeResolved = true
	isCanBeConsumed = false
	isTransitive = false // avoid pulling in extra deps you don't need patched
}

val patchedJarFiles = patchedJarConfig.incoming.artifactView {
	attributes { attribute(artifactType, fixedManifestType) }
}.files

configurations.named("implementation") {
	exclude(group = "org.apache.groovy", module = "groovy")
}

dependencies {
	registerTransform(FixManifestTransform::class) {
		from.attribute(artifactType, "jar")
		to.attribute(artifactType, fixedManifestType)

		parameters {
			manifestEntries.set(mapOf(
				"Automatic-Module-Name" to "org.apache.groovy",
				"Implementation-Version" to groovyVersion,
				"FMLModType" to "GAMELIBRARY"
			))
		}
	}

	patchedJarConfig("org.apache.groovy:groovy:$groovyVersion")
	//api("org.apache.groovy:groovy-callsite:$groovyVersion")
	testImplementation("org.assertj:assertj-core:3.27.3")
	implementation("dev.latvian.mods:better-advanced-tooltips") {
		version {
			strictly("[$batVersion,)")
			prefer(batVersion)
		}
	}

	implementation(files(patchedJarFiles))?.let { jarJar(it) }

	jarJar(implementation("dev.latvian.apps:tiny-java-server") {
		version {
			strictly("[$tinyServerVersion,)")
			prefer(tinyServerVersion)
		}
	})
	jarJar(implementation("com.github.rtyley:animated-gif-lib-for-java") {
		version {
			strictly("[animated-gif-lib-$gifLibVersion,)")
			prefer("animated-gif-lib-$gifLibVersion")
		}
	})
}

// Make the Groovy fixtures available in the gametest run's game directory.
val copyGameTestScripts by tasks.registering(Sync::class) {
	mustRunAfter("prepareGametestRun")
	from(layout.projectDirectory.dir("src/test/kubegroovy"))
	into(layout.buildDirectory.dir("tmp/gametestRuns/gametest/kubegroovy"))
}

val copyGameTestClientScripts by tasks.registering(Sync::class) {
	mustRunAfter("prepareGametestClientRun")
	from(layout.projectDirectory.dir("src/test/kubegroovy"))
	into(gametestClientRunDir.map { it.dir("kubegroovy") })
}

tasks.matching { it.name == "runGametest" }.configureEach {
	outputs.upToDateWhen { false }
	dependsOn(copyGameTestScripts)
}

tasks.matching { it.name == "runGametestClient" }.configureEach {
	group = "mod development"
	description = "Launches a GameTest-enabled client with the test mod; run /test run testmod:* in an integrated world."
	outputs.upToDateWhen { false }
	dependsOn(copyGameTestClientScripts)
}

// Keep one merged local coverage report for the unit-test and game-test JVMs.
val coverageIncludes = listOf("dev.uncandango.kubegroovy.*")
val coverageExcludes = listOf(
	"dev/uncandango/kubegroovy/core/mixin/**",
)

jacoco {
	toolVersion = "0.8.15"
	applyTo(tasks.withType<JavaExec>().matching {
		it.name == "runGametest" || it.name == "runGametestClient"
	})
}

val coverageRequested = gradle.startParameter.taskNames.any {
	it.substringAfterLast(':').startsWith("coverage")
}

tasks.named<Test>("test") {
	failOnNoDiscoveredTests = false
	if (coverageRequested) {
		outputs.upToDateWhen { false }
	}
	extensions.configure<JacocoTaskExtension> {
		includes = coverageIncludes
	}
}

tasks.withType<JavaExec>().matching {
	it.name == "runGametest" || it.name == "runGametestClient"
}.configureEach {
	mustRunAfter("test")
	if (coverageRequested) {
		outputs.upToDateWhen { false }
	}
	extensions.configure<JacocoTaskExtension> {
		isEnabled = true
		includes = coverageIncludes
	}
}

val coverageReport by tasks.registering(JacocoReport::class) {
	group = "verification"
	description = "Merges coverage from the JUnit and game-test JVMs."
	dependsOn("test", "runGametest")
	sourceSets(sourceSets["main"])
	classDirectories.setFrom(sourceSets["main"].output.asFileTree.matching {
		exclude(coverageExcludes)
	})
	executionData(fileTree(layout.buildDirectory) {
		include("jacoco/test.exec", "jacoco/runGametest.exec", "jacoco/runGametestClient.exec")
	})
	reports {
		html.required = true
		csv.required = true
		xml.required = false
		html.outputLocation = layout.buildDirectory.dir("reports/coverage/html")
		csv.outputLocation = layout.buildDirectory.file("reports/coverage/coverage.csv")
	}
}

val coverageSummaryJson by tasks.registering {
	group = "verification"
	description = "Writes coverage-summary.json from the JaCoCo CSV report."
	dependsOn(coverageReport)
	val csvFile = layout.buildDirectory.file("reports/coverage/coverage.csv")
	val jsonFile = layout.buildDirectory.file("reports/coverage/coverage-summary.json")
	inputs.file(csvFile)
	outputs.file(jsonFile)
	doLast {
		val rows = csvFile.get().asFile.readLines().drop(1).filter { it.isNotBlank() }
		var instrMissed = 0L; var instrCovered = 0L
		var branchMissed = 0L; var branchCovered = 0L
		var lineMissed = 0L; var lineCovered = 0L
		for (row in rows) {
			val c = row.split(',')
			if (c.size < 9) continue
			instrMissed += c[3].toLong(); instrCovered += c[4].toLong()
			branchMissed += c[5].toLong(); branchCovered += c[6].toLong()
			lineMissed += c[7].toLong(); lineCovered += c[8].toLong()
		}
		fun pct(covered: Long, missed: Long): String {
			val total = covered + missed
			val value = if (total == 0L) 0.0 else covered * 100.0 / total
			return String.format(Locale.ROOT, "%.2f", value)
		}
		fun metric(name: String, covered: Long, missed: Long) =
			"    \"$name\": { \"covered\": $covered, \"missed\": $missed, \"percent\": ${pct(covered, missed)} }"
		val json = buildString {
			appendLine("{")
			appendLine(metric("instruction", instrCovered, instrMissed) + ",")
			appendLine(metric("branch", branchCovered, branchMissed) + ",")
			appendLine(metric("line", lineCovered, lineMissed))
			appendLine("}")
		}
		jsonFile.get().asFile.writeText(json)
		logger.lifecycle("Coverage (instructions): ${pct(instrCovered, instrMissed)}% -> ${jsonFile.get().asFile}")
	}
}

publishing {
	repositories {
		val mavenUrl = System.getenv("MAVEN_URL") ?: return@repositories
		val mavenUsername = System.getenv("MAVEN_USERNAME") ?: return@repositories
		val mavenToken = System.getenv("MAVEN_TOKEN") ?: return@repositories

		maven {
			url = uri(mavenUrl)
			credentials {
				username = mavenUsername
				password = mavenToken
			}
		}
	}

	publications {
		create<MavenPublication>("mavenNeoForge") {
			artifactId = base.archivesName.get()
			from(components["java"])
		}
	}
}

/*unifiedPublishing {
    project {
        releaseType = "${ENV["RELEASE_TYPE"] ?: "release"}"
        gameVersions = Arrays.asList(rootProject.extra["supported_versions"].toString().split(", "))
        gameLoaders = listOf("neoforge")
        displayName = "$mod_name NeoForge ${project.version}"
        changelog = "https://kubejs.com/changelog?mc=${project.extra["minecraft_version"]}"
        mainPublication(tasks.jar)

        relations {
            depends {
                curseforge = "rhino"
                modrinth = "rhino"
            }
        }

        if (ENV["CURSEFORGE_KEY"] != null) {
            curseforge {
                token = ENV["CURSEFORGE_KEY"]
                id = project.extra["curseforge_id"].toString()
            }
        }

        if (ENV["MODRINTH_TOKEN"] != null) {
            modrinth {
                token = ENV["MODRINTH_TOKEN"]
                id = project.extra["modrinth_id"].toString()
                version = project.version.toString()
            }
        }
    }
}*/

configure<org.gradle.plugins.ide.idea.model.IdeaModel> {
	module {
		if (!runningInCI) {
			isDownloadSources = true
			isDownloadJavadoc = true

			val subdirs = listOf(
				".architectury-transformer",
				"config",
				"crash-reports",
				"debug",
				"downloads",
				"dumps",
				"flashback",
				"local",
				"logs",
				"mods",
				"profilekeys",
				"saves",
				"voicechat_recordings",
				"command_history.txt",
				"options.txt",
				"server.properties",
				"world",
				"emi.json",
				"usercache.json",
				"usernamecache.json"
			)

			listOf("run", "runs/client", "runs/server").forEach { dir ->
				subdirs.forEach { ext -> excludeDirs.add(file("$dir/$ext")) }
			}
		}
	}
}
