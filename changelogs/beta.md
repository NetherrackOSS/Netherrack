# v0.4.0-beta

### Added
- **Custom form UI API:** plugins can open a Bedrock `SimpleForm` with a title, message and buttons
  through `Player.showForm(Form)`. The server sends the form JSON, matches responses by id, runs
  submit or close callbacks, rejects a second form while one is open, and fires
  `PlayerFormRespondedEvent` for observers. `examples/hello-plugin/` now has `/testform`, which
  opens a form titled `Test` with the message `Test` and one button also labeled `Test`.
- **Burning dropped items:** item entities now take Cloudburst Nukkit-style damage from lava and
  fire, show the `ON_FIRE` flag while alight, and keep taking afterburn damage for up to eight
  seconds after leaving the hazard. Netherite items are fireproof and burn off faster.
  `tests/run-item-hazard.sh` checks the damage numbers, flowing lava and soul fire, afterburn,
  the netherite exception, and cleanup of items left in unloaded chunks.
- **Shift-click crafting:** shift-clicking a recipe in the 2×2 or 3×3 crafting book now asks
  the server for a whole batch instead of one result. The server checks the requested number
  of crafts, verifies that the inventory consumes cover its own copy of the recipe (including
  item tags), rejects batches that would overflow a stack, and leaves the crafting grid clear.
- **Expanded setup wizard:** after the language and license screens, a first run can now skip
  the remaining questions or set the server MOTD, port, default gamemode and maximum player
  count before a final summary shows the local and externally detected addresses. The public
  IP lookup is best-effort and only runs in an interactive terminal; piped launches keep the
  old behavior so console commands aren't swallowed.
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
- **`/reload` command:** reapplies `netherrack.yml` logging settings and hot-reloads plugins
  without restarting. Logging reload includes the console DEBUG switch and per-level enable flags;
  plugin reload disables loaded plugins, clears their listeners/commands/tasks/permissions, and
  discovers jars in `plugins/` again.
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
- **Whitelist:** `white-list` in `server.properties` now restricts joining to the players in
  `whitelist.json`, with operators always allowed. Entries can store a gamertag, XUID, UUID or any
  combination, so offline names and renamed accounts keep working. `/whitelist on|off|list|reload`
  and `/whitelist add|remove <player>` manage it at command level 3; the disconnect reason comes
  from `messages.server-whitelist` in `netherrack.yml`. `tests/run-whitelist.sh` checks the JSON
  store, identifier matching, hand edits and the saved toggle.
- **Bans:** a Cloudburst Nukkit-style `banned-players.json` now stores each ban's lowercase name,
  creation date, source, expiry and reason, with XUID and UUID saved too when the player is
  online so a gamertag change doesn't lift the ban. Banned players are turned away at login with
  the reason from `messages.server-ban` in `netherrack.yml`. `/ban <player> [reason...]` kicks an
  online target as well; `/pardon <player>` (alias `/unban`) removes bans and `/banlist` lists them
  or reloads the file. All three are level 2, like Nukkit's operator-only ban commands; Nukkit's
  `/banlist ips` is accepted but Netherrack doesn't keep IP bans. `tests/run-ban.sh` checks the
  saved fields, identifier matching, expiry and hand edits.
- **`/clear` command:** operators with command level 2 or higher can empty a target player's
  inventory, including armor, offhand and anything on the cursor or in a crafting grid. Players
  can clear their own inventory with `/clear` or `/clear @s`.
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
  commands, permissions, locations, game modes, block state properties and form JSON/callbacks.
  The server also has `tests/run-form.sh` for form ids, packet JSON, stale responses, close
  callbacks and disconnect cleanup.

### Changed
- Startup logs the running Netherrack version, git commit and GPL notice once NetherNet signaling
  is listening, so the console shows which build accepted connections.
- `/version` now includes the plugin API version below the operating system line. The value comes
  from the same Maven tag used to build the server's API dependency.
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
- `/reload` now also hot-reloads plugins after reapplying logging settings, so a replaced jar in
  `plugins/` can be picked up without a full restart.

### Known limitations
- Plugin hot-reload is best-effort and follows Nukkit's broad reload shape; for classloader or
  state-heavy plugins, a full server restart is still the safest recovery path.
- API permission changes are checked at command execution, but a player's already-sent client
  command suggestions are not refreshed until the server sends them again (for example on a change
  of vanilla command level).
