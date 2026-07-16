# GuildMark

GuildMark is a client-side Fabric mod for creating local Minecraft guild lists and displaying custom guild marks on players. Assign players to guilds, set your relationship with each guild, and show its image on capes, chest armor, shields, elytra, or as a colored head marker.

## Features

- Local guild and player editor with JSON import and export
- Guild relationships: `My Guild`, `Ally`, and `Other`
- Custom PNG, JPG, and WebP marks loaded from a file, clipboard, or HTTPS URL
- Guild marks on capes, chest armor, shields, elytra, and player heads
- Player skin previews and 3D models in the map view
- One-click Auto Import of a remote guild database and its images
- English and Polish interface
- Configurable banner resolution limit
- Client-side only; a server does not need to install GuildMark

## Supported versions

| Minecraft | Java | Fabric Loader | Fabric API |
| --- | --- | --- | --- |
| 1.21.8 | 21 or newer | 0.19.3+ | Required |
| 26.1.2 | 25 or newer | 0.19.3+ | Required |

Download the JAR matching your Minecraft version from [GitHub Releases](../../releases) or the Modrinth project page.

## Installation

1. Install Fabric Loader and Fabric API for your Minecraft version.
2. Put the matching GuildMark JAR in `.minecraft/mods`.
3. Start Minecraft and open **Options → GuildMark**, or press `G` in game.

Guild data is stored in `.minecraft/config/guildmark/guilds.json`. Downloaded guild images are stored in `.minecraft/config/GuildMark/Guilds/`.

## Remote database format

```json
{
  "formatVersion": 2,
  "guilds": [
    {
      "name": "Nebula",
      "color": 10181887,
      "players": ["Steve", "Alex"],
      "markUrl": "https://cdn.example.com/nebula.webp"
    }
  ]
}
```

For remote imports, `markUrl` must use HTTPS. GuildMark downloads and validates each image, saves it locally, and fills in `markFile` and `markPath`. Existing local data stays active if an import cannot be completed.

See [auto-import-example.json](release/modrinth/auto-import-example.json) for a complete example.

## Building

The Gradle wrapper downloads the required build tools automatically.

```powershell
.\builder.bat
.\builder-26.1.2.bat
```

The scripts create:

- `release/modrinth/GuildMark-1.21.8-1.0.1.jar`
- `release/modrinth/GuildMark-26.1.2-1.0.1.jar`

Linux and macOS users can run `./gradlew clean remapJar` and `./gradlew -p versions/26.1.2 clean jar`.

## Contributing

Bug reports and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes.

## License

Copyright 2026 Slogerski. Licensed under the [Apache License 2.0](LICENSE).
