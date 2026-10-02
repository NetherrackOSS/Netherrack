package com.netherrack.server;

import com.netherrack.server.util.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ServerConfig {

    private final Properties properties = new Properties();
    private final Path path;

    public ServerConfig(Path path) {
        this.path = path;
    }

    public void load() {
        if (!Files.exists(path)) {
            Logger.warn("server.properties not found, creating default configuration...");
            writeDefaults();
        }
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            Logger.error("Failed to load server.properties: " + e.getMessage());
        }
    }

    private void writeDefaults() {
        properties.setProperty("server-name", "Netherrack Server");
        properties.setProperty("server-ip", "0.0.0.0");
        properties.setProperty("server-port", "19132");
        properties.setProperty("gamemode", "survival");
        properties.setProperty("difficulty", "normal");
        properties.setProperty("max-players", "20");
        properties.setProperty("level-name", "world");
        properties.setProperty("view-distance", "10");
        properties.setProperty("online-mode", "true");

        try (OutputStream out = Files.newOutputStream(path)) {
            properties.store(out, "Netherrack server configuration");
        } catch (IOException e) {
            Logger.error("Failed to write default server.properties: " + e.getMessage());
        }
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
    }
}
