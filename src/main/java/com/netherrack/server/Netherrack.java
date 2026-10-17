package com.netherrack.server;

import com.netherrack.server.command.AvailableCommands;
import com.netherrack.server.command.CommandManager;
import com.netherrack.server.command.ConsoleCommandSender;
import com.netherrack.server.command.DeopCommand;
import com.netherrack.server.command.GamemodeCommand;
import com.netherrack.server.command.HelpCommand;
import com.netherrack.server.command.OpCommand;
import com.netherrack.server.command.StopCommand;
import com.netherrack.server.command.VersionCommand;
import com.netherrack.server.entity.ItemEntities;
import com.netherrack.server.network.ChunkViews;
import com.netherrack.server.network.RakNetServer;
import com.netherrack.server.network.TripwireSensor;
import com.netherrack.server.player.GameModes;
import com.netherrack.server.player.Permission;
import com.netherrack.server.player.Permissions;
import com.netherrack.server.player.Player;
import com.netherrack.server.player.PlayerData;
import com.netherrack.server.player.PlayerManager;
import com.netherrack.server.setup.Lang;
import com.netherrack.server.setup.SetupWizard;
import com.netherrack.server.util.AnsiSupport;
import com.netherrack.server.util.Console;
import com.netherrack.server.util.Logger;
import com.netherrack.server.util.NettyLogBridge;
import com.netherrack.server.util.Scheduler;
import com.netherrack.server.world.World;
import com.netherrack.server.world.WorldManager;

