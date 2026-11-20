# Plugin API plan

This is a design document for a Nukkit-like plugin API split across two repositories:
`NetherrackAPI` holds the contracts plugins compile against, and this repository implements
those contracts and fires the events. Phases 1 (lifecycle), 2 (events), 3a (scheduler, commands
and permissions) and 3b (player, world and block) are implemented; the remaining phases are still
plans.

## Goals and constraints

- **Nukkit-like feel, not Nukkit code.** Borrow the *shape* developers already know —
  `plugin.yml`, a `PluginBase` class, `Listener` plus `@EventHandler`, event priorities and
  cancellation, a `PluginManager`, a scheduler, and permissions. Write every line fresh; don't
  copy Nukkit source files, and don't inherit its internal quirks (notably its reflection-heavy
  event registration) without a reason.
- **A real separate artifact.** `com.netherrack:netherrack-api`, Java 17, no runtime
  dependencies. Plugins depend on the API jar with `provided` scope; the server depends on it
  normally and shades it into `netherrack.jar` alongside the other libraries.
- **No server internals in the API.** The API must never expose CloudburstMC protocol classes,
  `Netherrack`, `Block`, or LevelDB. It defines its own small interfaces (`Server`, `Player`,
  `World`, `BlockSnapshot`, `ItemStack`) so protocol changes don't break plugins.
- **Small first slice.** Phase 1 is the lifecycle callbacks (`onLoad`, `onEnable`, `onDisable`)
  and a plugin that can be loaded and do nothing. Events come next. Shipping the smallest
  useful thing first keeps the daily usage cost down and gives us something to test early.

## Repository layout

`NetherrackAPI` (`~/Documents/netherrack-api`):

```
pom.xml
src/main/java/com/netherrack/api/
  plugin/    Plugin, PluginBase, PluginDescription, PluginLoadOrder, PluginLogger
  event/     Event, Cancellable, Listener, EventHandler, EventPriority,
             EventExecutor, HandlerList, EventException, EventManager,
             RegisteredHandler
  event/player/  PlayerEvent, PlayerLoginEvent, PlayerJoinEvent, PlayerQuitEvent,
                 PlayerChatEvent, PlayerCommandEvent
  event/server/  ServerEvent, ServerStartEvent, ServerStopEvent
  event/block/   BlockEvent, BlockPlaceEvent, BlockBreakEvent
  entity/    Player, GameMode
  world/     World, BlockSnapshot, BlockPosition, Location
  scheduler/ Scheduler, TaskHandler
  command/   Command, CommandExecutor, CommandSender, PluginCommand
  permission/ Permissible, Permission, PermissionAttachment, PermissionDefault,
              PermissionManager, PermissionState
  plugin/    (also CommandDescription for plugin.yml commands)
  Server
src/test/java/...
```

This repository gains a thin implementation layer:

```
src/main/java/com/netherrack/server/plugin/
  PluginManagerImpl      loads/enables/disables, owns registries
  JavaPluginLoader       finds jars, reads plugin.yml, makes classloaders
  PluginClassLoader      per-plugin loader, parent-first for API, isolated otherwise
  ServerAdapter          implements com.netherrack.api.Server over Netherrack
  PlayerAdapter          implements com.netherrack.api.entity.Player over server Player
  WorldAdapter           implements com.netherrack.api.world.World over server World
  EventBridge            helpers to call events from packet handlers and startup code
  PluginCommandAdapter   bridges API Command to server CommandManager
  PluginScheduler        adds ids, repeating tasks and cancellation to the tick scheduler
  PluginTask             one tracked plugin task
  PermissionManagerImpl  registry of declared permission nodes
  PermissionSubject      resolves attachments, defaults, wildcards and operator status
  PlayerAdapter          also acts as the player's API command sender and permissible
  ConsoleSenderAdapter   the console's API command sender and permissible
```

The existing `Scheduler`, `CommandManager`, `PlayerManager`, `World`, `Logger` and
`NetherrackConfig` stay as they are; the plugin layer adapts to them rather than rewriting
them. API-facing types never leak into their signatures.

## Phase 1 — lifecycle only (implemented)

These are **callbacks, not `Event` objects**. `onLoad()`, `onEnable()` and `onDisable()` are
declared on `Plugin` and default to no-ops in `PluginBase`, matching Nukkit's familiar lifecycle.

`plugin.yml` (a small subset at first):

```yaml
name: ExamplePlugin
version: 1.0.0
api-version: 0.4.0
main: com.example.ExamplePlugin
description: An example plugin
author: Someone
load: POSTWORLD          # STARTUP or POSTWORLD
depend: []               # required plugin names
softdepend: []           # optional plugin names
```

