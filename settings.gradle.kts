pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		mavenCentral()
		gradlePluginPortal()
	}

	plugins {
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
	}
}

// Lets Gradle provision the Java 25 toolchain this Minecraft version needs, so the build and the
// runClient/runServer tasks work without a system-wide JDK 25 install.
plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Should match your modid
rootProject.name = "drop-stacker"
