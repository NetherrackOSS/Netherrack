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
- **Example Maven plugin:** `examples/hello-plugin/` builds a jar that demonstrates all three
  lifecycle callbacks, a generated `config.yml`, and a listener for server and player events.
- **API regression checks:** the separate `NetherrackAPI` repository has plain-Java checks for
  lifecycle, config, event ordering, cancellation, failure isolation and unregister-on-disable.

### Changed
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
- The plugin API currently covers lifecycle and the first server, player and block events. The
  scheduler, commands and permissions are still planned, as is runtime plugin
  enable/disable/reload.
