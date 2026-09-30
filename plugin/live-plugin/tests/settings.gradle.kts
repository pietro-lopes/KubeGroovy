pluginManagement {
	val fixtureVersions = java.util.Properties().apply {
		file("../../../gradle.properties").inputStream().use { load(it) }
	}
	repositories {
		gradlePluginPortal()
	}
	plugins {
		id("org.jetbrains.intellij.platform") version fixtureVersions.getProperty("livePluginPlatformPluginVersion")
	}
}

rootProject.name = "kubegroovy-live-plugin-fixtures"