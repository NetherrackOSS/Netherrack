#!/usr/bin/env bash
# Plain-Java check for whitelist.json and the white-list setting. Run from anywhere.
set -euo pipefail
cd "$(dirname "$0")/.."

bash mvnw -q clean compile
bash mvnw -q dependency:build-classpath -Dmdep.outputFile=target/whitelist-cp.txt
CLASSPATH="$(cat target/whitelist-cp.txt)"

mkdir -p target/whitelist-test-classes
javac -cp "target/classes:${CLASSPATH}" -d target/whitelist-test-classes tests/whitelist/*.java
java -cp "target/whitelist-test-classes:target/classes:${CLASSPATH}" \
     com.netherrack.server.player.WhitelistTest
