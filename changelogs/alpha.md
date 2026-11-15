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

# v0.2.0-alpha (unreleased)
### Added
- **NetherNet Networking:** Support for NetherNet. The server now generates a ``nethernet-identity.pem`` file on startup.
<<<<<<< Updated upstream

- **Survival Statistics:** Statistics such as health, hunger, and many more have all been added.

=======
- **Survival Statistics:** Statistics such as health, hunger, and many more have all been added.
- **Terrain Generator:** The default world type now generates real terrain: rolling hills, valleys, lakes with sandy shores, stone and dirt under grass, a jagged bedrock floor, and scattered oak trees - all new with every world's seed. `level-type` in server.properties picks the world type a new level gets: `default` (now the default) for terrain, or `flat` for the flat presets, which are unchanged. New levels also make sure to spawn on dry ground.

>>>>>>> Stashed changes
### Removed
- **RakNet Networking:** Starting Bedrock v1.26.60, Mojang has completely removed RakNet from the game, replacing it with NetherNet.

### Fixed
- **Liquids such as water and lava not saving:** This bug that has been known since v0.1.0-alpha has now been fixed.
<<<<<<< Updated upstream
=======
- **Eating food didn't work:** Eating made no sound and the food stack never went down. Item uses and inventory requests that current clients carry inside their auth input packets were dropped entirely; they are now processed, so food can be eaten. The client's own consume request is honoured, and the server takes the food out of the slot exactly once.
- **Inventory desync after eating:** When the client reported its inventory no longer matching the server's, the server ignored it and the two could stay out of step. The server now sends the inventory as it has it, which settles the mismatch.
- **Lava damage was delayed:** Standing in lava took a moment before it started hurting. The damage now applies right away.
>>>>>>> Stashed changes

## ⚠️ Alpha Notice

**Netherrack is experimental software.**

This release is intended for early testing, experimentation, and feedback. Features may be incomplete, behavior may change between releases, and unexpected crashes or data loss may occur.
Do not use this release for production servers or worlds containing important data without maintaining independent backups.
If you encounter a bug, please report it through the project's issue tracker and include relevant logs, reproduction steps, and environment details whenever possible.
