# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project

Netherrack is an experimental vibe coded Minecraft: Bedrock Edition server software project
focused on learning, experimentation, and building a server implementation from the ground up.

- Language: Java 17
- Build: Maven (shaded fat JAR, `finalName` = `netherrack`)
- Network library: [CloudburstMC/Network](https://github.com/CloudburstMC/Network)
  (NetherNet transport via `netty-transport-nethernet` / libdatachannel)
- Protocol library: [CloudburstMC/Protocol](https://github.com/CloudburstMC/Protocol)
  (targets Bedrock 1.26.60, codec `Bedrock_v2225`)
- Storage: LevelDB (`org.cloudburstmc` leveldb 1.1.0)
- License: GPL-3.0

## Build & Run

```bash
# Compile and package (produces the executable fat JAR target/netherrack.jar)
bash mvnw clean package -DskipTests

# Quick syntax check only
bash mvnw clean compile
```

- Use `bash mvnw ...` — the wrapper script is not executable on Linux.
- There is currently no automated test suite in the repo. Verify changes by starting the
  server and connecting with a Bedrock Edition client (default port `19132`).
- Run the server with the JVM flags `start.sh` uses: java may need
  `--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED` for the
  NetherNet native transport.

## Running for verification (smoke tests)

- A first run with no config launches an interactive setup wizard (language prompt, then a
  license prompt that must be answered `y`). It consumes lines from stdin — account for that
  when piping commands to the console.
- `server.properties` keys include: `server-port`, `server-ip`, `level-name`, `level-type`
  (`default` = generated terrain, the default; `flat` = flat presets, which must keep working),
  `gamemode`, `difficulty`, `max-players`, `view-distance`.
- `netherrack.yml` holds Netherrack's own settings, including `level.preset-type` (which flat
  preset new flat levels use) and `messages.*`.
- Worlds live in `worlds/<level-name>/`: `level.dat` (see `LevelDat`), `levelname.txt`, `db/`.
- A world's generator type in `level.dat`: 0 = old, 1 = infinite (terrain generator),
  2 = flat. `level-type` only affects worlds created new; existing levels keep theirs.

## Codebase layout

- `src/main/java/com/netherrack/server/`
  - `Netherrack.java` — main class, startup, world bootstrapping.
  - `block/` — block types, placement, breaking, drops, `Blocks` (block registry),
    `BlockID` (name constants).
  - `command/` — the command framework (`CommandManager`, `Command`, built-in commands).
  - `entity/` — entities (item entities so far).
  - `network/` — packet handling (`NetherrackPacketHandler` is the hub), NetherNet setup,
    `VanillaData` (block states / creative data loaded from resources).
  - `player/` — player session, survival logic (health, hunger, eating, damage),
    inventory.
  - `world/` — `World`, `Chunk`/`SubChunk`, `WorldManager` (open/create worlds, spawn point),
    generators (`TerrainGenerator` + `PerlinNoise`, `FlatGenerator`/`FlatPreset`,
    `EmptyGenerator`), `LevelDat`, `storage/` (LevelDB persistence).
  - `setup/`, `util/` — first-run wizard, logging, misc.
- `changelogs/` — release notes; add user-facing changes to the current unreleased section.

## Conventions

- Javadoc is written in prose, explaining *why* things behave the way they do; wrap it around
  100 columns. Match the surrounding file's style before inventing new patterns.
- Logging: `Logger.info` for things an operator should see, `Logger.debug` for internal
  detail. Never leave temporary diagnostic logging behind — strip it before the work is done.
- Blocks are looked up by name through `Blocks.byName("minecraft:...")` (returns null for
  unknown names); don't hand-roll block instances.
- `Chunk` accessors are synchronized and take local x/z (0-15) with world y;
  `Chunk.MIN_Y` is -64. `getBlock` returns null for air.
- Packet handlers return `PacketSignal.HANDLED`; current Bedrock clients carry item uses and
  inventory requests inside `PlayerAuthInputPacket`, not only in transactions — keep that
  routing intact.

## Things to avoid

- Do not commit generated `target/` output or runtime config files (`server.properties`,
  `netherrack.yml`, `permissions.json`, worlds).
- Do not change the Java release level, CloudburstMC/Network or CloudburstMC/Protocol versions
  without a clear reason.
- Do not reintroduce RakNet: since Bedrock 1.26.60 the game speaks NetherNet only.
- Do not introduce new dependencies without updating `pom.xml` and noting why.
- Do not regress already-fixed behavior: eating (sound + stack decrement), immediate lava
  damage, and the auth-input item-use/inventory-request routing were hard-won bugs.
- Do not replace the flat world presets with the terrain generator; flat worlds stay flat.

## Notes for verification

- The user's live server may occupy port 19132; point smoke-test runs at another port via
  `server-port` in a scratch directory's `server.properties`.
- Temporary test harnesses previously lived under `/tmp` and do not survive reboots. If a
  regression harness is worth keeping, put it in a permanent folder in the repo instead.
