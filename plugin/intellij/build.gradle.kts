import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
	java
	id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.uncandango.kubegroovy"
version = "0.1.0"

repositories {
	mavenCentral()
	intellijPlatform {
		defaultRepositories()
	}
}

dependencies {
	intellijPlatform {
		intellijIdea("2025.3")
		bundledPlugin("org.intellij.groovy")
		testFramework(TestFrameworkType.Platform)
	}
	testImplementation("junit:junit:4.13.2")
}

java {
	toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 21
}

tasks.test {
	useJUnit()
}
