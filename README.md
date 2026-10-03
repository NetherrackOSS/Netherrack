<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/logo-dark.png">
    <source media="(prefers-color-scheme: light)" srcset=".github/logo-light.png">
    <img alt="Netherrack Logo" src="/logo-light.png" width="75%">
  </picture>
</p>

<p align="center"><b>Open source server software for Minecraft: Bedrock Edition written in Java</b></p>

<p align="center">
  <img src="https://img.shields.io/github/license/NetherrackOSS/Netherrack?style=flat-square" alt="License">
  <img src="https://img.shields.io/github/stars/NetherrackOSS/Netherrack?style=flat-square" alt="GitHub Stars">
  <img src="https://img.shields.io/badge/Minecraft-Bedrock-62B47A?style=flat-square" alt="Minecraft Bedrock">
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17+">
  <img src="https://img.shields.io/badge/Build-Maven-C71A36?style=flat-square" alt="Maven">
</p>

# ℹ️ Information
Netherrack is an experimental vibe coded Minecraft: Bedrock Edition server software project focused on learning, experimentation, and building a server implementation from the ground up.
> [!IMPORTANT]
> Netherrack is not afilliated by Mojang or Microsoft.

## 🚧 Current status

Netherrack is currently in **early development**.

The project started as a server-software simulation and is gradually being turned into a real Bedrock server. Features are incomplete and APIs may change significantly.

### Currently implemented

- [x] Java/Maven project
- [x] Server startup and shutdown lifecycle
- [x] `server.properties` configuration
- [x] Console command handling
- [x] Colored console logging
- [x] Linux support/testing
- [x] RakNet networking
- [x] Bedrock protocol integration
- [x] Basic Bedrock login handling
- [x] Initial block system
- [x] Grass block
- [x] Cobblestone block
- [ ] Complete world system
- [ ] Chunk loading and generation
- [ ] Complete Bedrock play/login flow
- [ ] Entity system
- [ ] Persistent world storage
- [ ] Plugin API
- [ ] More blocks and gameplay features

## 🧱 Project goals

The long-term goal is to create a flexible, open-source Bedrock server software implementation while learning more about Java, networking, Minecraft's Bedrock protocol, world systems, and server architecture.

Netherrack is **not intended to be a clone of another server implementation**. The server architecture and functionality are being developed independently.

## 🌐 Networking

Netherrack uses open-source CloudburstMC components for networking and Bedrock protocol functionality.
