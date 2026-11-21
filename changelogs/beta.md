# v0.4.0-beta (unreleased)

### Added
- **Customizable console logging:** `netherrack.yml` now has `logging` and `labels` sections that
  control per-level formats, colors, prefixes, suffixes, timestamps and console visibility. The
  sections are read before the first startup line, and any missing section or key falls back to
  its built-in default.
- **Per-level log formats:** `info-format`, `warn-format`, `error-format` and `debug-format`, plus a
  shared `format` fallback. Formats accept `{time}`, `{date}`, `{millis}`, `{thread}`, `{level}`,
  `{message}`, `{prefix}` and `{suffix}`. Inline `<name>` placeholders insert colors, styles or
  those same values, so a line can be written as `[<yellow>{time}] <bold>{level}<reset> {message}`.
- **Friendly label colors:** `cyan`, `yellow`, `blue`, `red`, `bright-red`, `null` and the rest of
  the sixteen-color palette, plus `#rrggbb` hex values and raw `"\u001B[36m"` escape sequences.
  `labels.bracket-color` colors the brackets around placeholders without coloring the brackets
  in a player's message. Malformed settings and unknown placeholders are warned about and fall
  back safely.
- **`/reload` command:** reapplies `netherrack.yml` logging settings without restarting, including
  the console DEBUG switch and per-level enable flags.
- **Separate plugin API artifact:** the lifecycle contracts live in `NetherrackAPI`, built by
  JitPack from `v0.4.0-SNAPSHOT` and consumed with `provided` scope by plugins. The server declares
  the same artifact and shades it into `netherrack.jar`.
- **Server-side plugin loading:** jars in `plugins/` are read from `plugin.yml`, checked for name
  duplicates and `api-version` compatibility, and sorted by `depend`, `softdepend`, `loadbefore`
  and `STARTUP`/`POSTWORLD`. Each plugin gets its own classloader, data folder, `config.yml` and
  name-prefixed logger, then receives `onLoad()`, `onEnable()` and `onDisable()` at the matching
  points in startup and shutdown. A failing callback disables only that plugin and its dependents.
- **`/plugins` command:** lists loaded plugins alphabetically as `Plugins (1): HelloPlugin`, with
  each name in lime green for players and in ANSI lime green on a color-capable console.
- **`/kick` command:** operators with command level 2 or higher can disconnect an online player,
  with an optional reason shown on their disconnect screen and in the console's logout line.
- **Plugin events:** plugins can register `Listener` classes with `@EventHandler`, choose from six
  priorities, cancel cancellable events, and let base-class listeners receive subclasses. Handler
  errors are logged without stopping later handlers, and every handler is unregistered when its
  plugin is disabled.
- **First event set:** server start/stop, player login/join/quit/chat/command, and block
  place/break. Cancellable events are wired into the action they guard: a cancelled login kicks
  the player, a cancelled chat isn't broadcast, a cancelled command doesn't run, and cancelled
  block changes are sent back to the client.
- **Plugin-facing world types:** the API now has small `Player`, `World` and `BlockSnapshot`
  interfaces backed by adapters, so plugins never see protocol or storage classes.
- **Plugin commands:** a `commands:` block in `plugin.yml` declares names, aliases, descriptions,
  usage lines and permission nodes. Commands are registered during `onEnable()`, removed when the
  plugin stops, shown in client suggestions for players who pass the check, and handled in
  `PluginBase.onCommand()` or by an executor set on `getCommand(name)`. Returning false shows the
  declared usage line.
- **Plugin scheduler:** `Server.getScheduler()` runs one-shot, delayed and repeating tasks on the
  server tick. Tasks have ids and can be cancelled individually or by plugin; all of a plugin's
  tasks are cancelled on disable. Repeating tasks cannot be given a non-positive period.
- **Plugin permissions:** plugins can register nodes with allow, deny, operator, non-operator or
  undefined defaults and attach allow/deny rules to any player or the console. Exact nodes beat
  `parent.*` wildcards, explicit denials beat defaults, and unregistered nodes still follow a
  player's vanilla operator status. API permissions add to op levels rather than replacing them.
- **Player, world and block API:** plugins can read a player's feet position and facing, teleport
  them with rotation, read or change their game mode, and read or replace blocks as immutable
  snapshots. Snapshots carry exact vanilla state properties, `World` accepts `BlockPosition`
  coordinates alongside x/y/z, and `setBlock()` sends the change to clients that have the chunk.
- **Example Maven plugin:** `examples/hello-plugin/` builds a jar that demonstrates all three
  lifecycle callbacks, a generated `config.yml`, listeners for server, player and chat events,
  `/hello` and `/where` commands with a registered `hello.use` node, and a delayed scheduler task.
  It welcomes players on join, answers `!hello` in chat with a private, configurable greeting, and
  uses `/where` to show location, game mode and the block underfoot; `/where up` and `/where copy`
  demonstrate teleporting and writing a block snapshot back to the world.
- **Pick block:** middle-clicking a block now selects its item. The server finds a matching stack
  in the hotbar or main inventory, gives creative players one when needed, keeps the clicked
  block's state and simple block-entity data such as bed colors, and tells the client which slot
  to select.
- **API regression checks:** the separate `NetherrackAPI` repository has plain-Java checks for
  lifecycle, config, event ordering, cancellation, failure isolation, unregister-on-disable,
  commands, permissions, locations, game modes and block state properties.

### Changed
- Console logout lines now read `SchleroMC logged out due to: Disconnected`. The reason is the
  player's kick message when the server ended the session, or a plain translation of the transport
  reason when the client left; in-game leave messages are unchanged.
- Logging is configured before Netty, ANSI detection or world loading, so even the first line uses
  the operator's settings. Warnings raised while reading the file are held until `logs/latest.log`
  is open and then written to it.
- Redirected console output no longer receives forced ANSI escape codes. With `colors: auto`,
  escape codes are emitted only when output is going to a real terminal; `true` and `false` force
  the behavior either way.
- DEBUG lines stay out of the console by default and remain in the log file. Per-level
  `*-enabled` keys hide a level from the console without removing it from the log file.
- Generated `netherrack.yml` documents the new sections. The YAML reader and writer now handle
  `\uXXXX` escapes, which raw ANSI color values need.
- README, installation and third-party notices now describe `v0.4.0-beta`; the README logo,
  tagline and badges are unchanged. The README documents every logging setting and lists the new
  commands.
- A design document at `PLUGIN_API_PLAN.md` records the plugin API boundaries, event plan,
  implementation phases and the threading rule for each event.

### Known limitations
- Runtime plugin enable, disable and reload are still planned; a plugin jar change needs a restart.
- API permission changes are checked at command execution, but a player's already-sent client
  command suggestions are not refreshed until the server sends them again (for example on a change
  of vanilla command level).
