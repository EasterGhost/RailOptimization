<div align="center">

<img src="src/main/resources/assets/icon.png" style="max-width:128px;max-height:128px" alt="RailOptimization Icon"/>

# RailOptimization

[![Mod Environment](https://img.shields.io/badge/Environment-Fabric-blue?style=round)](https://github.com/EasterGhost/RailOptimization)
[![Hits Of Code](https://hitsofcode.com/github/EasterGhost/RailOptimization?branch=main)](https://github.com/EasterGhost/RailOptimization)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/railoptimization?style=round&logo=modrinth)](https://modrinth.com/mod/railoptimization)

A Fabric mod that makes powered rails and activator rails update faster.

</div>

## About

RailOptimization replaces repeated vanilla powered rail signal searches with a cached propagation path. It is intended for builds with long powered rail lines, especially rail systems where many rails are updated by the same redstone change.

This fork maintains the Fabric version with bug fixes, cross-version builds, and extra GameTest coverage.

## Features

- Faster powered rail and activator rail on/off updates.
- Optimized propagation for straight rails and ascending/descending rail chains.
- Vanilla-compatible fallback boundaries for unsupported rail shapes.
- Runtime toggle command for debugging or compatibility checks.
- Configurable powered and activator rail signal range.
- GameTest coverage for straight rails, slopes, observer updates, BUD-sensitive behavior, and vanilla/optimized comparisons.

## Compatibility

Use the jar that matches your Minecraft version range:

| Jar | Minecraft versions |
| --- | --- |
| `railoptimization-fabric-mc1.20.1-*.jar` | 1.20-1.21.1 |
| `railoptimization-fabric-mc1.21.2-*.jar` | 1.21.2-1.21.4 |
| `railoptimization-fabric-mc1.21.5-*.jar` | 1.21.5-1.21.10 |
| `railoptimization-fabric-mc1.21.11-*.jar` | 1.21.11 |
| `railoptimization-fabric-mc26.1.2-*.jar` | 26.1-26.2 |

Fabric Loader and Fabric API are required.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/railoptimization` | everyone | Shows the optimization state and current power limit |
| `/railoptimization on` | admin | Enables the optimized update path |
| `/railoptimization off` | admin | Disables the optimization and uses vanilla behavior |
| `/railoptimization powerLimit <value>` | admin | Sets the runtime rail power limit from 1 to 64 (default: 8) |

## Build

```bash
./gradlew build
```

The release jar and sources jar are written to `build/libs`.

## Notes

- This mod only changes powered rail and activator rail update logic.
- If you find a redstone contraption that behaves differently from vanilla, please open an issue with the Minecraft version, mod version, and a minimal reproduction.

## Links

- [Modrinth](https://modrinth.com/mod/railoptimization)
- [GitHub](https://github.com/EasterGhost/RailOptimization)
- [Issues](https://github.com/EasterGhost/RailOptimization/issues)

This project is based on FX's Rail Optimization. Credit to the original author and contributors.
