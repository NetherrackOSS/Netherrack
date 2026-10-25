#!/bin/bash

JAR_FILE="netherrack.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: The JAR file for the server was not found."
    exit 1
fi

java --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED -jar "$JAR_FILE"
