# GuildMark

**GuildMark** is a client-side Fabric mod that displays custom guild cosmetics and makes guild members easy to recognize in-game.

Create and manage a private guild list through the built-in interface, or import a complete community database from an HTTPS address. No server installation is required.

## Features

- High-resolution guild marks on capes, chests, shields and elytra
- Configurable head markers for your guild and allied guilds
- Custom colors for `MY` and `ALLY` head markers
- Local guild editor with player management and search
- Guild map with small 3D player previews
- Per-guild relationships: `MY`, `ALLY` and `OTHER`
- Global or per-guild cosmetic visibility settings
- PNG, JPG and WEBP support
- Image loading from files, links and the clipboard
- JSON import and export through the clipboard
- Automatic guild database import from HTTPS
- Optional daily guild database updates at Minecraft startup
- Configurable cosmetic render distance and bounded shared texture cache
- Configurable nearest-player cosmetic limit for crowded servers
- English and Polish interface
- Fully client-side

## Auto Import

The **Auto Import** panel downloads a JSON guild database and every image referenced through `markUrl`. Images are validated and stored locally before the active guild list is replaced.

A remote entry can be as simple as:

```json
{
  "name": "Nebula",
  "players": ["Steve", "Alex"],
  "markUrl": "https://example.com/nebula.webp",
  "relation": "ally"
}
```

After importing, GuildMark stores the source URL together with the generated local filename and path. If any required file fails to download or validate, the existing guild list remains active.

The saved URL can also be checked automatically when Minecraft starts. Auto Update is enabled by default and runs at most once every 24 hours. It works in the background, so it does not block the main menu or joining a server.

## Local files

Guild data:

```text
.minecraft/config/guildmark/guilds.json
```

Downloaded guild images:

```text
.minecraft/config/GuildMark/Guilds/
```

Language and head-marker color settings:

```text
.minecraft/config/guildmark/settings.json
```

## Privacy

Guild lists, relationships, images and preferences are stored locally. GuildMark does not require Microsoft account credentials, passwords or private server data.

The mod only changes rendering on your client. It does not modify server-side data, combat mechanics or what other players see.

## Requirements

- Minecraft `1.21.8` or `26.1.2` (use the matching mod file)
- Fabric Loader `0.19.3` or newer
- Fabric API
- Client-side installation only

## Contact

Guild database submissions and project contact: **Slogerski**
