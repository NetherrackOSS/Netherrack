# Contributing to Netherrack

Thank you for your interest in contributing to Netherrack!

Netherrack is an open-source Minecraft: Bedrock Edition server implementation written in Java. The project is currently in early development and is being built from the ground up with a focus on learning, experimentation, networking, protocol implementation, world systems, and server architecture.

Because Netherrack is still evolving, contributions of all kinds are welcome — code, testing, documentation, bug reports, protocol research, ideas, and experimentation.

Table of Contents

Before You Start

Development Environment

Getting the Code

Building Netherrack

Project Structure

Making Changes

Code Guidelines

Testing

Commit Guidelines

Pull Requests

Bug Reports

Feature Requests

Protocol and Networking Contributions

Documentation Contributions

What We Look For

What to Avoid

License

Before You Start

Please keep in mind that Netherrack is experimental and under active development.

APIs, internal architecture, networking code, and project conventions may change without notice. A contribution that makes sense today may need to be revised as the project develops.

Before starting a large change, especially architectural changes, it is recommended to open an issue or discussion first so the direction can be agreed upon.

Small fixes and improvements can generally be submitted directly as pull requests.

Development Environment

Netherrack currently uses:

Java 17 or newer

Maven

Git

A development environment capable of running Java applications

Netherrack uses CloudburstMC components for RakNet transport and Minecraft: Bedrock protocol functionality.

Make sure Java is available:

java -version


You should be using Java 17 or newer.

You can also verify Maven:

mvn -version

Getting the Code

Fork the repository on GitHub and clone your fork:

git clone https://github.com/YOUR_USERNAME/Netherrack.git
cd Netherrack


Add the upstream repository:

git remote add upstream https://github.com/NetherrackOSS/Netherrack.git


You can verify your remotes with:

git remote -v


Before starting new work, make sure your local branch is up to date:

git fetch upstream
git checkout main
git pull upstream main


Create a branch for your change:

git checkout -b feature/my-change


Use a descriptive branch name whenever possible.

Examples:

feature/entity-system
feature/chunk-generation
fix/packet-handling
fix/server-shutdown
docs/contributing-guide
refactor/world-storage

Building Netherrack

Netherrack is built with Maven.

To compile the project:

mvn compile


To create a distributable JAR:

mvn package


The Maven build produces a shaded JAR containing the project's required dependencies, allowing the resulting server JAR to be run directly with Java.

For example:

java -jar target/netherrack-0.1.0-SNAPSHOT.jar


If your change affects the build system or dependencies, make sure a clean build succeeds before opening a pull request.

mvn clean package

Project Structure

The repository is currently intentionally small.

The primary source code is located under:

src/main/


The project is built using:

pom.xml


Documentation and project-level configuration may be located in the repository root or under:

.github/


As the project grows, additional modules and directories may be introduced.

Do not assume that the current structure is permanent.

Making Changes

When working on Netherrack, try to keep changes focused.

A good pull request should generally solve one problem or implement one cohesive feature.

For example:

Good:

Add basic chunk serialization


Less desirable:

Rewrite networking, add entities, change logging,
reformat the entire project, and update dependencies


Large changes are sometimes necessary, but they should be discussed beforehand when they significantly affect the project's architecture.

Keep Changes Focused

Avoid unrelated modifications such as:

Reformatting files unrelated to your change

Renaming unrelated classes

Changing dependencies without a reason

Modifying unrelated behavior

Mixing large refactors with feature work

Committing generated files

Focused changes make code review substantially easier.

Code Guidelines

Netherrack is a Java project. Follow the existing style of the code you are modifying.

In general:

Prefer clear and descriptive names.

Keep methods reasonably small and focused.

Avoid unnecessary abstractions.

Prefer straightforward code over clever code.

Document non-obvious behavior.

Avoid introducing dependencies when the JDK or existing project dependencies are sufficient.

Handle errors deliberately rather than silently ignoring them.

Avoid unnecessary global state.

Keep public APIs minimal until they are well established.

Comments

Comments should explain why something is done when the reason is not obvious from the code.

Prefer:

// Bedrock clients expect this packet to be sent after the connection is established.
sendLoginSuccess();


over comments that simply repeat the code:

// Send login success.
sendLoginSuccess();


Temporary debugging comments and dead code should not be committed.

Testing

Netherrack is still in an early stage, so not every subsystem currently has comprehensive automated tests.

Nevertheless, contributors should test their changes as thoroughly as reasonably possible.

At minimum, run:

mvn clean package


If your change affects server behavior, networking, protocol handling, world logic, commands, or configuration, test the affected functionality manually as well.

When submitting a pull request, mention what you tested.

For example:

Testing:
- mvn clean package
- Started the server successfully
- Connected using a Bedrock client
- Verified the new command


If a change cannot currently be tested automatically, explain how you tested it manually.

Commit Guidelines

Write commits that clearly describe the change being made.

Prefer:

Add basic chunk loading

Fix server shutdown handling

Implement grass block registration


over:

stuff

changes

fixed things


A commit does not need to follow a complicated conventional-commit format unless the project adopts one in the future.

The most important thing is that the commit message accurately describes the change.

Pull Requests

When your work is ready, push your branch:

git push origin feature/my-change


