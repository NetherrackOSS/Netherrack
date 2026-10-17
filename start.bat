@echo off
set "JAR_FILE=netherrack-0.1.0-alpha.jar"

if not exist "%JAR_FILE%" (
    echo Error: The JAR file for the server was not found.
    pause
    exit /b 1
)

java --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED -jar "%JAR_FILE%"
