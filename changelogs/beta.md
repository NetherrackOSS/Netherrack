# v0.6.0-beta

### Added
- **More player events:** plugins can now listen to `PlayerDamageEvent`, `PlayerDeathEvent`,
  `PlayerRespawnEvent`, `PlayerMoveEvent`, `PlayerTeleportEvent`, `PlayerGameModeChangeEvent`,
  `PlayerInventoryClickEvent`, `PlayerItemPickupEvent`, `PlayerItemDropEvent`, `PlayerItemUseEvent`
  and `PlayerItemConsumeEvent`. Damage, movement, teleports, drops and item uses are cancellable
  before the server applies them; damage amounts, death messages and drops, respawn and movement
  destinations, and item stacks can be changed by listeners. The server fires them from the real
  gameplay paths: survival damage and death, the respawn handshake, client movement and teleports,
  game-mode commands, item pickups, hotbar and inventory drops, inventory stack requests, and both
  item-use packet routes.
- **Metadata API:** `Player` now has case-insensitive string metadata through `getMeta`,
  `setMeta`, `removeMeta`, `getPrefix` and `setPrefix`. Changing a connected player's value fires a
  cancellable `PlayerMetadataChangeEvent`, so a plugin can veto or rewrite what another plugin
  stores. Metadata is per session and is not written to disk.
- **Offline player lookup:** `Server.getOfflinePlayer(nameOrXuid)`, `getOfflinePlayer(UUID)` and
  `getOfflinePlayers()` return `OfflinePlayer` identities with the last known name, UUID, XUID and
  online state. New names and ids are kept in Nukkit-style `players/<uuid>.json` files, and lookup
  also considers online players, permissions, the whitelist, bans and saved world UUIDs. This is an
  identity API, not a saved inventory or position API.
- **Item stacks in events:** the API's `ItemStack` gives plugins a protocol-free view of a stack's
  id, count and damage; `InventoryClickAction`, `DamageCause` and `TeleportCause` name the matching
  gameplay causes without leaking protocol enums.

### Changed
- **Offline identity storage:** the old `player-identity.json` index is imported once into
  `players/<uuid>.json` and then removed. Each file carries the player's name, UUID, XUID and
  `firstPlayed`/`lastPlayed` timestamps, following Nukkit's `players/` folder and UUID-based player
  data; an in-memory index covers name and XUID lookups without duplicating inventories or
  positions.
- **Plugin API version:** the API artifact and JitPack tag are now `v0.6.0-SNAPSHOT`, the server
  version is `0.6.0-beta`, and plugins declare `api-version: 0.6.0`; plugins declaring `0.5.x`
  still load because compatibility is checked by major and minor version.

# v0.5.0-beta

### Added
- **Button icons in forms:** plugin `SimpleForm` buttons can now carry an icon through
  `addButton(text, FormButtonImage.path(...))` or `FormButtonImage.url(...)`, serialized into the
  button's `image` field like Cloudburst Nukkit's `ElementButtonImageData`. The example plugin's
  `/testform` shows both icon types next to a plain button.
- **Nukkit-style login log:** as soon as a player's StartGame sequence is sent, the console
  records their address, entity id, world and block coordinates in a line like
  `Player1[/192.168.1.36:59274] logged in with entity id 3 at (world, 139, 65, 11)`. Like
  Nukkit, this no longer waits for the client to finish loading.
- **HUD message events:** plugins can now send action bar, toast and popup messages through
  Cloudburst Nukkit-style `Player` methods. Each send fires a cancellable
  `PlayerActionBarEvent`, `PlayerToastEvent` or `PlayerPopupEvent` with mutable message, title and
  timing values before the client receives the packet. `examples/hello-plugin/` has a
  `/testmessages` command that exercises all three.
- **Client-language messages:** command replies, join and leave announcements, and death messages
  are rendered with the player's UI language when Netherrack has a matching local key. Keys that
  aren't translated locally keep using vanilla translation packets, so the client still handles
  them as before.
- **Operator command broadcasts:** successful administrative command replies are now shown to the
  console and other online operators as `[Sender: result]`, like Cloudburst Nukkit's admin command
  broadcasts. Every recipient gets the result in their own language: the console uses the server
  language, and other operators see it in gray italics. Other replies to player commands - errors,
  usage, help, version and plugin commands - stay with the player and are not copied to the console.

### Changed
- **Vanilla command wording:** the vanilla keys in `lang/en-US.properties` and
  `lang/pt-BR.properties` now use the strings from Minecraft's default `en_US.lang` and
  `pt_BR.lang`, including game mode and difficulty names, so server-rendered output matches what
  clients say.
- **Plugin API version:** the API artifact and JitPack tag are now `v0.5.1-SNAPSHOT`, and plugins
  declare `api-version: 0.5.1`; plugins declaring `0.5.0` still load because compatibility is
  checked by major and minor version. The new form button icons are part of this patch; `/version`
  still reports the server version `0.5.0-SNAPSHOT`.

### Fixed
- **Early join visibility:** players are registered as online as soon as their StartGame sequence
  is sent, instead of waiting for their client to finish loading. Other clients now receive the
  joining player's player-list entry while the new client is still loading, as Nukkit's
  `addOnlinePlayer()` does, and the player is spawned into any views that already have their
  chunks. The join message still waits for the client to finish loading.
- **Server list player count:** the NetherNet server-list response now reports how many players are
  online when the list is refreshed, instead of always showing 0. Like Nukkit's query information,
  the MOTD is rebuilt per request from the live player manager.

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
- **Time and weather commands:** `/time query|set|add` and `/weather clear|rain|thunder` change the
  world's time and weather, broadcast `SetTimePacket`/level weather events to players, and persist
  the values in `level.dat`. Nukkit-style time names like `day`, `night`, `noon`, `midnight`,
  `sunrise` and `sunset` are accepted.
- **Difficulty command and wiring:** `/difficulty` shows or sets peaceful/easy/normal/hard. The
  configured difficulty is stored in new worlds, loaded from `level.dat`, synced through
  `StartGamePacket`/`SetDifficultyPacket`, and saved back to `server.properties` and `level.dat`.
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
  from `messages.server-whitelist` in `netherrack.yml`.
- **Bans:** a Cloudburst Nukkit-style `banned-players.json` now stores each ban's lowercase name,
  creation date, source, expiry and reason, with XUID and UUID saved too when the player is
  online so a gamertag change doesn't lift the ban. Banned players are turned away at login with
  the reason from `messages.server-ban` in `netherrack.yml`. `/ban <player> [reason...]` kicks an
  online target as well; `/pardon <player>` (alias `/unban`) removes bans and `/banlist` lists them
  or reloads the file. All three are level 2, like Nukkit's operator-only ban commands; Nukkit's
  `/banlist ips` is accepted but Netherrack doesn't keep IP bans.
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
  commands, including `/time`, `/weather` and `/difficulty`.
- A design document at `PLUGIN_API_PLAN.md` records the plugin API boundaries, event plan,
  implementation phases and the threading rule for each event.
- `/reload` now also hot-reloads plugins after reapplying logging settings, so a replaced jar in
  `plugins/` can be picked up without a full restart.
- The shutdown thread is named `main`, which fits the localized shutdown log format more naturally.

### Known limitations
- Plugin hot-reload is best-effort and follows Nukkit's broad reload shape; for classloader or
  state-heavy plugins, a full server restart is still the safest recovery path.
- API permission changes are checked at command execution, but a player's already-sent client
  command suggestions are not refreshed until the server sends them again (for example on a change
  of vanilla command level).