Then open a pull request against the main branch of Netherrack.

A good pull request should explain:

What changed?

Briefly describe the implementation.

Why?

Explain the problem being solved or the reason for the change.

How was it tested?

Include the commands and manual testing you performed.

For example:

## Summary

Adds the initial implementation of chunk loading.

## Testing

- mvn clean package
- Started Netherrack locally
- Loaded a test world
- Verified chunks are loaded correctly

Keep Pull Requests Reviewable

Please avoid unnecessarily large pull requests.

If a feature requires substantial work, consider breaking it into several smaller pull requests.

For example:

Add the basic data structures.

Add serialization.

Add loading.

Add persistence.

Integrate the system with the server.

This makes it easier to review and identify problems.

Review Feedback

Pull requests may receive requests for changes.

Please treat review as part of the development process rather than as criticism. The goal is to produce a maintainable project and establish good architecture as Netherrack grows.

Bug Reports

Before opening a bug report, make sure you are testing a reasonably current version of Netherrack.

When reporting a bug, include as much useful information as possible.

A good report should include:

What you expected to happen

What actually happened

Steps to reproduce the problem

Java version

Operating system

Netherrack version or commit

Relevant console output or stack traces

Minecraft Bedrock client version, when relevant

Any configuration required to reproduce the issue

For example:

## Bug

The server crashes when a client disconnects during login.

## Steps to Reproduce

1. Start Netherrack.
2. Connect with a Bedrock client.
3. Disconnect before login completes.

## Expected Behavior

The connection should be closed cleanly.

## Actual Behavior

The server throws an exception and terminates.

## Environment

Java: 17
OS: Linux
Netherrack: <commit>


Please remove sensitive information from logs before posting them.

Feature Requests

Feature requests are welcome.

Before proposing a feature, consider whether it fits Netherrack's current goals.

Netherrack aims to develop an independent Bedrock server implementation from the ground up. It is not intended to be a clone of another server implementation.

When proposing a feature, explain:

What the feature does

Why it would be useful

How you think it could fit into Netherrack

Any relevant Bedrock protocol or Minecraft behavior

Whether you are willing to implement it yourself

For large architectural changes, discussing the design before implementation is strongly encouraged.

Protocol and Networking Contributions

Networking is a particularly important part of Netherrack.

The project uses CloudburstMC components for RakNet transport and Bedrock protocol functionality.

When working on networking or protocol code:

Reference the relevant Bedrock protocol behavior when possible.

Avoid guessing when packet behavior can be verified.

Keep protocol-specific behavior isolated where practical.

Document unusual protocol behavior.

Test both successful and invalid packet flows where possible.

Be especially careful with connection lifecycle and resource management.

If you have discovered behavior through protocol research, packet captures, documentation, or experimentation, include that context in your pull request.

Documentation Contributions

Documentation is just as valuable as code.

You can contribute by improving:

Setup instructions

Architecture documentation

Protocol notes

Developer documentation

Examples

Configuration documentation

Troubleshooting information

Comments and JavaDoc

Contribution documentation

If you notice that something is difficult to understand, that is often a good indication that the documentation could be improved.

What We Look For

Good contributions generally:

Solve a real problem.

Keep the implementation understandable.

Fit Netherrack's goals.

Avoid unnecessary complexity.

Include appropriate testing.

Explain non-obvious design decisions.

Keep unrelated changes out of the pull request.

Improve the project for future contributors.

Because Netherrack is still young, we especially value contributions that improve the foundations of the project.

Examples include:

Networking improvements

Protocol handling

World and chunk systems

Block systems

Entity systems

Persistence

Server lifecycle

Configuration

Testing infrastructure

Developer tooling

Documentation

What to Avoid

Please avoid:

Copying large portions of another server implementation.

Introducing unnecessary dependencies.

Making broad architectural changes without discussion.

Committing generated build artifacts.

Committing IDE-specific configuration unless explicitly needed.

Silently changing public behavior.

Hiding errors or exceptions.

Submitting code that has not been tested at all.

Combining unrelated changes into one pull request.

Netherrack's architecture is intentionally being developed independently, so contributions should respect that goal.

Community and Conduct

Please be respectful and constructive when interacting with maintainers and other contributors.

We welcome contributors regardless of experience level.

If you disagree with an implementation or design decision, explain your reasoning and, when possible, provide an alternative.

Technical discussion is encouraged; personal attacks and hostile behavior are not.

If the repository contains a CODE_OF_CONDUCT.md, its requirements apply to all project interactions.

Security Issues

Please do not publicly disclose an exploitable security vulnerability before giving maintainers an opportunity to investigate it.

If the repository provides a security policy or private vulnerability reporting mechanism, use that process instead of opening a public issue.

If no security reporting process currently exists, contact the project maintainers before publicly disclosing a serious vulnerability.

Final Notes

Netherrack is a work in progress.

There are many parts of Minecraft: Bedrock Edition that have not yet been implemented, and the architecture will continue to evolve as the project grows.

You do not need to be an expert in Minecraft internals, networking, or Java to contribute. If you are interested in learning, experimenting, researching the protocol, improving the codebase, or helping build Netherrack, your contribution is welcome.

Thank you for helping build Netherrack! ❤️
