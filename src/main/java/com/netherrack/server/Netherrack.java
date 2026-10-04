package com.netherrack.server;

import com.netherrack.server.network.RakNetServer;
import com.netherrack.server.util.AnsiSupport;
import com.netherrack.server.util.Logger;
import com.netherrack.server.world.World;
import com.netherrack.server.world.WorldManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class Netherrack {

    public static final String VERSION = "0.1.0";

    private final ServerConfig config;
    private final RakNetServer rakNetServer;
    private final WorldManager worldManager;
    private World world;
    private volatile boolean running = true;

    public Netherrack() {
        this.config = new ServerConfig(Path.of("server.properties"));
        this.rakNetServer = new RakNetServer(config);
        this.worldManager = new WorldManager();
    }

    public static void main(String[] args) {
        AnsiSupport.init();
        new Netherrack().start();
    }

    private void start() {
        Logger.info("Starting Netherrack server version " + VERSION);

        config.load();

        String ip = config.get("server-ip", "0.0.0.0");
        int port = config.getInt("server-port", 19132);
        String levelName = config.get("level-name", "world");
        int maxPlayers = config.getInt("max-players", 20);

        sleep(150);
        Logger.info("Preparing level \"" + levelName + "\"...");
        world = worldManager.loadOrCreate(levelName);
        world.getChunk(0, 0);

        sleep(150);
        Logger.info("Opening server on " + ip + ":" + port + "...");
        rakNetServer.start(world);

        sleep(150);
        Logger.info("This is an early build; no plugin API yet.");

        sleep(300);
        Logger.info("Preparing spawn area...");

        sleep(300);
        Logger.info("Server thread/Netherrack is now running.");
        Logger.info("Done! Max players: " + maxPlayers + ". Type \"help\" for a list of commands.");

        runConsoleLoop();
    }

    private void runConsoleLoop() {
        Logger.bindConsoleThread(Thread.currentThread());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (running) {
                System.out.print("> ");
                System.out.flush();
                Logger.notePromptShown();
                String line = reader.readLine();
                Logger.notePromptConsumed();
                if (line == null) {
                    continue;
                }
                handleCommand(line.trim());
            }
        } catch (Exception e) {
            Logger.error("Console loop terminated: " + e.getMessage());
        }
    }

    private void handleCommand(String command) {
        if (command.isEmpty()) {
            return;
        }

        switch (command.toLowerCase()) {
            case "stop" -> shutdown();
            case "version" -> Logger.info("Netherrack version " + VERSION + " (simulation build)");
            case "help", "?" -> printHelp();
            default -> Logger.info("Unknown command: \"" + command + "\". Type \"help\" or \"?\" for a list of commands.");
        }
    }

    private void printHelp() {
        Logger.info("Available commands:");
        Logger.info("stop - Stops the server");
        Logger.info("version - Shows the server version");
        Logger.info("help, ? - Shows this list of commands");
    }

    private void shutdown() {
        Logger.info("Stopping the server...");
        sleep(200);
        Logger.info("Saving level \"" + config.get("level-name", "world") + "\"...");
        rakNetServer.stop();
        sleep(200);
        Logger.info("Server closed.");
        running = false;
        System.exit(0);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
