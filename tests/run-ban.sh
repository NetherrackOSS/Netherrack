#!/usr/bin/env bash
# Plain-Java check for banned-players.json. Run from anywhere.
set -euo pipefail
cd "$(dirname "$0")/.."

bash mvnw -q clean compile
bash mvnw -q dependency:build-classpath -Dmdep.outputFile=target/ban-cp.txt
CLASSPATH="$(cat target/ban-cp.txt)"

mkdir -p target/ban-test-classes
javac -cp "target/classes:${CLASSPATH}" -d target/ban-test-classes tests/ban/*.java
java -cp "target/ban-test-classes:target/classes:${CLASSPATH}" \
     com.netherrack.server.player.BanTest
