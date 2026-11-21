#!/usr/bin/env bash
# Plain-Java check for dropped items burning in lava and fire. Run from anywhere.
set -euo pipefail
cd "$(dirname "$0")/.."

bash mvnw -q clean compile
bash mvnw -q dependency:build-classpath -Dmdep.outputFile=target/item-hazard-cp.txt
CLASSPATH="$(cat target/item-hazard-cp.txt)"

mkdir -p target/item-hazard-test-classes
javac -cp "target/classes:${CLASSPATH}" -d target/item-hazard-test-classes tests/item-hazard/*.java
java -cp "target/item-hazard-test-classes:target/classes:${CLASSPATH}" \
     com.netherrack.server.entity.ItemHazardTest
