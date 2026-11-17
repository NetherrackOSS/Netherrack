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

# v0.2.0-alpha
### Added
- **NetherNet Networking:** Support for NetherNet. The server now generates a ``nethernet-identity.pem`` file on startup.
- **Survival Statistics:** Statistics such as health, hunger, and many more have all been added.
- **Terrain Generator:** The default world type now generates real terrain: rolling hills, valleys, lakes with sandy shores, stone and dirt under grass, a jagged bedrock floor, and scattered oak trees - all new with every world's seed. `level-type` in server.properties picks the world type a new level gets: `default` (now the default) for terrain, or `flat` for the flat presets, which are unchanged. New levels also make sure to spawn on dry ground.

### Removed
- **RakNet Networking:** Starting Bedrock v1.26.60, Mojang has completely removed RakNet from the game, replacing it with NetherNet.

### Fixed
- **Liquids such as water and lava not saving:** This bug that has been known since v0.1.0-alpha has now been fixed.
- **Eating food didn't work:** Eating made no sound and the food stack never went down. Item uses and inventory requests that current clients carry inside their auth input packets were dropped entirely; they are now processed, so food can be eaten. The client's own consume request is honoured, and the server takes the food out of the slot exactly once.
- **Inventory desync after eating:** When the client reported its inventory no longer matching the server's, the server ignored it and the two could stay out of step. The server now sends the inventory as it has it, which settles the mismatch.
- **Lava damage was delayed:** Standing in lava took a moment before it started hurting. The damage now applies right away.

## What Has Not Been Tested
- Terrain beyond a single biome: no caves, ores, flowers, or biome variety yet - every chunk is plains.
- World type switching for levels that already exist: `level-type` only decides what a *new* level is made as.
- Infinite worlds created before this release will suddenly grow terrain when their empty chunks are next generated.
- Server stability under prolonged use, and behavior under heavy chunk-generation workloads.
- Compatibility with every supported Minecraft: Bedrock Edition client version.

This list is not exhaustive. Additional bugs and limitations may be discovered during testing.

# v0.3.0-alpha

### Added
- **Crafting recipes:** Crafting works. All 2,708 crafting recipes a crafting table or the inventory screen's 2x2 grid can make are taken straight from CloudburstMC's data repository for this protocol version, sent to the client as it joins, and enforced by the server: a craft request names its recipe, the server checks the grid really holds it laid out (shaped recipes either way round, as vanilla accepts both), uses the ingredients up, and hands out the result - four sticks from two planks, a hoe from planks and sticks, everything from acacia planks to lingering-potion tipped arrows. Ingredient tags (any planks, any wool, any logs) work as vanilla's do, and the few recipes whose outputs carry saved data, like banners and firework rockets, make it with it. The client's recipe book, output preview and auto-fill run on its side; shift-clicking a recipe to craft a stack at once isn't supported yet. Furnace, stonecutter and smithing recipes wait for their screens.
- **Crafting table:** Right-clicking a crafting table now opens its screen, with the 3x3 crafting grid instead of the inventory screen's 2x2 - laid out the way Nukkit opens the workbench: not a container of its own but the player's UI screen with a bigger grid, placed at the table itself. Items can be laid out in the grid and taken back, and recipes can be crafted from it as from the 2x2. Whatever is left in the grid goes back into the inventory when the screen closes, drops if there is no room, spills where a player dies with it open, and is saved with the rest of their things. Opening the table is guarded like any used block: within reach, and not for visitors or spectators.
- **PvP:** Players can now fight each other. A hit lands when the attacker is close enough - vanilla's three blocks of reach, double that in creative - and does what the held item does, as vanilla Bedrock counts damage: a fist 1, a sword or axe its tier's 4 through 8, a pickaxe or shovel a little less, anything else 1. A hit sends its target flying back and plays the hurt flash and sound, and if it takes their last half heart, the death screen and the chat say who dealt the blow. Every blow also respects the hurt the one before it left: for ten ticks, anything of the same strength or weaker is shrugged off, and a stronger blow lands for only the difference, so hammering the attack button isn't a stream of damage. Creative players can't be hit by other players, and spectators can't attack anyone.
- **Death drops:** When a player dies, everything they carry - their inventory, armor, offhand, and anything left on the cursor or in the crafting grid - drops where they fell, in every game mode and for every cause of death, `/kill` included. Each stack scatters out a little as it falls, waits a moment before it can be picked up, and despawns after five minutes like any other item: run back for your things before someone else does. Respawning no longer keeps what you had.

### Fixed
- **Ghost fall damage in creative mode:** A player flying in creative could take fall damage and flash red mid-air for no visible reason. The server never tracked the client's flight state: the client announces starting and stopping flight (inside its auth input, or through ability requests), and the server ignored both. It now keeps that state and echoes it back in its abilities packets - a client told it isn't flying drops out of the sky at once, even mid-air, which is what let the ghost damage in. Fall distance is also no longer measured while the player is flying.
- **Other players always showed as survival mode:** The packet that shows a player to everyone else hardcoded survival as their game mode; it now shows their real one.
- **Dying gave your items straight back:** Your own death drops could be picked up again - by you, while still on the death screen, since a dead player stays registered where they fell. Dead players now pick up nothing, as Nukkit's pickup rules do; anyone else standing by can still take their share.
- **A dead player's corpse never went away:** Everyone else kept seeing the body where the player fell, and when the player respawned, the corpse teleported to the spawn and moved around with them - alive-looking to themselves, dead to everyone else. The body now stays where they fell for a second, long enough for the death animation to play, and is then despawned from everyone's view until they respawn and are shown again, fresh, as Nukkit does.

## What Has Not Been Tested
- Fighting between players: whether hits land, how the knockback feels, and the deaths they cause.
- Crafting with a real client: whether every recipe's book entry, preview and result behave as vanilla's.
- Items dropped into lava or fire are not destroyed yet; they sit there until picked up or despawned.
- Death drops landing in unloaded chunks or in the void.
- Dying while flying, or while a chest-like screen is open.
- Compatibility with every supported Minecraft: Bedrock Edition client version.
- Server stability under prolonged use.

This list is not exhaustive. Additional bugs and limitations may be discovered during testing.

## ⚠️ Alpha Notice

**Netherrack is experimental software.**

This release is intended for early testing, experimentation, and feedback. Features may be incomplete, behavior may change between releases, and unexpected crashes or data loss may occur.
Do not use this release for production servers or worlds containing important data without maintaining independent backups.
If you encounter a bug, please report it through the project's issue tracker and include relevant logs, reproduction steps, and environment details whenever possible.