Load sequence in `PluginManagerImpl`:

1. Create `plugins/` if needed, scan for `*.jar`, read and validate each `plugin.yml`.
2. Reject duplicates (same name), incompatible `api-version`, missing `main` class, and
   unsatisfied `depend` entries.
3. Sort by `depend` / `softdepend` / `load` order.
4. Construct the main class with a per-plugin classloader and call `onLoad()`. At this point
   the world and network may not exist — plugins must not touch them here.
5. After the world is loaded and the server is listening, call `onEnable()` and set the
   enabled flag.
6. On shutdown, call `onDisable()` in reverse order, cancel that plugin's tasks, and
   unregister its listeners, commands and permissions.

`PluginBase` provides the conveniences plugins expect: `getServer()`, `getDescription()`,
`getDataFolder()` (`plugins/<Name>/`), `getConfig()` / `saveConfig()` from a small API-owned
config class, `getLogger()` (prefixed with the plugin name and routed to our `Logger`),
`isEnabled()`, and `getPluginLoader()`. The API's config class should be a minimal YAML-ish
reader/writer implemented in the API — don't add SnakeYAML yet, and don't expose
`NetherrackConfig`.

## Phase 2 — events (implemented)

- `Event` is abstract with `getEventName()`; `Cancellable` is a separate interface with
  `setCancelled(boolean)` / `isCancelled()`.
- `Listener` is a marker interface. `@EventHandler(priority = EventPriority.NORMAL,
  ignoreCancelled = true)` marks methods with one `Event` parameter.
- `EventPriority` in ascending order: `LOWEST`, `LOW`, `NORMAL`, `HIGH`, `HIGHEST`, `MONITOR`.
  `MONITOR` handlers must not modify or cancel the event.
- `HandlerList` is held per event class. A central `Map<Class<? extends Event>, HandlerList>` in
  `EventManager` avoids Nukkit's reflection-over-static-fields approach; dispatch walks the
  event's superclasses so a listener for `PlayerEvent` also sees `PlayerJoinEvent`, and sorts all
  matching handlers together by priority. There is no static `getHandlers()` on event classes.
- Registration caches the reflected `Method` (or a `MethodHandle`) at register time; dispatch
  wraps invocation errors in `EventException`, logs them, and keeps calling later handlers.
- Unregister by plugin on disable — stale listeners are the most likely way to get a crash
  after a reload.

First events, in this order:

| Event | Cancellable | Hook point | Thread |
| --- | --- | --- | --- |
| `ServerStartEvent` / `ServerStopEvent` | no | end of `Netherrack.start()` / start of shutdown | startup / shutdown |
| `PlayerLoginEvent` | yes | `NetherrackPacketHandler.completeLogin()`, before play status | player's network thread |
| `PlayerJoinEvent` | no | after `SetLocalPlayerAsInitialized` adds them to the player list | player's network thread |
| `PlayerQuitEvent` | no | `onDisconnect()`, after removal and save | player's network thread |
| `PlayerChatEvent` | yes | `handle(TextPacket)`, before broadcast | player's network thread |
| `PlayerCommandEvent` | yes | `runCommand(...)`, before `CommandManager.dispatch(...)` | player's network thread |
| `BlockPlaceEvent` / `BlockBreakEvent` | yes | `BlockInteraction` place/break paths | player's network thread |

**Threading rule:** events are delivered synchronously on the thread performing the action, so
cancellable events can decide what that thread does next. Server start and stop run on the main
startup and dedicated shutdown threads; every player and block event runs on that player's
network thread. Netherrack already mutates world state there during block interactions, so hop-
ping only the listener to the tick thread would not make world access safer — the longer-term fix
is to move the block action itself, listeners included, onto one owner thread. Listeners must
return quickly; a plugin scheduler for slower work comes in Phase 3.

## Phase 3a — scheduler, commands, permissions (implemented)

- The server's tick scheduler still only knows one-shot `schedule(int ticks, Runnable)`; the
  plugin layer wraps it with ids, repeating tasks and cancellation. The API exposes `Scheduler`
  and `TaskHandler`, so plugins call `runTask`, `runTaskLater` or `runTaskTimer` and can cancel by
  handle or id. Every task records its owning plugin, and disable cancels all of its tasks before
  closing the jar. Tasks always run on the single server tick thread.
