# Third-party notices

Netherrack itself is licensed under the [GNU GPL v3.0](LICENSE). This file lists the
third-party code and data that Netherrack redistributes — either shaded into the
`netherrack.jar` built by Maven or bundled under `src/main/resources/data/`.

Versions below are the ones declared in `pom.xml` for Netherrack 0.4.0-beta. If a
dependency is added, removed or upgraded, this file should be updated with it.

## Bundled vanilla data

| Files | Source | License | Local notice |
| --- | --- | --- | --- |
| `recipes.json`, `block_palette.nbt` | [CloudburstMC/Data](https://github.com/CloudburstMC/Data) | Apache-2.0 | [`LICENSE-CloudburstMC.txt`](src/main/resources/data/LICENSE-CloudburstMC.txt) |
| `entity_identifiers.nbt`, `biome_definitions.nbt` | [PMMP/BedrockData](https://github.com/pmmp/BedrockData) | CC0 1.0 | — |
| `item_definitions.nbt`, `creative_items.nbt`, `data_driven_blocks.nbt` | [df-mc/Dragonfly](https://github.com/df-mc/dragonfly), commit `4c7b5074be94` | MIT | [`LICENSE-dragonfly.txt`](src/main/resources/data/LICENSE-dragonfly.txt) |
| `block_mining.json` | [PrismarineJS/minecraft-data](https://github.com/PrismarineJS/minecraft-data), commit `9001b1ea` | MIT | [`LICENSE-minecraft-data.txt`](src/main/resources/data/LICENSE-minecraft-data.txt) |

The files above were converted into the wire-ready NBT/JSON Netherrack loads at runtime
using tools from CloudburstMC's Protocol and Data repositories (Apache-2.0); that
conversion does not change the license of the underlying data. The CloudburstMC license
file also covers those tools. Projects consulted as references but not redistributed —
such as Cloudburst Nukkit and protocol dumps — are not listed here.

## Libraries included in the jar

### Apache License 2.0

| Component | Version | Upstream |
| --- | --- | --- |
| CloudburstMC Network — `netty-transport-nethernet`, `netty-transport-common` | 2.0.0.CR6-SNAPSHOT | [CloudburstMC/Network](https://github.com/CloudburstMC/Network) |
| CloudburstMC Network — `netty-transport-raknet` (transitive) | 1.1.0.CR4-SNAPSHOT | [CloudburstMC/Network](https://github.com/CloudburstMC/Network) |
| CloudburstMC Protocol — `bedrock-codec`, `bedrock-connection`, `common` | 3.0.0.Beta15-SNAPSHOT | [CloudburstMC/Protocol](https://github.com/CloudburstMC/Protocol) |
| CloudburstMC NBT — `nbt` | 3.1.0.Final | [CloudburstMC/NBT](https://github.com/CloudburstMC/NBT) |
| CloudburstMC fastutil — `core`, `commons`, `maps`, `sets` modules | 8.5.15 | [CloudburstMC/fastutil](https://github.com/CloudburstMC/fastutil) |
| Netty — `netty-common`, `netty-buffer`, `netty-codec`, `netty-transport`, `netty-resolver`, `netty-transport-native-unix-common`, `netty-codec-http`, `netty-handler`, `netty-codec-haproxy` | 4.1.130.Final | [netty/netty](https://github.com/netty/netty) |
| ExpiringMap | 0.5.10 | [jhalterman/expiringmap](https://github.com/jhalterman/expiringmap) |
| jose4j | 0.9.6 | [b_c/jose4j](https://bitbucket.org/b_c/jose4j/) |
| Gson | 2.13.2 | [google/gson](https://github.com/google/gson) |
| Jackson Annotations | 2.18.0 | [FasterXML/jackson-annotations](https://github.com/FasterXML/jackson-annotations) |
| aircompressor | 2.0.3 | [airlift/aircompressor](https://github.com/airlift/aircompressor) |
| leveldb-mcpe-java — `leveldb`, `leveldb-api` | 1.1.0 | [HiveGamesOSS/leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java) |
| Guava — `guava`, `failureaccess`, `listenablefuture` | 33.4.8-jre, 1.0.3, 9999.0-empty-to-avoid-conflict-with-guava | [google/guava](https://github.com/google/guava) |
| JSpecify | 1.0.0 | [jspecify/jspecify](https://github.com/jspecify/jspecify) |
| Error Prone Annotations | 2.36.0 | [google/error-prone](https://github.com/google/error-prone) |
| J2ObjC Annotations | 3.0.0 | [google/j2objc](https://github.com/google/j2objc) |
| NukkitX natives — `natives` | 1.0.3 | [CloudburstMC/Natives](https://github.com/CloudburstMC/Natives) |

License text: <https://www.apache.org/licenses/LICENSE-2.0>. A full copy is also bundled at
[`LICENSE-CloudburstMC.txt`](src/main/resources/data/LICENSE-CloudburstMC.txt).

### MIT License

| Component | Version | Upstream |
| --- | --- | --- |
| libdatachannel Java binding — `libdatachannel-java`, `libdatachannel-java-arch-detect` (Java code) | 0.24.6.0-SNAPSHOT | [opencollab-incubator/libdatachannel-java](https://github.com/opencollab-incubator/libdatachannel-java) |
| Bouncy Castle — `bcpkix-jdk18on`, `bcutil-jdk18on`, `bcprov-jdk18on` | 1.85 | [bc-java](https://github.com/bcgit/bc-java) |
| SLF4J API — `slf4j-api` | 2.0.18 | [qos-ch/slf4j](https://github.com/qos-ch/slf4j) |
| CloudburstMC Math — `immutable` | 2.0-SNAPSHOT | [CloudburstMC/math](https://github.com/CloudburstMC/math) |

License text: <https://opensource.org/licenses/MIT>. Full copies are bundled at
[`LICENSE-dragonfly.txt`](src/main/resources/data/LICENSE-dragonfly.txt) and
[`LICENSE-minecraft-data.txt`](src/main/resources/data/LICENSE-minecraft-data.txt).

### BSD 3-Clause License

| Component | Version | Upstream |
| --- | --- | --- |
| JLine — `jline-terminal`, `jline-native`, `jline-terminal-jni`, `jline-reader` | 3.29.0 | [jline/jline3](https://github.com/jline/jline3) |

License text: <https://opensource.org/licenses/BSD-3-Clause>.

### Mozilla Public License 2.0

| Component | Version | Upstream |
| --- | --- | --- |
| libdatachannel native libraries (the `.so`, `.dll` and `.dylib` binaries bundled by `libdatachannel-java-arch-detect`) | 0.24.x | [paullouisageneau/libdatachannel](https://github.com/paullouisageneau/libdatachannel) |

These native binaries are redistributed unmodified. The Java binding that loads them is MIT
licensed (listed above); the C++ library itself has been MPL-2.0 licensed since 0.18.
License text and source: <https://www.mozilla.org/en-US/MPL/2.0/> and the upstream
repository linked above.

### CC0 1.0 Universal

The PMMP BedrockData files listed under "Bundled vanilla data" are dedicated to the public
domain under CC0 1.0: <https://creativecommons.org/publicdomain/zero/1.0/>.

## Build tooling (not redistributed)

The Maven wrapper, the Maven compiler/jar/shade plugins and the git-commit-id plugin are
used only to build Netherrack; none of their code is included in the distributed jar. See
their respective projects for their licenses.
