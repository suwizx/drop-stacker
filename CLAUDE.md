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

**Core flow:** merging is *push*-based. `ServerLevelMixin` hooks `ServerLevel.addFreshEntity` so a new drop joins a nearby pile in the tick it spawns (`StackEngine.joinNeighbours`: the newcomer is drained into the existing pile, never the other way round). `ItemEntityMixin` also runs a periodic scan as a safety net (for items that moved, or loaded from disk), with an adaptive backoff that slows idle piles down to `maxScanInterval`. All merging is server-side; a vanilla client needs no mod.

**Key files:**
- `stack/StackEngine.kt` — all merge logic; the only place that scans for neighbours
- `stack/StackLabel.kt` — builds the name tag from `labelFormat`/`timerFormat` (`{placeholders}` and `&` codes; the default renders `[×count | m:ss]`), plus the `labelKey` change-detection scalar
- `stack/LabelVisibility.kt` — adds the "discrete" flag to entity data *sent to clients* when `labelThroughWalls` is off; applied by `ServerEntityMixin` to both the pairing and the update packet
- `mixin/ItemEntityMixin.kt` — tick scan, label refresh, and the `isMergable`/`tryToMerge`/`mergeWithNeighbours` overrides
- `mixin/ServerLevelMixin.kt` — instant merge-on-spawn
- `mixin/EntityMixin.kt`, `HandPickupMixin.kt`, `HopperBlockEntityMixin.java`, `SimpleContainerMixin.kt` — make every pickup path see one vanilla-sized stack of a pile at a time (see the pickup invariant). The hopper mixin is Java because its target is static
- `mixin/ServerEntityMixin.java`, `EntityAccessor.java` — the discrete-flag hook and its static accessor (Java for the same reasons: a static accessor, and a Kotlin `@Shadow` of a final field would need an initializer)
- `accessor/ItemEntityAccessor.kt` — plain Kotlin interface (not `@Accessor`) mixed into `ItemEntity`, exposing private `age`/`pickupDelay` and the mod's per-entity state
- `config/DropStackerConfig.kt` — GSON-backed config, with migration from the 1.0.x `scanRadiusX/Y/Z` keys
- `config/ItemRules.kt` — resolves blacklist/whitelist/overrides from strings to `Item` refs and `TagKey`s once, so the scan predicate allocates nothing
- `command/DropStackerCommand.kt` — `/dropstacker`
- `DropStacker.kt` — entrypoint; loads config, registers the command, invalidates `ItemRules` on datapack reload

**Performance invariants** (these are the reasons the code looks the way it does — don't undo them):
- Scans stagger by `(tickCount + entity.id)`, never bare `tickCount`, or every drop from one mob scans on the same tick.
- Never call `BuiltInRegistries.ITEM.getKey(...).toString()` in the scan predicate; use the resolved `ItemRules` sets.
- Use the `Level.getEntities(test, box, predicate, outList, limit)` overload with the shared scratch list — the `getEntitiesOfClass` variant allocates a fresh `ArrayList` of every match.
- Merging must produce a **new** `ItemStack` and go through `setItem`. `SynchedEntityData.set` compares by equality, so mutating the tracked stack's count in place never syncs to clients. The one deliberate exception is `StackEngine.withVanillaSizedPile`, which lowers the count in place and restores it in a `finally` before the tick ends, so clients never see the temporary value and a pickup that takes nothing sends no packet.
- The receiver of a merge is the **biggest** pile in range, not whichever entity scans first, or a single item pulls a whole pile over to itself. Thrown items (those with a thrower) only merge once on the ground or in water, and drops only merge when their `target` is equal.
- Rebuild the label only when `StackLabel.labelKey` changes; each rebuild is a component tree plus a packet to every tracking player.

**Persistence invariant:** vanilla's disk codec for `ItemStack` only accepts counts 1–99, so `ItemEntityMixin` saves piles above 99 as a capped `Item` plus the true count under `DropStackerCount` (`StackEngine.SAVED_COUNT_KEY`), and restores it on load. Without those hooks, any pile over 99 is deleted on the next load. The network codec has no cap, so this stays server-side only. Don't remove or rename the key.

The mod's own label is saved as the entity's custom name, so `ItemEntityMixin` also writes `DropStackerLabel` (`StackEngine.SAVED_LABEL_KEY`) when the saved name is the mod's label, and re-adopts it on load. Without it a reloaded label looks like a real name: it freezes and the pile never merges again. Don't remove or rename this key either.

**Pickup invariant:** vanilla never sees a ground stack above `maxStackSize`, and several pickup paths break on one: hoppers and `SimpleContainer` (villagers, allays, piglins) trim it to the slot limit and delete the rest, dolphins/pandas/raiders hold it in a hand slot that cannot be saved above 99, and Ledger serialises pickups with the vanilla codec (1–99). So every pickup, players included, goes through `StackEngine.withVanillaSizedPile`, which shows one stack, keeps the entity alive via `EntityMixin` while it is "collecting", and puts the rest back. Don't pass a whole oversized pile to vanilla code.

**Source sets:** The project uses Fabric Loom's split environment feature. `src/main` is common/server-side; `src/client` is client-only and currently empty. Mixins are declared separately in `drop-stacker.mixins.json` (server) and `drop-stacker.client.mixins.json` (client).

**Testing:** **run `./gradlew build` after every change** — Loom wires `runGameTest` into `check`, so it runs the Fabric server gametests headlessly and fails the build on any failing test. `./gradlew runGameTest` runs just the tests. Tests live in the separate `gametest` source set (`src/gametest/kotlin/.../gametest/DropStackerGameTests.kt`, its own `drop-stacker-gametest` mod), so none of it ships in the release jar. Add a gametest with every fix or feature; for a bug, write the test first and watch it fail. Note that in 26.3 entity type constants live in `EntityTypes`, not `EntityType`.

For manual testing beyond the gametests, enable RCON in `run/server.properties` and drive `./gradlew runServer` with `/summon item ...` commands. Note that a dev server with **no player connected does not tick item entities reliably** — merge-on-spawn is testable headlessly, but the periodic tick scan needs `runClient` with a player in range.

## Minecraft version

Targets Minecraft `26.3` with Java 25 (Gradle provisions the JDK 25 toolchain via the foojay resolver in `settings.gradle.kts`). The `minecraft_version` in `gradle.properties` controls which Minecraft mappings and API version Loom uses.

Mappings are **Mojang official**, not Yarn — there is no `mappings(...)` line in `build.gradle.kts`. Note `ResourceLocation` is named `Identifier` in this era, and Loom 1.17 has no `modImplementation` (intermediary is `0.0.0`, so mod deps are consumed unremapped via plain `implementation`).
