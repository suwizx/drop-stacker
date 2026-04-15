# Drop Stacker

A Fabric mod for Minecraft that automatically stacks dropped item entities on the ground into a single entity, reducing entity lag and keeping your world tidy.

## Features

- Dropped items of the same type and NBT automatically merge within a configurable radius
- Stacks beyond vanilla's 64-item limit — up to your configured `maxStackSize`
- Colorful stack count label displayed above items:
  - 🟢 Green — 64 or below
  - 🟡 Yellow — 65 to 500
  - 🔴 Red — above 500
- Optional despawn countdown timer on the label:
  - 🟢 Green — plenty of time remaining
  - 🟡 Yellow — under 2 minutes
  - 🔴 Red — under 30 seconds
- Server-side merge logic only, no client-side desync
- New config fields are added automatically on server restart — no manual editing needed

## Requirements

- Minecraft 26.1.2
- [Fabric Loader](https://fabricmc.net/) 0.19.1+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- Java 25+

## Configuration

Config file is created automatically at `.minecraft/config/drop-stacker.json` on first launch:

```json
{
  "maxStackSize": 1000,
  "scanRadiusX": 5.0,
  "scanRadiusY": 2.0,
  "scanRadiusZ": 5.0,
  "scanInterval": 5,
  "showDespawnTimer": true
}
```

| Field | Description | Default |
|---|---|---|
| `maxStackSize` | Maximum items per stacked entity | `1000` |
| `scanRadiusX` | Scan radius on X axis (blocks) | `5.0` |
| `scanRadiusY` | Scan radius on Y axis (blocks) | `2.0` |
| `scanRadiusZ` | Scan radius on Z axis (blocks) | `5.0` |
| `scanInterval` | Ticks between each scan | `5` |
| `showDespawnTimer` | Show despawn countdown on the label | `true` |

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
