# Drop Stacker

A Fabric mod for Minecraft that automatically stacks dropped item entities on the ground into a single entity, reducing entity lag and keeping your world tidy.

## Features

- **Smart Stacking:** Dropped items of the same type and NBT automatically merge within a configurable radius.
- **Limitless Piles:** Stacks beyond vanilla's 64-item limit — up to your configured `maxStackSize`.
- **Visual Feedback:** Colorful stack count label displayed above items (configurable thresholds).
- **Despawn Timer:** Optional countdown on the label with urgency-based color coding (supports infinite items with `∞`).
- **Performance Optimized:** Smart throttling reduces network bandwidth by updating labels less frequently for "healthy" items and staggering updates across ticks.
- **Vanilla Parity:** Merging preserves the "best" state (minimum age and maximum pickup delay) to prevent despawn bugs or pickup exploits.
- **Item Blacklist:** Choose specific items that should never be stacked.
- **Server-Side Only:** All logic is handled on the server to prevent client-side desync.

## Requirements

- Minecraft 26.1.2
- [Fabric Loader](https://fabricmc.net/) 0.19.1+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- Java 25+

## Configuration

The config file is created automatically at `.minecraft/config/drop-stacker.json`. Fields update automatically on restart if you upgrade the mod.

```json
{
  "maxStackSize": 1000,
  "scanRadiusX": 5.0,
  "scanRadiusY": 2.0,
  "scanRadiusZ": 5.0,
  "scanInterval": 5,
  "showDespawnTimer": true,
  "countLowThreshold": 64,
  "countHighThreshold": 500,
  "hideSingleItemLabel": false,
  "blacklist": [],
  "despawnTicks": 6000
}
```

| Field | Description | Default |
|---|---|---|
| `maxStackSize` | Maximum items per stacked entity | `1000` |
| `scanRadiusX/Y/Z` | Scan radius on each axis (blocks) | `5.0 / 2.0 / 5.0` |
| `scanInterval` | Ticks between each merge scan | `5` |
| `showDespawnTimer` | Show despawn countdown on the label | `true` |
| `countLowThreshold` | Count at which the label turns Yellow | `64` |
| `countHighThreshold` | Count at which the label turns Red | `500` |
| `hideSingleItemLabel`| Hide the label if the stack only has 1 item | `false` |
| `blacklist` | List of item IDs to ignore (e.g. `["minecraft:diamond"]`) | `[]` |
| `despawnTicks` | World despawn rate (matches timer to server settings) | `6000` |

## Building

```bash
./gradlew build
```

Output JAR is in `build/libs/`.

## License

MIT — see [LICENSE](LICENSE).

---

> [!CAUTION]
> I am not great at Kotlin and this project was built with AI assistance. There may be bad code, wrong patterns, or things that could be done much better. If you spot anything — **please open an issue or a pull request**. I genuinely appreciate it and will learn from it. Thank you.
