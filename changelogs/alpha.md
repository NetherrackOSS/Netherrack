# v0.1.0-alpha

### Added
- **LevelDB world persistence:** World data can persist across complete server restarts.
- **Flat world types:** Support for multiple flat-world presets, including classic flat, redstone-ready, and void worlds.
- **Chunk loading:** Chunks can load as players move through the world.
- **Player connection messages:** Joining and leaving players generate messages in both the in-game chat and server console.
- **Server shutdown handling:** A dedicated `Server shutdown` thread handles the shutdown process.
- **Basic gameplay functionality:** Initial support for joining worlds, player movement, and basic block interactions.

### Changed
- **Shutdown behavior:** Fixed an issue where executing `/stop` could prevent the server from shutting down properly.
- **Server logging:** Improved visibility into player connections and disconnections through chat and console messages.
- **World handling:** Improved support for loading and maintaining world data during gameplay.

## What Has Not Been Tested
- Server stability under prolonged use.
- Performance with large numbers of simultaneous players.
- Shutdown behavior under every possible condition.
- World persistence under unexpected crashes or forced process termination.
- Compatibility with all supported Minecraft: Bedrock Edition client versions.
- Reliability of all gameplay mechanics and block interactions.
- Behavior under heavy chunk-generation and loading workloads.

This list is not exhaustive. Additional bugs and limitations may be discovered during testing.

## Alpha Notice

**Netherrack v0.1.0-alpha is experimental software.**

This release is intended for early testing, experimentation, and feedback. Features may be incomplete, behavior may change between releases, and unexpected crashes or data loss may occur.
Do not use this release for production servers or worlds containing important data without maintaining independent backups.
If you encounter a bug, please report it through the project's issue tracker and include relevant logs, reproduction steps, and environment details whenever possible.
