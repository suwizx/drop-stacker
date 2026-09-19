import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom-remap")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.4.20"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
}

loom {
	splitEnvironmentSourceSets()

	// The mixins are Kotlin, so the Mixin annotation processor never runs and no refmap is made.
	// Without this, the release jar keeps Mojang names in @Inject/@At strings and every mixin
	// fails to find its target once remapped to intermediary. With it, tiny-remapper rewrites the
	// annotation strings directly in the remapped jar.
	mixin {
		useLegacyMixinAp = false
	}

	mods {
		register("drop-stacker") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

fabricApi {
	configureDataGeneration {
		client = true
	}

	// Server gametests live in their own `gametest` source set and mod, so none of it ships in the
	// release jar. Loom wires `runGameTest` into `check`, which means `./gradlew build` runs them.
	configureTests {
		createSourceSet = true
		modId = "drop-stacker-gametest"
		enableGameTests = true
		enableClientGameTests = false
		eula = true
	}
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	// 1.21.1 is still obfuscated: sources use Mojang names and Loom remaps the jar to intermediary.
	mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
}

tasks.processResources {
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 21
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
	}
}

java {
	// Minecraft 1.21.1 runs on Java 21. Pinning the toolchain means compilation and the run tasks all
	// use it, rather than silently falling back to whatever JVM Gradle happens to be running on.
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}

	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
	inputs.property("projectName", project.name)

	from("LICENSE") {
		rename { "${it}_${project.name}" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
