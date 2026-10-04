# Contributing to Netherrack

Thank you for your interest in contributing to **Netherrack**!

Netherrack is an experimental, open-source Minecraft: Bedrock Edition server software project written in Java. The project is being developed from the ground up with a focus on learning, experimentation, networking, protocol implementation, and server architecture.

Because Netherrack is still in early development, contributions of all kinds are welcome — whether you're fixing a bug, improving the codebase, researching the Bedrock protocol, adding a feature, improving documentation, or simply helping test the server.

## 📋 Before You Start

Before contributing, please:

1. Read the [README](README.md).
2. Check existing issues and pull requests to make sure your idea hasn't already been discussed.
3. For larger changes, open an issue first so the approach can be discussed before significant development work begins.
4. Make sure your changes are compatible with the project's goals and licensing.

Netherrack is still evolving rapidly, so APIs, internal systems, and project structure may change without notice.

## 🛠️ Development Setup

Netherrack uses **Java and Maven**.

### Requirements

You will need:

* A supported Java Development Kit (JDK)
* Apache Maven
* Git

A Linux development environment is recommended for testing, although contributions should avoid unnecessarily being Linux-specific unless the feature itself requires it.

### Clone the Repository

```bash
git clone https://github.com/NetherrackOSS/Netherrack.git
cd Netherrack
```

### Build the Project

Use Maven to build Netherrack:

```bash
mvn clean package
```

The resulting JAR can be found in the `target/` directory.

## 🧪 Testing Your Changes

Please test your changes before submitting a pull request.

Depending on what you changed, this may include:

* Starting and stopping the server.
* Testing console commands.
* Testing `server.properties`.
* Connecting with a Bedrock Edition client.
* Testing login and networking behavior.
* Testing block interactions.
* Testing world-related functionality.
* Testing on Linux where applicable.
* Checking that existing functionality has not regressed.

For networking or protocol-related changes, testing with an actual Bedrock client is strongly encouraged.

If your change cannot currently be tested because the relevant Netherrack systems do not exist yet, explain this in your pull request.

## 🌱 Areas Where Contributions Are Welcome

Netherrack is still a young project, so there are many areas where contributions can help.

Examples include:

* Bedrock protocol implementation
* RakNet/networking integration
* Login and play handling
* World and chunk systems
* Block systems
* Entity systems
* Persistent world storage
* Server configuration
* Console commands
* Logging
* Performance improvements
* Testing
* Documentation
* Bug fixes
* Developer tooling
* Linux compatibility
* Future plugin/API development

If you're interested in implementing something that isn't currently listed, feel free to open an issue and discuss it.

## 🧱 Architecture and Design

Netherrack is being developed independently rather than as a clone of an existing Minecraft server implementation.

When contributing, please keep the project's architecture and long-term goals in mind.

Avoid copying implementation code from other server software. Inspiration and research are welcome, but contributed code should be independently implemented and appropriately licensed.

Netherrack uses open-source CloudburstMC components for networking and Bedrock protocol functionality. Contributions involving these components should respect their respective licenses and upstream projects.

## ✨ Code Style

Try to keep code:

* Readable
* Simple
* Consistent with the surrounding code
* Properly named
* Focused on one responsibility where practical

Avoid unnecessarily complicated abstractions, especially when a simpler implementation is sufficient.

Comments should explain **why** something is done when the reason isn't obvious from the code itself.

## 🐛 Reporting Bugs

When reporting a bug, please provide as much useful information as possible.

A good bug report should include:

* What happened
* What you expected to happen
* Steps to reproduce the issue
* Netherrack version or commit
* Operating system
* Java version
* Minecraft Bedrock Edition version
* Relevant console output or stack traces
* Any additional information that could help reproduce the problem

For crashes, please include the complete relevant stack trace whenever possible.

## 💡 Feature Requests

Feature requests are welcome.

Before opening one, consider whether the feature fits Netherrack's current goals.

A useful feature request should explain:

* What the feature would do
* Why it would be useful
* How it could potentially work
* Whether it depends on other unfinished systems

Keep in mind that some features may be intentionally postponed while the underlying server architecture is being developed.

## 🔀 Pull Requests

When submitting a pull request:

1. Keep the PR focused on one change or closely related set of changes.
2. Explain what you changed.
3. Explain why you changed it.
4. Mention how you tested it.
5. Include relevant issue numbers when applicable.
6. Avoid unrelated formatting or refactoring changes.
7. Make sure the project still builds successfully.

A good pull request description might look like:

```text
## What does this PR do?

Adds basic support for XYZ.

## Why?

This is required for the next stage of the login/play flow.

## Testing

- [x] `mvn clean package`
- [x] Tested server startup
- [x] Tested with Bedrock client
- [x] Tested on Linux
```

Pull requests may be requested to change, simplified, or declined if they do not fit the project's current direction. This is especially likely for large architectural changes in an early-stage project.

## 📦 Dependencies

If your contribution requires a new dependency:

* Explain why it is needed.
* Prefer established and actively maintained libraries.
* Make sure its license is compatible with Netherrack's GPL-3.0 license.
* Avoid adding dependencies for functionality that can reasonably be implemented without one.

Do not copy third-party source code into Netherrack without verifying that its license permits doing so.

## 🤖 AI-Assisted Contributions

AI-assisted development is allowed.

However, contributors are responsible for the code they submit.

If you use an AI coding assistant:

* Review the generated code before submitting it.
* Make sure you understand what the code does.
* Verify that it builds and behaves correctly.
* Check for copied or potentially copyrighted implementation details.
* Do not submit large amounts of unreviewed generated code simply because it compiles.

AI assistance does not remove the contributor's responsibility for their contribution.

## 📜 Licensing

Netherrack is licensed under the **GNU General Public License v3.0**.

By submitting a contribution, you agree that your contribution may be distributed under the project's license.

Do not submit code that you do not have the right to contribute.

For third-party code or dependencies, make sure their licenses are compatible and properly respected.

## 🌐 Minecraft and Mojang/Microsoft

Netherrack is an independent project.

Netherrack is **not affiliated with, endorsed by, or sponsored by Mojang Studios or Microsoft**.

Contributors should not represent Netherrack as an official Minecraft product.

## 🤝 Community

Please be respectful when interacting with other contributors.

Constructive criticism, technical disagreement, and different approaches are completely normal in open-source development. Personal attacks, harassment, discrimination, or deliberately disruptive behavior are not acceptable.

Remember that Netherrack is a learning and experimental project. Not every contribution needs to be perfect on the first attempt.

## 🚀 Final Notes

Netherrack is still at an early stage, and there is a lot left to build.

If you're interested in Minecraft server development, Java, networking, Bedrock protocol research, world systems, or simply experimenting with how a server works internally, you're welcome to contribute.

**Have fun, experiment, and help build Netherrack!**