- API `Command` and `CommandSender` are independent of the server's own versions. A
  `PluginCommandAdapter` adapts declared commands to `CommandManager`, while player adapters and
  one shared console adapter act as command senders. The alias used is passed to the executor as
  `label`; returning false prints the usage line from `plugin.yml`. Name conflicts are refused
  rather than replacing a built-in command.
- API permissions are an additional layer on top of vanilla op levels. `PermissionSubject`
  resolves in this order: exact attachment rule, longest matching `parent.*` attachment rule,
  registered default, then operator status. Exact rules beat wildcards regardless of attachment
  age, and later attachments win ties. Registered defaults may be ALLOW, DENY, OPERATOR,
  NOT_OPERATOR or UNDEFINED. `PermissionManager` is only a node registry; a permission manager
  plugin changes players through `PermissionAttachment`.

## Phase 3b — player, world and block API (implemented)

- `Location` is an immutable world point with yaw and pitch in degrees. Its y is the player's feet,
  so it matches `World.getBlock`, vanilla `/tp` and `BlockPosition` rather than the eye-height
  vector carried by Bedrock movement packets. `toBlockPosition()` floors the coordinates.
- `Player` now exposes `getLocation()`, `teleport(Location)`, `getGameMode()` and
  `setGameMode(GameMode)`. The adapter converts between feet and eye height and calls
  `PlayerManager.teleport` with rotation, so the client, its loaded chunks and every onlooker all
  move together. `GameMode` uses Bedrock's ids: survival 0, creative 1, adventure 2, spectator 6.
- `World.getBlock`/`setBlock` accept a `BlockPosition` as well as x/y/z. The server resolves a
  snapshot through its exact properties first, then its state hash and finally the block's default
  state, so a snapshot read from a world can be written back without losing its variant.
- `BlockSnapshot` is immutable and now carries the block's vanilla properties as well as its name
  and state hash. NBT bit properties become booleans; integers and strings keep their values.
  `World.setBlock` changes saved world data but does not send a block-update packet to clients that
  already have the chunk yet.
- Calls from listeners happen on the player's network thread, where the action itself runs. Use the
  Phase 3a scheduler to move slower work onto the tick thread; don't hold a live server object in a
  plugin. Avoid designing the entire game in the API before there are plugins using it.

## Phase 5 — polish

Dependency-driven load order fully working, optional `/plugman`-style enable/disable/reload if
it proves safe, an example plugin repository, javadoc, and a short "writing your first plugin"
page linked from the README.

## Build and release workflow

- API repo is published through JitPack as
  `com.github.NetherrackOSS:NetherrackAPI:v0.4.0-SNAPSHOT`; the server declares that artifact and
  the shade plugin bundles it into `netherrack.jar`.
- Version the API independently with `major.minor.patch` and validate `api-version` in
  `plugin.yml` by major/minor. During beta, breaking changes bump the minor version and are
  called out in the changelog.
- Plugin builds declare the API as `provided` so their jars stay small and use the server's
  copy.
- No new third-party dependencies in either repo without a note in `THIRD_PARTY_NOTICES.md`.

## Testing

- API repo: plain-Java checks (no new test dependency) for `plugin.yml` parsing, handler
  ordering by priority and across base classes, cancellation with `ignoreCancelled`, handler
  failure isolation, unregister-on-disable, command declarations, permission attachments and
  the tri-state permission model, locations, game modes and block state properties. These checks
  are passing for Phases 1-3b.
- This repo: extend `tests/run.sh` with a smoke test that loads a stub plugin jar and asserts
  the order `onLoad → onEnable → (events fire) → onDisable`, plus a cancelled event not reaching
  the server action.
- Verify a real server start with one example plugin before calling any phase done.

## Decisions

1. Package root: `com.netherrack.api.*`.
2. API version: independent `0.4.0`, published as the JitPack tag `v0.4.0-SNAPSHOT`.
3. Plugin config format: API-owned mini-YAML.
4. Event dispatch is synchronous on the thread doing the action; each event's thread is documented
   in the table above.
5. `getServer()` is injected through `PluginBase`.
6. Runtime enable/disable/reload is deferred to Phase 5.
7. The API jar is published through JitPack.

## Effort outline

Each phase is roughly one focused session, with events and player/world taking two or three:

1. This plan.
2. API skeleton + `plugin.yml` + lifecycle + loader.
3. Event system + the first six events.
4. Scheduler, commands, permissions.
5. Player/World/Block interfaces + block events.
6. Polish, example plugin, docs.

Lifecycle callbacks are the first priority, so Phase 1 is the next thing to implement when the
time comes.
