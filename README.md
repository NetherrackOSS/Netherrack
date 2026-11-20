<p align="center">
  <a href="https://netherrackoss.github.io">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset=".github/logo-dark.png">
      <source media="(prefers-color-scheme: light)" srcset=".github/logo-light.png">
      <img alt="Netherrack Logo" src=".github/logo-light.png" width="75%">
    </picture>
</p>

<p align="center"><b>Open source server software for Minecraft: Bedrock Edition written in Java</b></p>

<p align="center">
  <img src="https://img.shields.io/github/license/NetherrackOSS/Netherrack?style=flat-square" alt="License">
  <img src="https://img.shields.io/github/stars/NetherrackOSS/Netherrack?style=flat-square" alt="GitHub Stars">
  <img src="https://img.shields.io/badge/Minecraft-Bedrock-62B47A?style=flat-square" alt="Minecraft Bedrock">
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17+">
  <img src="https://img.shields.io/badge/Build-Maven-C71A36?style=flat-square&logo=apachemaven" alt="Maven">
  <a href="https://discord.gg/JFUz2G8H4V">
    <img src="https://img.shields.io/badge/Discord-Server-5865F2?style=flat-square&logo=discord&logoColor=white" alt="Discord">
  </a>
</p>

> [!WARNING]
> Netherrack **v0.4.0-beta** is experimental software. Expect missing features, bugs, and
> glitches. Do not use it for a production server or a world you care about without keeping
> independent backups.

> [!NOTE]
> Netherrack is not affiliated with, endorsed by, or sponsored by Mojang Studios or Microsoft.

# ℹ️ Information

Netherrack is an experimental, from-scratch Minecraft: Bedrock Edition server written in Java.
It is a learning and experimentation project: the server architecture is developed
independently rather than as a clone of another implementation, and APIs and behavior may
change between releases.

This beta targets **Minecraft: Bedrock Edition 1.26.60** (`Bedrock_v2225`). Bedrock 1.26.60
replaced RakNet with **NetherNet**, so Netherrack uses that transport exclusively — old
RakNet-based clients and servers are not supported. Multi-version support is not implemented;
clients on other versions may be rejected or behave unexpectedly.

# 🛠️ Getting Started

## Requirements

- **Java 17 or newer** (`java --version` to check)
- **Minecraft: Bedrock Edition 1.26.60** to connect
- **Git**, only if building from source
- A free **port 19132** (configurable) for the server's HTTP signaling and game traffic

The NetherNet native WebRTC library is bundled in the JAR. On Linux/macOS you may need the
JVM flags used by the included start scripts to allow native memory access; see below.

## 🔨 Install from a release

