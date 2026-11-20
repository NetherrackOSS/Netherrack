# HelloPlugin

A minimal Maven plugin for Netherrack, showing `onLoad()`, `onEnable()`, `onDisable()` and a
listener for server and player events.

## Build

```bash
cd examples/hello-plugin
mvn clean package
```

The jar is written to `target/hello-plugin-1.0.0.jar`.

## Install

Copy that jar into the server's `plugins/` directory and start Netherrack. On the first start it
creates `plugins/HelloPlugin/config.yml` and prints lines like these:

```
Loaded plugin HelloPlugin v1.0.0 from hello-plugin-1.0.0.jar
[HelloPlugin] HelloPlugin loaded
[HelloPlugin] Hello from HelloPlugin! (running on 0.4.0-beta)
[HelloPlugin] HelloPlugin heard the server start.
```

Players receive `Welcome, <name>!` when they join. Stopping the server prints both
`[HelloPlugin] HelloPlugin heard the server stop.` and `[HelloPlugin] HelloPlugin disabled`.

The plugin depends on `NetherrackAPI` with `provided` scope, so its jar does not bundle the API —
the server supplies it.
