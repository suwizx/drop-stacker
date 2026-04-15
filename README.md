# Drop Stacker

A Fabric mod for Minecraft that automatically stacks dropped item entities on the ground into a single entity, reducing entity lag and keeping your world tidy.

## Features

- Nearby dropped items of the same type automatically merge together
- Configurable max stack size (default: 1000)
- Configurable scan radius and scan interval
- Stack count label displayed above items — yellow for stacks over 64, white for 64 or below
- Server-side logic only, no client-side desync

## Requirements

- Minecraft 26.1.2
- [Fabric Loader](https://fabricmc.net/) 0.19.1+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- Java 25+

## Configuration

After the first launch, a config file is created at `.minecraft/config/drop-stacker.json`:

```json
{
  "maxStackSize": 1000,
  "scanRadiusX": 5.0,
  "scanRadiusY": 2.0,
  "scanRadiusZ": 5.0,
  "scanInterval": 5
}
```

| Field | Description | Default |
|---|---|---|
| `maxStackSize` | Maximum items per stacked entity | 1000 |
| `scanRadiusX` | Scan radius on X axis (blocks) | 5.0 |
| `scanRadiusY` | Scan radius on Y axis (blocks) | 2.0 |
| `scanRadiusZ` | Scan radius on Z axis (blocks) | 5.0 |
| `scanInterval` | Ticks between each scan | 5 |

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
