#!/usr/bin/env bash
# Plain-Java check for the server side of the plugin form API. Run from anywhere.
set -euo pipefail
cd "$(dirname "$0")/.."

bash mvnw -q clean compile
bash mvnw -q dependency:build-classpath -Dmdep.outputFile=target/form-cp.txt
CLASSPATH="$(cat target/form-cp.txt)"

mkdir -p target/form-test-classes
javac -cp "target/classes:${CLASSPATH}" -d target/form-test-classes tests/form/*.java
java -cp "target/form-test-classes:target/classes:${CLASSPATH}" \
     --add-modules jdk.unsupported com.netherrack.server.plugin.FormApiTest
