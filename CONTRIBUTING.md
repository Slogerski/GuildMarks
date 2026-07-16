# Contributing to GuildMark

Thank you for helping improve GuildMark.

## Before opening a pull request

1. Keep changes focused and explain the player-facing result.
2. Build every Minecraft version affected by the change.
3. Do not commit generated folders, local configuration, logs, or JAR files.
4. Do not include account data, server credentials, or private guild databases.

## Build checks

For Minecraft 1.21.8 (Java 21):

```powershell
.\gradlew.bat clean remapJar --console=plain
```

For Minecraft 26.1.2 (Java 25):

```powershell
.\gradlew.bat -p versions\26.1.2 clean jar --console=plain
```

Both commands must finish successfully before a pull request is submitted.

## License

By contributing, you agree that your contribution is licensed under the Apache License 2.0 used by this project.
