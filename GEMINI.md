# Drop Stacker - Project Context

## Project Overview
Drop Stacker is a Fabric mod for Minecraft 26.1.2 written in Kotlin. It automatically stacks dropped item entities on the ground into a single entity to reduce server/client entity lag and keep worlds tidy. Merging logic is executed strictly on the server-side to prevent desyncs. The mod utilizes Mixins to hook into the `ItemEntity` tick logic and handles the stacking of items with the same type and NBT components up to a configurable limit.

## Architecture & Key Components
- **Core Logic:** `src/main/kotlin/dev/suwizx/dropstacker/mixin/ItemEntityMixin.kt` handles the primary stacking logic. It scans nearby entities at a configured interval and merges compatible item entities.
- **Configuration:** `src/main/kotlin/dev/suwizx/dropstacker/config/DropStackerConfig.kt` manages user settings using Gson. The configuration is loaded from and saved to `.minecraft/config/drop-stacker.json`.
- **Mod Entrypoints:** `src/main/kotlin/dev/suwizx/dropstacker/DropStacker.kt` (Common/Server) and client-specific entrypoints in `src/client/kotlin/`.
- **Source Sets:** The project utilizes Fabric Loom's split environment feature. `src/main` contains common and server-side code, while `src/client` contains client-only code.

## Building and Running
The project uses Gradle wrapper with the Fabric Loom plugin.
- **Build the mod:** `./gradlew build` (The output JAR will be located in `build/libs/`)
- **Run the Minecraft client:** `./gradlew runClient`
- **Run the Minecraft server:** `./gradlew runServer`
- **Run data generation:** `./gradlew runDatagen`
- **Generate Minecraft sources for IDE:** `./gradlew genSources`

## Development Conventions & Best Practices
- **Text Formatting:** Always use the modern Minecraft `Component` API for text and labels (e.g., `Component.empty().append(Component.literal("...").withStyle(ChatFormatting.RED))`). Do **not** use legacy `§` formatting codes.
- **Logging:** Utilize SLF4J (`LoggerFactory.getLogger(...)`) for logging instead of standard console output (`println`).
- **Kotlin Mixins Casting:** When you need to cast `this` to the target Minecraft class within a Kotlin Mixin, use the double-cast pattern `this as Any as TargetClass` (e.g., `val entity = this as Any as ItemEntity`) to prevent Kotlin compiler warnings about impossible casts.
- **Server-Side Authority:** Keep entity manipulation, merging, and core game logic on the server side to ensure stability and prevent client-server desynchronization. Visual updates (like updating the custom name for stack counts and timers) are handled on the server and synced to the client automatically.
- **Target Versions:** The project is configured for Java 25 and Kotlin 2.3.20, targeting Minecraft 26.1.2.