import java.nio.file.Files;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.jline.reader.UserInterruptException;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Netherrack {

    public static final String VERSION = "0.1.0";

    /** 20 ticks a second. */
    private static final long TICK_MILLIS = 50;
    private static final long AUTOSAVE_SECONDS = 30;

    private final ServerConfig config;
    private final NetherrackConfig settings;
    private final RakNetServer rakNetServer;
    private final PlayerManager playerManager = new PlayerManager();
    private final WorldManager worldManager;
    private final CommandManager commandManager;
    private World world;
    private ItemEntities itemEntities;
    private ChunkViews chunkViews;
    private TripwireSensor tripwires;
    private final Scheduler scheduler = new Scheduler();
    private Permissions permissions;
    private ScheduledExecutorService ticker;
    private ScheduledExecutorService autosave;
    private boolean worldClosed;
    private volatile boolean running = true;
    private final java.util.concurrent.atomic.AtomicBoolean stopping = new java.util.concurrent.atomic.AtomicBoolean();

    public Netherrack() {
        this.config = new ServerConfig(Path.of("server.properties"));
        this.settings = new NetherrackConfig(NetherrackConfig.FILE);
        this.rakNetServer = new RakNetServer(config);
        this.worldManager = new WorldManager(config, settings);

        this.commandManager = new CommandManager();
        commandManager.register(new StopCommand());
        commandManager.register(new VersionCommand());
        commandManager.register(new HelpCommand());
        commandManager.register(new GamemodeCommand());
        commandManager.register(new OpCommand());
        commandManager.register(new DeopCommand());
    }

    public ServerConfig getConfig() {
        return config;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public Permissions getPermissions() {
        return permissions;
    }

    /**
     * The command level operators get: op-permission-level from server.properties, 0 to 4.
     * See Command.getPermissionLevel() - at 4, operators may also stop the server.
     */
    public int getOpPermissionLevel() {
        return Math.max(0, Math.min(4, config.getInt("op-permission-level", 2)));
    }

    /** The command level that goes with a permission. */
    public int commandLevelFor(Permission permission) {
        return permission == Permission.OPERATOR ? getOpPermissionLevel() : 0;
    }

    /**
     * Gives an online player a permission, telling their client what they may now do -
     * including which commands to suggest.
     */
    public void applyPermission(Player player, Permission permission) {
        player.setPermission(permission, commandLevelFor(permission));
        player.sendAbilities();
        player.getSession().sendPacket(AvailableCommands.forLevel(commandManager, player.getCommandLevel()));
    }

    public ItemEntities getItemEntities() {
        return itemEntities;
    }

    /** Things to do some ticks from now, like a pressed button popping back out. */
    public Scheduler getScheduler() {
        return scheduler;
    }

    public ChunkViews getChunkViews() {
        return chunkViews;
    }

    /** The furthest players may see, in chunks: view-distance from server.properties. */
    public int getViewDistance() {
        return Math.max(1, config.getInt("view-distance", 32));
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public static void main(String[] args) {
        NettyLogBridge.install();
        AnsiSupport.init();
        new Netherrack().start();
    }

    private void start() {
        Logger.openLogFile(Path.of("logs", "latest.log"));

        // Printed before a language is known either way, same as the wizard's own first
        // screen - there's nothing to translate it into yet.
        Logger.info("Starting Netherrack server version " + VERSION);

        String language = Lang.DEFAULT_CODE;
        if (!Files.exists(Path.of("server.properties"))) {
            Logger.info("First-time setup detected. Launching setup wizard...");
            String chosen = new SetupWizard().run();
            if (chosen == null) {
                Logger.error("Setup wizard did not accept the license. Startup has been cancelled.");
                System.exit(1);
                return;
            }
            language = chosen;
        }

        // Set before config.load() too, so messages like "server.properties not found"
        // (logged from inside that call, on a first run) are already in the right language.
        Lang.current = new Lang(language);

        config.load(language);
        Lang.current = new Lang(config.get("language", language));
        Lang lang = Lang.current;
        settings.load(config);

        permissions = Permissions.load(Permissions.FILE, defaultPermission());

        NettyLogBridge.setDebug(settings.getBoolean("logs.debug"));
        if (NettyLogBridge.isDebug()) {
            Logger.warn(lang.get("server.debug_enabled"));
        }

        String ip = config.get("server-ip", "0.0.0.0");
        int port = config.getInt("server-port", 19132);
        String levelName = config.get("level-name", "Bedrock level");
        int maxPlayers = config.getInt("max-players", 20);

        sleep(150);
        Logger.info(lang.get("server.preparing_level", levelName));
        try {
            world = worldManager.loadOrCreate(levelName);
        } catch (Exception e) {
            // Running on without the world's storage would lose everything players build.
            Logger.error(lang.get("server.level_unreadable", levelName, e.getMessage()));
            System.exit(1);
            return;
        }
        world.getChunk(world.getSpawnX() >> 4, world.getSpawnZ() >> 4);
        // Ctrl+C or closing the console window still saves the world.
        Runtime.getRuntime().addShutdownHook(new Thread(this::closeWorld, "World save"));
        startAutosave();

        sleep(150);
        Logger.info(lang.get("server.opening", ip, port));
        itemEntities = new ItemEntities(world, playerManager);
        chunkViews = new ChunkViews(world);
        tripwires = new TripwireSensor(world, playerManager);
        startTicking();
        rakNetServer.start(world, playerManager, this);

        sleep(150);
        Logger.info(lang.get("server.early_build"));

        sleep(300);
        Logger.info(lang.get("server.preparing_spawn"));

        sleep(300);
        Logger.info(lang.get("server.thread_running"));
        Logger.info(lang.get("server.done", maxPlayers));

        runConsoleLoop();
    }

    /**
     * Runs the commands typed at the console, one line at a time. Ctrl+C at the prompt stops
     * the server as the stop command does. If the console's input runs out, as piped input
     * can, the server runs on without it.
     */
    private void runConsoleLoop() {
        Console.open(AnsiSupport.getTerminal());
        while (running) {
            String line;
            try {
                line = Console.readLine();
            } catch (UserInterruptException e) {
                shutdown();
                return;
            } catch (RuntimeException e) {
                Logger.error("Console loop terminated: " + e.getMessage());
                return;
            }
            if (line == null) {
                Logger.warn(Lang.current.get("server.console_closed"));
                return;
            }
            commandManager.dispatch(this, ConsoleCommandSender.INSTANCE, line.trim());
        }
    }

    /**
     * Stops the server. Safe to call from any thread, and more than once: the stopping itself
     * always runs on its own thread, never the caller's. That matters when a player's /stop
     * arrives on a network thread - closing the RakNet channel waits on that same thread to
     * close the player's connection, so stopping from it would wait on itself forever (the
     * player gets kicked and the server never stops, and a second /stop from the console
     * then waits on the same stuck close).
     */
    public void shutdown() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(this::stopServer, "Server shutdown");
        thread.start();
    }

    private void stopServer() {
        Logger.info(Lang.current.get("server.stopping"));
        // Players see why they were let go, rather than losing the connection.
        String message = settings.getString("messages.server-shutdown");
        for (Player player : List.copyOf(playerManager.getPlayers())) {
            player.getSession().disconnect(message);
        }
        sleep(200);
        try {
            rakNetServer.stop();
        } catch (RuntimeException e) {
            Logger.error("Error closing the network listener: " + e);
        }
        ticker.shutdownNow();
        closeWorld();
        Logger.info(Lang.current.get("server.closed"));
        running = false;
        System.exit(0);
    }

    /**
     * Saves the world and closes its storage, once: on stopping the server, or from the
     * shutdown hook when the process is ended some other way.
     */
    private synchronized void closeWorld() {
        if (world == null || worldClosed) {
            return;
        }
        worldClosed = true;
        if (autosave != null) {
            autosave.shutdownNow();
        }
        Logger.info(Lang.current.get("server.saving_level", config.get("level-name", "Bedrock level")));
        savePlayers();
        world.close();
    }

    /** Saves every player still online, where they are and what they have. */
    private void savePlayers() {
        for (Player player : playerManager.getPlayers()) {
            world.savePlayer(player.getUuid(), PlayerData.save(player));
        }
    }

    /** Writes what's changed in the world every so often, so a crash loses little. */
    private void startAutosave() {
        autosave = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "World autosave");
            thread.setDaemon(true);
            return thread;
        });
        autosave.scheduleWithFixedDelay(() -> {
            try {
                savePlayers();
                int saved = world.save();
                if (saved > 0) {
                    Logger.debug("Autosave wrote " + saved + " chunks.");
                }
            } catch (Exception e) {
                Logger.error("Error during autosave: " + e);
            }
        }, AUTOSAVE_SECONDS, AUTOSAVE_SECONDS, TimeUnit.SECONDS);
    }

    /** The game mode players join in, and "default" stands for: "gamemode" in server.properties. */
    public GameType getDefaultGameMode() {
        String configured = config.get("gamemode", "survival");
        GameType gameMode = GameModes.parse(configured);
        if (gameMode == null) {
            Logger.warn("Unknown gamemode \"" + configured + "\" in server.properties, using survival.");
            return GameType.SURVIVAL;
        }
        return gameMode;
    }

    /** default-player-permission-level from server.properties: what players without a permissions.json entry get. */
    private Permission defaultPermission() {
        String configured = config.get("default-player-permission-level", "member");
        Permission permission = Permission.fromName(configured);
        if (permission == null) {
            Logger.warn("Unknown default-player-permission-level \"" + configured
                    + "\" in server.properties, using member. Expected visitor, member or operator.");
            return Permission.MEMBER;
        }
        return permission;
    }

    /**
     * Runs the world 20 times a second, as vanilla does: everything that changes on its
     * own over time, such as dropped items falling and being picked up, tripwire pressed by
     * the players walking through it, players getting the chunks around them as they move,
     * and the players and items in those chunks appearing to them. A tick that throws
     * is logged and the next one runs anyway - an uncaught exception would otherwise stop
     * the schedule for good.
     */
    private void startTicking() {
        ticker = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "Server tick"));
        ticker.scheduleAtFixedRate(() -> {
            try {
                // Chunks first, so what's shown below goes by the chunks clients have now.
                chunkViews.tick();
                scheduler.tick();
                tripwires.tick();
                itemEntities.tick();
                playerManager.updateVisibility();
                playerManager.updateEquipment();
            } catch (Exception e) {
                Logger.error("Error during the server tick: " + e);
            }
        }, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
