# AGENTS.md

Guidance for AI coding agents working in this repository.

## Project
Netherrack is an experimental vibe coded Minecraft: Bedrock Edition server software project focused on learning, experimentation, and building a server implementation from the ground up.

- Language: Java 17
- Build: Maven (shaded fat JAR)
- Network library: [CloudburstMC/Network](https://github.com/CloudburstMC/Network)
- Protocol library: [CloudburstMC/Protocol](https://github.com/CloudburstMC/Protocol)
- License: GPL-3.0

## Build & Run
```bash
# Compile and package (produces the executable fat JAR)
./mvnw clean package

# Run the server
java -jar target/netherrack.jar
```

- Main class: com.netherrack.server
- `mvn clean compile` is enough for a quick syntax check; `mvn clean package` runs the shade plugin.
- There is currently no automated test suite. Verify changes by starting the server and
  connecting with a Bedrock Edition client (default port `19132`).

## Things to avoid

- Do not commit generated `target/` output or runtime `server.properties`.
- Do not change the Java release level, CloudburstMC/Network or CloudburstMC/Protocol version without a clear reason.
- Do not introduce new dependencies without updating `pom.xml` and noting why.
