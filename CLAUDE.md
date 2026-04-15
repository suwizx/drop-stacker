# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build the mod
./gradlew build

# Run the Minecraft client with the mod loaded
./gradlew runClient

# Run the Minecraft server with the mod loaded
./gradlew runServer

# Run data generation
./gradlew runDatagen

# Generate Minecraft sources for IDE navigation
./gradlew genSources
```

The output JAR is in `build/libs/`.

## Architecture

This is a Fabric mod for Minecraft 26.1.2 written in Kotlin that automatically stacks dropped item entities on the ground.

**Core flow:** `ItemEntityMixin` hooks into `ItemEntity` tick logic to periodically scan nearby entities (every `scanInterval` ticks), find items of the same type and NBT components, and merge them up to `maxStackSize`. Merging is server-side only to prevent desync.

**Key files:**
- `ItemEntityMixin.kt` — all core stacking logic via Mixin injections and redirects
- `ItemEntityAccessor.kt` — Mixin `@Accessor` interface to expose `updateStackLabel` to the mixin
- `DropStackerConfig.kt` — GSON-backed config loaded from `.minecraft/config/drop-stacker.json`
- `DropStacker.kt` — mod entrypoint, loads config on init

**Source sets:** The project uses Fabric Loom's split environment feature. `src/main` is common/server-side; `src/client` is client-only. Mixins are declared separately in `drop-stacker.mixins.json` (server) and `drop-stacker.client.mixins.json` (client).

**Mixin refmap:** `drop-stacker.refmap.json` — generated at build time, maps obfuscated names.

**Config defaults:** `maxStackSize=1000`, scan radius `5x2x5` blocks, `scanInterval=5` ticks. Config auto-creates with defaults if missing.

**Visual feedback:** Stacked items display their count as a custom name — yellow for counts >64, white for ≤64.

## Minecraft version

Targets Minecraft `26.1.2` with Java 25. The `minecraft_version` in `gradle.properties` controls which Minecraft mappings and API version Loom uses.
