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

This is a Fabric mod for Minecraft 26.3 written in Kotlin that automatically stacks dropped item entities on the ground.

**Core flow:** merging is *push*-based. `ServerLevelMixin` hooks `ServerLevel.addFreshEntity` so a new drop is absorbed into a nearby pile in the tick it spawns. `ItemEntityMixin` also runs a periodic scan as a safety net (for items that moved, or loaded from disk), with an adaptive backoff that slows idle piles down to `maxScanInterval`. All merging is server-side; a vanilla client needs no mod.

**Key files:**
- `stack/StackEngine.kt` — all merge logic; the only place that scans for neighbours
- `stack/StackLabel.kt` — builds the `[×count | m:ss]` name tag, plus the `labelKey` change-detection scalar
- `mixin/ItemEntityMixin.kt` — tick scan, label refresh, and the `isMergable`/`tryToMerge`/`mergeWithNeighbours` overrides
- `mixin/ServerLevelMixin.kt` — instant merge-on-spawn
- `accessor/ItemEntityAccessor.kt` — plain Kotlin interface (not `@Accessor`) mixed into `ItemEntity`, exposing private `age`/`pickupDelay` and the mod's per-entity state
- `config/DropStackerConfig.kt` — GSON-backed config, with migration from the 1.0.x `scanRadiusX/Y/Z` keys
- `config/ItemRules.kt` — resolves blacklist/whitelist/overrides from strings to `Item` refs and `TagKey`s once, so the scan predicate allocates nothing
- `command/DropStackerCommand.kt` — `/dropstacker`
- `DropStacker.kt` — entrypoint; loads config, registers the command, invalidates `ItemRules` on datapack reload

**Performance invariants** (these are the reasons the code looks the way it does — don't undo them):
- Scans stagger by `(tickCount + entity.id)`, never bare `tickCount`, or every drop from one mob scans on the same tick.
- Never call `BuiltInRegistries.ITEM.getKey(...).toString()` in the scan predicate; use the resolved `ItemRules` sets.
- Use the `Level.getEntities(test, box, predicate, outList, limit)` overload with the shared scratch list — the `getEntitiesOfClass` variant allocates a fresh `ArrayList` of every match.
- Merging must produce a **new** `ItemStack` and go through `setItem`. `SynchedEntityData.set` compares by equality, so mutating the tracked stack's count in place never syncs to clients.
- Rebuild the label only when `StackLabel.labelKey` changes; each rebuild is a component tree plus a packet to every tracking player.

**Persistence invariant:** vanilla's disk codec for `ItemStack` only accepts counts 1–99, so `ItemEntityMixin` saves piles above 99 as a capped `Item` plus the true count under `DropStackerCount` (`StackEngine.SAVED_COUNT_KEY`), and restores it on load. Without those hooks, any pile over 99 is deleted on the next load. The network codec has no cap, so this stays server-side only. Don't remove or rename the key.

**Source sets:** The project uses Fabric Loom's split environment feature. `src/main` is common/server-side; `src/client` is client-only and currently empty. Mixins are declared separately in `drop-stacker.mixins.json` (server) and `drop-stacker.client.mixins.json` (client).

**Testing:** **run `./gradlew build` after every change** — Loom wires `runGameTest` into `check`, so it runs the Fabric server gametests headlessly and fails the build on any failing test. `./gradlew runGameTest` runs just the tests. Tests live in the separate `gametest` source set (`src/gametest/kotlin/.../gametest/DropStackerGameTests.kt`, its own `drop-stacker-gametest` mod), so none of it ships in the release jar. Add a gametest with every fix or feature; for a bug, write the test first and watch it fail. Note that in 26.3 entity type constants live in `EntityTypes`, not `EntityType`.

For manual testing beyond the gametests, enable RCON in `run/server.properties` and drive `./gradlew runServer` with `/summon item ...` commands. Note that a dev server with **no player connected does not tick item entities reliably** — merge-on-spawn is testable headlessly, but the periodic tick scan needs `runClient` with a player in range.

## Minecraft version

Targets Minecraft `26.3` with Java 25 (Gradle provisions the JDK 25 toolchain via the foojay resolver in `settings.gradle.kts`). The `minecraft_version` in `gradle.properties` controls which Minecraft mappings and API version Loom uses.

Mappings are **Mojang official**, not Yarn — there is no `mappings(...)` line in `build.gradle.kts`. Note `ResourceLocation` is named `Identifier` in this era, and Loom 1.17 has no `modImplementation` (intermediary is `0.0.0`, so mod deps are consumed unremapped via plain `implementation`).
