# GuildMark 1.0.2

- Auto Import now remembers the last valid HTTPS guild database URL.
- Added an `AUTO UPDATE` switch, enabled by default, with green/red ON and OFF states.
- When enabled, GuildMark checks the saved database URL once at Minecraft startup and downloads updates at most once every 24 hours.
- The automatic check runs asynchronously and does not block the main menu or joining a server.
- A downloaded database replaces the active guild list only after its JSON and every referenced image have been downloaded and validated successfully.
- Remote `markUrl` values are preserved for future updates while `markFile` and `markPath` point to locally stored PNG files.
- Failed imports keep the existing guild database and images active.
- GitHub-hosted imports use HTTP/1.1 and retry once after transient connection failures such as `ClosedChannelException`.
- Added a cosmetic render-distance slider from 10 to 256 blocks plus `Unlimited`; the default is 128 blocks.
- Distant guild capes, chest marks, head markers, shields and elytra layers are rejected before texture lookup or draw submission.
- Player-to-guild lookups now use a case-insensitive index instead of scanning every guild member each frame.
- Guild textures remain shared between players and now use an LRU cache limited to 128 entries and approximately 256 MiB.
- Added a nearest-player cosmetic limit with 8, 16, 32, 64, 128 and `Unlimited` options; the default is 64.
- The nearest eligible guild players are recalculated only once every 10 client ticks, while the local player remains eligible independently of the limit.