1. Download the latest JAR from the [Releases](https://github.com/NetherrackOSS/Netherrack/releases) page.
2. Move it into a dedicated directory, for example `netherrack-server`, and rename it to
   `netherrack.jar` if needed.
3. Launch it:

```bash
java -jar netherrack.jar
```

## 🔧 Build from source

```bash
git clone https://github.com/NetherrackOSS/Netherrack.git
cd Netherrack
```

Then build with the Maven wrapper — no separate Maven installation required:

```bash
bash mvnw clean package    # Linux/macOS
mvnw.cmd clean package     # Windows
```

The shaded, runnable JAR is written to `target/netherrack.jar`.

## 🧭 First run

The first launch (when `server.properties` is missing) runs an interactive setup wizard:

1. Choose a console language: `en-US` (English) or `pt-BR` (Português). Press Enter for `en-US`.
2. Read the GPLv3 notice and accept the license with `y` (or `yes` / `s` / `sim`). The server
   will not start without accepting it.

You should see something like this:

```
[16:01:07] [main] [INFO] Starting Netherrack server version 0.4.0-beta
[16:01:07] [main] [INFO] First-time setup detected. Launching setup wizard...

═══════════════════════════════════════════════════════════════
         Netherrack Setup Wizard - Language Selection
═══════════════════════════════════════════════════════════════

Welcome! Please select a language first.

[*] Enter a language code from the list below (press Enter for default).
  [en-US] English (United States)
  [pt-BR] Português (Brasil)

» Language [en-US]:
```

The first run creates:

| File | Purpose |
| --- | --- |
| `server.properties` | Server and world settings |
| `netherrack.yml` | Netherrack-specific settings (logs, messages, flat preset) |
| `permissions.json` | Operator/permission data |
| `nethernet-identity.pem` | Persistent NetherNet server identity (EC key pair) |
| `logs/latest.log` | Console copy plus optional debug detail |
| `worlds/Bedrock level/` | The default world (`level.dat`, `levelname.txt`, `db/`) |

Open Bedrock Edition on the same machine (or network) and connect to the server IP on port
`19132`. Players joining and leaving are announced in chat and in the console.

> Encountering issues? Check that Java 17+ is installed, that port 19132 is free, and that
> your client is version 1.26.60. See [INSTALLATION.md](INSTALLATION.md) for additional notes.

# ⚙️ Configuration

`server.properties` is generated with these defaults:

| Key | Default | Meaning |
| --- | --- | --- |
| `server-name` | `Netherrack Server` | Name shown in the server list |
| `gamemode` | `survival` | Default game mode for new players |
| `difficulty` | `normal` | `peaceful`, `easy`, `normal`, or `hard` (not enforced yet; gameplay currently runs on easy) |
| `max-players` | `20` | Maximum concurrent players |
| `online-mode` | `true` | Generated but currently ignored — NetherNet always requires Xbox authentication |
| `server-port` | `19132` | Listening port |
| `server-ip` | `0.0.0.0` | Bind address; `0.0.0.0` listens on all interfaces |
| `view-distance` | `32` | View distance in chunks (5 or more) |
| `level-name` | `Bedrock level` | World name and folder under `worlds/` |
| `default-player-permission-level` | `member` | `visitor`, `member`, or `operator` |
| `language` | wizard choice | Console language (`en-US` or `pt-BR`) |
| `level-type` | `default` | `default` for generated terrain, `flat` for flat presets |
| `op-permission-level` | `2` | Command access granted to operators (0–4) |

`level-type` only decides the generator of a **new** world; existing worlds keep theirs.

`netherrack.yml` holds Netherrack's own settings:

| Section | Key | Default | Meaning |
| --- | --- | --- | --- |
| `logs` | `debug` | `false` | Write every packet and debug detail to `logs/latest.log` |
| `logging` | `info-format`, `warn-format`, `error-format`, `debug-format` | `[{time}] [{thread}] [{level}] {message}` | Console pattern for each level |
| `logging` | `format` | same as above | Fallback for levels without their own pattern |
| `logging` | `prefix`, `suffix` | empty | Text inserted by `{prefix}` and `{suffix}` |
| `logging` | `<level>-prefix`, `<level>-suffix` | shared values | Per-level overrides for those placeholders |
| `logging` | `time-format` | `HH:mm:ss` | Java time pattern used for `{time}` |
| `logging` | `date-format` | `yyyy-MM-dd` | Java date pattern used for `{date}` |
| `logging` | `colors` | `auto` | `auto` detects a terminal; `true` and `false` force colors on or off |
| `logging` | `console-debug` | `false` | Show DEBUG lines on the console using `debug-format` |
| `logging` | `<level>-enabled` | `true` | Hide a level on the console while keeping it in the log file |
| `labels` | `timestamp-color`, `thread-color`, `info-color`, `warn-color`, `error-color`, `debug-color` | `cyan`, `yellow`, `blue`, `red`, `bright-red`, `null`, `null` | Named color for each label; `null` leaves it plain |
| `labels` | `bracket-color` | `null` | Named color for brackets around placeholders; `null` leaves them plain |
| `messages` | `server-shutdown` | `Server closed` | Message shown to players when the server closes |
| `messages` | `server-whitelist` | `You are not whitelisted on this server!` | Configured message for turned-away players; whitelist enforcement is not implemented yet |
| `level` | `preset-type` | `Classic Flat` | Flat preset used for new `flat` worlds |

The `logging` and `labels` sections are read before the server prints its first line, so the
startup messages use them too. If either section or a key is missing, that setting falls back
to the default shown above. The `reload` command re-reads them without a restart.

Formats have two kinds of placeholder. `{time}`, `{date}`, `{millis}`, `{thread}`, `{level}`,
`{message}`, `{prefix}` and `{suffix}` insert values. `<name>` inserts a color, a style
(`<bold>`, `<italic>`, `<underline>`, `<reset>`), or any of those same values — for example
`[<yellow>{time}] <bold>{level}<reset> {message}`. `<null>` inserts nothing. These work
alongside `labels.*`, and a trailing reset is added when needed so an inline color can't carry
into the console prompt.

Label colors are names: `black`, `red`, `green`, `yellow`, `blue`, `magenta`, `cyan`, `white`,
`gray`, and `bright-` variants of each, plus `purple` and `grey` as aliases. Hex values such as
`#ff8800` work too. Raw ANSI escape sequences written as `"\u001B[36m"` still work. With
`colors: auto`, escape codes are dropped when output is piped to a file.

DEBUG lines go only to the log file unless `console-debug: true` is set, which makes them use
`debug-format` on the console too. Per-level `*-enabled` keys hide a level from the console
without removing it from the log file.

Available flat presets: `Classic Flat`, `Tunneler's Dream`, `Water World`, `Overworld`,
`Snowy Kingdom`, `Bottomless Pit`, `Desert`, `Redstone Ready`, and `The Void`.

# ✨ Features

- **NetherNet networking** — WebRTC (ICE/DTLS/SCTP) transport via CloudburstMC's Network
  library, with Xbox Live token authentication and a persistent server identity.
- **Worlds and terrain** — seed-based "infinite" worlds with rolling hills, valleys, lakes,
  sandy shores, stone/dirt/grass layers, a jagged bedrock floor, and scattered oak trees.
  New levels spawn on dry ground. Nine vanilla flat presets remain available.
- **Chunk streaming and persistence** — chunks load as players move, worlds save to LevelDB,
  and the server autosaves every 30 seconds.
- **Players and gameplay** — login, movement, chat, join/leave messages, four game modes,
  health and hunger, eating, block breaking and placement, drops, and item entities that can
  be picked up and despawn over time.
- **Combat** — player-versus-player hits with vanilla-like reach, weapon damage and knockback,
  invulnerability ticks, creative/spectator protections, and dropped inventories on death.
- **Inventory and crafting** — the 2×2 inventory grid and a right-clickable 3×3 crafting
  table. All 4,770 recipes for this protocol version are sent to the client; the server
  validates grid shapes, consumes ingredients, and hands out results. Ingredient tags and
  data-carrying outputs such as banners and firework rockets work.
- **Commands** — built-in console and chat commands with permission levels:

  | Command | Alias | Min. level | Description |
  | --- | --- | --- | --- |
  | `help` | `?` | 0 | List commands or describe one |
  | `version` | `ver` | 0 | Show server version and system information |
  | `gamemode` | — | 2 | Change a player's game mode |
  | `give` | — | 2 | Give a player an item |
  | `kill` | — | 2 | Kill a player |
  | `kick` | — | 2 | Disconnect a player, optionally with a reason |
  | `tp` | — | 2 | Teleport a player (players or coordinates, with `~` relative support) |
  | `op` / `deop` | — | 3 | Grant or remove operator status |
  | `reload` | `rl` | 4 | Reload logging settings from `netherrack.yml` |
  | `plugins` | `pl` | 0 | List installed plugins |
  | `stop` | — | 4 | Stop the server gracefully |

- **Localization and logging** — English (US) and Brazilian Portuguese console messages,
  customizable per-level console formats and colors, plain log files, and optional verbose
  debug logging that can be reloaded at runtime.
- **Plugin API** — jars in `plugins/` are read in dependency order and get `onLoad()`, `onEnable()`
  and `onDisable()` callbacks. Plugins can register listeners with priorities and cancellation for
  server, player and block events, declare commands in `plugin.yml`, schedule delayed and repeating
  tick tasks, and register permission nodes with wildcard matching and per-player attachments. They
  can also read player locations and game modes, teleport players, and read or replace blocks as
  immutable snapshots. `examples/hello-plugin/` is a minimal Maven plugin showing all of them.

# 🚧 Current status

v0.4.0-beta is an early beta, not a feature-complete server. Known gaps and untested areas
include:

- Long-term stability, many simultaneous players, and shutdown or crash edge cases.
- Compatibility with Bedrock client versions other than 1.26.60.
- Terrain variety: no caves, ores, flowers, or multiple biomes yet — every generated chunk is
  plains.
- Runtime plugin enable, disable and reload are still to come; restarting is needed after adding,
  removing or updating a plugin jar.
- Plugin `World.setBlock()` changes saved world data but does not yet send a block-update packet to
  clients that already have the chunk; the new state appears when they reload the chunk.
- Furnace, stonecutter, and smithing screens and their interactions are not implemented;
  shift-click crafting of a stack is not supported.
- Items dropped into lava or fire are not destroyed, and death drops in unloaded chunks or
  the void are untested.
- Client movement is not validated server-side, so positions are currently accepted as reported.
- Some bundled vanilla data is from versions just before 1.26.60; a few newer blocks or
  entities may be missing or appear as the unknown-block texture.

Bugs and limitations beyond this list may still be discovered. Please report issues with
logs, reproduction steps, your Java/OS details, and your Bedrock client version.

# 🧱 Project goals

The long-term goal is a flexible, open-source Bedrock server implementation built while
learning about Java, networking, the Bedrock protocol, world systems, and server
architecture. Netherrack is not intended to be a clone of another server implementation.

# 📜 Licensing and credits

Netherrack uses open-source components from [CloudburstMC](https://github.com/CloudburstMC)
(Network and Protocol), the [libdatachannel](https://github.com/opencollab-incubator/libdatachannel-java)
WebRTC binding, [leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java), and
bundled vanilla data from CloudburstMC/Data, PMMP/BedrockData, Dragonfly, and
PrismarineJS/minecraft-data. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for versions
and license details.

Netherrack itself is licensed under the [GNU General Public License v3.0](LICENSE).

## 🌐 Community

- Website: <https://netherrackoss.github.io>
- Releases: <https://github.com/NetherrackOSS/Netherrack/releases>
- Issues: <https://github.com/NetherrackOSS/Netherrack/issues>
- Discord: <https://discord.gg/JFUz2G8H4V>
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
