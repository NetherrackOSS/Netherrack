# Installation Instructions

This guide covers installing and launching **Netherrack v0.6.0-beta**. For an overview of
the project and its features, see the [README](README.md).

## Requirements

- **Java 17 or newer** on the server machine (`java --version` to check)
- **Minecraft: Bedrock Edition 1.26.60** on the client machine — other versions are not
  supported, since Netherrack uses the NetherNet transport that replaced RakNet in that
  release
- A free **port 19132** (configurable in `server.properties`)
- **Git**, only if you are building from source

The NetherNet native WebRTC library is bundled in the JAR; you do not need to install it
separately.

## Install from a release

1. Download the latest JAR from the [Releases](https://github.com/NetherrackOSS/Netherrack/releases)
   page.
2. Move the JAR into a dedicated directory, for example `netherrack-server`, and rename it to
   `netherrack.jar` if needed.
3. Launch it from that directory:

```bash
java -jar netherrack.jar
```

If the native transport reports memory-access or native-access errors, use the JVM flags from
the included start scripts:

```bash
java --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED -jar netherrack.jar
```

You can also use the bundled scripts instead of typing the command yourself: `start.sh` on
Linux/macOS, or `start.bat` on Windows. Both expect `netherrack.jar` in the same directory and
already include the flags above.

## Build from source

Clone the repository and build with the Maven wrapper — no separate Maven installation is
needed:

```bash
git clone https://github.com/NetherrackOSS/Netherrack.git
cd Netherrack
```

```bash
bash mvnw clean package    # Linux/macOS
mvnw.cmd clean package     # Windows
```

The shaded, runnable JAR is written to `target/netherrack.jar`. Copy it to a dedicated server
directory before running it, so generated config, logs, and worlds stay together.

## First run

On the first launch, when `server.properties` does not exist yet, an interactive setup wizard
runs before the server starts:

1. Choose a console language: `en-US` (English) or `pt-BR` (Português). Press Enter for
   `en-US`.
2. Read the GPLv3 notice and accept the license with `y` (also accepts `yes`, `s`, or `sim`).
   The server cannot start without accepting it.
3. Choose whether to enter the server setup steps. Press Enter for defaults, or answer `n` to
   configure them.
4. Enter the MOTD, port, default gamemode and maximum player count. Press Enter at any prompt
   to keep the default shown. The gamemode menu accepts `0` Survival, `1` Creative,
   `2` Adventure or `3` Spectator.
5. Review the local and external addresses, then press Enter to start. The external lookup uses
   a public IP service and is skipped with a "not detected" message if unavailable.

If stdin is piped instead of coming from a terminal, the optional setup prompts are skipped so
scripted launches don&rsquo;t lose console commands.

You should see something like this:

```
[16:01:07] [main] [INFO] Starting Netherrack server version 0.6.0-beta
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

After the wizard finishes, the server generates:

| File | Purpose |
| --- | --- |
| `server.properties` | Server and world settings |
| `netherrack.yml` | Netherrack-specific settings |
| `permissions.json` | Operator/permission data |
| `whitelist.json` | Players allowed in when `white-list` is enabled |
| `banned-players.json` | Banned players, reasons and who banned them |
| `nethernet-identity.pem` | Persistent NetherNet server identity |
| `logs/latest.log` | Server log |
| `worlds/Bedrock level/` | The default world |

## Connecting

Open Bedrock Edition 1.26.60 and add a server using the machine's IP address and port
`19132`. For a server running on the same machine, use `127.0.0.1` as the address.

To change the port or IP, stop the server, edit `server-port` / `server-ip` in
`server.properties`, and start it again.

## Troubleshooting

- **`java` is not recognized / wrong version:** Install Java 17 or newer and check with
  `java --version`.
- **Port already in use:** Change `server-port` in `server.properties` to a free port, then
  restart the server.
- **Native library or illegal-access errors:** Use `start.sh` / `start.bat`, or add the JVM
  flags shown above to your launch command.
- **Client cannot connect:** Confirm the client is Bedrock 1.26.60, the IP and port are
  correct, and any firewall allows the connection.
- **Setup wizard appears again:** It only runs when `server.properties` is missing; running
  from a different directory creates a fresh server there. Always launch from the directory
  containing your config and `worlds/`.
