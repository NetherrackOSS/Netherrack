# Plugin API plan

This is a design document for a Nukkit-like plugin API split across two repositories:
`NetherrackAPI` holds the contracts plugins compile against, and this repository implements
those contracts and fires the events. Phase 1 (lifecycle) is implemented; the remaining phases
are still plans.

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
             EventExecutor, HandlerList, EventException
  event/player/  PlayerEvent, PlayerLoginEvent, PlayerJoinEvent, PlayerQuitEvent,
                 PlayerChatEvent, PlayerCommandEvent
  event/server/  ServerEvent, ServerStartEvent, ServerStopEvent
  event/block/   BlockEvent, BlockPlaceEvent, BlockBreakEvent
  entity/    Player
  world/     World, BlockSnapshot
  scheduler/ Scheduler, TaskHandler
  command/   Command, CommandSender, PluginCommand
  permission/ Permissible, PermissionAttachment
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
  EventBridge            helpers to call events from packet handlers and tick code
  PluginCommandAdapter   bridges API Command to server CommandManager
```

The existing `Scheduler`, `CommandManager`, `PlayerManager`, `World`, `Logger` and
`NetherrackConfig` stay as they are; the plugin layer adapts to them rather than rewriting
them. API-facing types never leak into their signatures.

## Phase 1 — lifecycle only

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

## Phase 2 — events

- `Event` is abstract with `getEventName()`; `Cancellable` is a separate interface with
  `setCancelled(boolean)` / `isCancelled()`.
- `Listener` is a marker interface. `@EventHandler(priority = EventPriority.NORMAL,
  ignoreCancelled = true)` marks methods with one `Event` parameter.
- `EventPriority` in ascending order: `LOWEST`, `LOW`, `NORMAL`, `HIGH`, `HIGHEST`, `MONITOR`.
  `MONITOR` handlers must not modify or cancel the event.
- `HandlerList` is held per event class. Prefer a central
  `Map<Class<? extends Event>, HandlerList>` in the manager over Nukkit's
  reflection-over-static-fields approach; keep a static `getHandlers()` on each event class
  only if it buys compatibility.
- Registration caches the reflected `Method` (or a `MethodHandle`) at register time; dispatch
  wraps invocation errors in `EventException`, logs them, and keeps calling later handlers.
- Unregister by plugin on disable — stale listeners are the most likely way to get a crash
  after a reload.

First events, in this order:

| Event | Cancellable | Hook point |
| --- | --- | --- |
| `ServerStartEvent` / `ServerStopEvent` | no | around `Netherrack.start()` / shutdown |
| `PlayerLoginEvent` | yes | `NetherrackPacketHandler.handle(LoginPacket)`, before play status |
| `PlayerJoinEvent` | no | `PlayerManager.join(...)` |
| `PlayerQuitEvent` | no | `PlayerManager.leave(...)` / `onDisconnect()` |
| `PlayerChatEvent` | yes | `handle(TextPacket)`, before broadcast |
| `PlayerCommandEvent` | yes | `runCommand(...)`, before `CommandManager.dispatch(...)` |
| `BlockPlaceEvent` / `BlockBreakEvent` | yes | `BlockInteraction` place/break paths |

**Threading rule to settle before implementing:** packet handlers run on Netty threads while
world state is tick-thread owned. Cancellable events that touch the world must be dispatched on
the tick thread via the scheduler; chat/command events can run on the packet thread if they only
read state. Document the thread for every event rather than leaving it implicit.

## Phase 3 — scheduler, commands, permissions

- The current `Scheduler` only supports one-shot `schedule(int ticks, Runnable)`. Add repeating
  tasks and cancellation ids in the server, then expose `Scheduler` / `TaskHandler` in the API:
  `runTask`, `runTaskLater`, `runTaskTimer`, `cancelTask`. Every task records its owning plugin
  so `onDisable()` can cancel them all.
- API `Command` and `CommandSender` are independent of the server versions, with an adapter in
  `PluginCommandAdapter`. `plugin.yml`'s `commands:` block registers aliases, descriptions and
  permissions automatically.
- API `Permissible.hasPermission(String)` and `PermissionAttachment` map onto the existing
  `Permission` / `Permissions` classes. Keep the server's permission level system intact —
  API permission strings are an additional layer, not a replacement.

## Phase 4 — player, world and block API

- `Player`: name, UUID/XUID, position, teleport, sendMessage, kick, game mode, inventory,
  world, permission checks.
- `World`: name, seed, spawn, `getBlock(x,y,z)`, `setBlock(x,y,z,snapshot)`, save.
- `BlockSnapshot`: immutable name/state value type so plugins never hold a live server `Block`.
- Only add what an event needs; avoid designing the entire game in the API before there are
  plugins using it.

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
  ordering by priority, cancellation with `ignoreCancelled`, and unregister-on-disable.
- This repo: extend `tests/run.sh` with a smoke test that loads a stub plugin jar and asserts
  the order `onLoad → onEnable → (events fire) → onDisable`, plus a cancelled event not reaching
  the server action.
- Verify a real server start with one example plugin before calling any phase done.

## Decisions

1. Package root: `com.netherrack.api.*`.
2. API version: independent `0.4.0`, published as the JitPack tag `v0.4.0-SNAPSHOT`.
3. Plugin config format: API-owned mini-YAML.
4. Event dispatch threading rule: still to settle before events are implemented.
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
