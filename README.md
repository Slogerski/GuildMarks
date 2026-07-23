# GuildMark

GuildMark is a client-side Fabric mod for creating local Minecraft guild lists and displaying custom guild marks on players. Assign players to guilds, set your relationship with each guild, and show its image on capes, chest armor, shields, elytra, or as a colored head marker.

## Features

- Local guild and player editor with JSON import and export
- Guild relationships: `My Guild`, `Ally`, and `Other`
- Custom PNG, JPG, and WebP marks loaded from a file, clipboard, or HTTPS URL
- Guild marks on capes, chest armor, shields, elytra, and player heads
- Player skin previews and 3D models in the map view
- One-click Auto Import of a remote guild database and its images
- Optional daily Auto Update from the saved database URL at Minecraft startup
- English and Polish interface
- Configurable banner resolution limit
- Configurable 10–256 block cosmetic render distance with an Unlimited option
- Configurable nearest-player cosmetic limit: 8, 16, 32, 64, 128, or Unlimited
- Shared texture cache with automatic memory and entry limits
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

See [auto-import-example.json](examples/auto-import-example.json) for a complete example.

## Network communication

GuildMark has no analytics or telemetry. Network requests are limited to features visible in the interface:

- Remote import downloads the JSON address selected by the user and the image addresses listed in that JSON.
- Player previews request public Minecraft profile and skin data from Mojang services.
- On `mcextreme.pl`, opening Auto Import with no saved API address checks the displayed recommended API address once and asks before saving it. On a supported dedicated server, GuildMark downloads the available cape catalog and active public player assignments from the API address accepted or saved by the user.
- Selecting a dedicated-server cape sends the cape ID to that API, submits the Minecraft session proof directly to Mojang through Minecraft's session service, and then sends the returned challenge ID with the public Minecraft username to the API.

The Microsoft/Minecraft access token is passed only to Minecraft's official session service and is never included in a request to the configured GuildMark API. The dedicated API never receives a password. HTTP API addresses are supported temporarily but are shown as unencrypted in the interface; HTTPS is recommended.

## Building

The Gradle wrapper downloads the required build tools automatically.

```powershell
.\builder.bat
.\builder-26.1.2.bat
```

The scripts create:

- `release/modrinth/GuildMark-1.21.8-1.1.1.jar`
- `release/modrinth/GuildMark-26.1.2-1.1.1.jar`

Linux and macOS users can run `./gradlew clean remapJar` and `./gradlew -p versions/26.1.2 clean jar`.

## Contributing

Bug reports and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes.

## License

Copyright 2026 Slogerski. Licensed under the [Apache License 2.0](LICENSE).
