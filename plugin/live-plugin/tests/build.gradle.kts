import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Properties

plugins {
	java
	id("org.jetbrains.intellij.platform")
}

val fixtureVersions = Properties().apply {
	file("../../../gradle.properties").inputStream().use { load(it) }
}

group = fixtureVersions.getProperty("group")
version = fixtureVersions.getProperty("modVersion")

repositories {
	mavenCentral()
	intellijPlatform {
		defaultRepositories()
	}
}

dependencies {
	intellijPlatform {
		val ideHome = providers.environmentVariable("INTELLIJ_HOME")
		if (ideHome.isPresent) local(ideHome.get())
		else intellijIdea(fixtureVersions.getProperty("livePluginIdeVersion"))
		bundledPlugins("com.intellij.java", "org.intellij.groovy")
		testFramework(TestFrameworkType.Platform)
	}
	testImplementation("junit:junit:${fixtureVersions.getProperty("livePluginJUnitVersion")}")
}

java {
	toolchain.languageVersion = JavaLanguageVersion.of(fixtureVersions.getProperty("livePluginJavaVersion").toInt())
}

sourceSets.test {
	java.setSrcDirs(listOf(projectDir))
	java.include("*.java")
}

tasks.test {
	useJUnit()
	maxHeapSize = "1G"
	inputs.files(fileTree("..") { include("*/plugin.groovy") })
	systemProperty("kubegroovy.livePluginRoot", projectDir.parentFile.absolutePath)
}