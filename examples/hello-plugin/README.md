# HelloPlugin

A minimal Maven plugin for Netherrack, showing `onLoad()`, `onEnable()`, `onDisable()`, a
listener for server, player and chat events, a permission node, a `/hello` command and a delayed
scheduler task.

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

Players receive `Welcome, <name>!` when they join. Typing `!hello` in chat sends them the greeting
from `config.yml` privately and cancels the public chat message. `/hello` (or `/hi`) sends the same
greeting as command output; its `hello.use` node is registered with an allow default, so any player
can run it. A one-tick delayed task prints `[HelloPlugin] HelloPlugin's delayed task ran.` after
startup. Stopping the server prints both `[HelloPlugin] HelloPlugin heard the server stop.` and
`[HelloPlugin] HelloPlugin disabled`.

The plugin depends on `NetherrackAPI` with `provided` scope, so its jar does not bundle the API —
the server supplies it.
